package com.wingsheep.network.noise

import com.google.gson.Gson
import com.wingsheep.network.model.Notification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Callback interface for receiving unsolicited server notifications.
 *
 * The server pushes notifications (e.g. "new_post") to all connected clients
 * over the Noise transport. The client registers a listener via
 * [setNotificationListener] and receives callbacks on a background thread.
 */
fun interface NotificationListener {
    fun onNotification(notification: Notification)
}

/**
 * A single Noise NK session over one TCP connection.
 *
 * Sessions are single-use: after [close] the instance cannot be reused.
 * [close] is idempotent and safe to call from any thread; it unblocks the
 * reader thread and releases the socket.
 *
 * At most one request may be in flight. The protocol is a strictly ordered
 * request/response stream and response frames carry no request id, so
 * [sendRequest] holds [sendMutex] for the whole round trip — sending and
 * waiting — which is the only way to guarantee a caller receives the frame
 * that answers its own request.
 */
class NoiseManager(
    private val host: String,
    private val port: Int,
    private val devicePrivateKey: java.security.PrivateKey,
    private val devicePublicKeySec1: ByteArray,
    private val serverNoisePk: ByteArray,
    private val prologue: ByteArray
) {

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val RESPONSE_TIMEOUT_SECONDS = 30L

        /** Frame length prefix is 2 bytes, so a frame can never exceed this. */
        const val MAX_FRAME_BYTES = 65535
    }

    private val gson = Gson()
    @Volatile private var socket: Socket? = null
    @Volatile private var outputStream: OutputStream? = null
    @Volatile private var inputStream: InputStream? = null
    @Volatile private var sendCs: NoiseCipherState? = null
    @Volatile private var recvCs: NoiseCipherState? = null

    /**
     * Queue for responses to [sendRequest] calls.
     *
     * The notification listener is the sole reader of the InputStream.
     * It dispatches each decrypted message to either the notification
     * listener (if it has a "type" field) or this queue (for responses).
     */
    private val responseQueue = LinkedBlockingQueue<Inbound>()

    /** A decrypted response frame, or the signal that the session has closed. */
    private sealed class Inbound {
        class Payload(val json: String) : Inbound()
        object Closed : Inbound()
    }

    @Volatile
    private var notificationListener: NotificationListener? = null

    /**
     * Invoked on the reader thread when an established session dies
     * unexpectedly (connection lost, protocol error).  Not invoked when the
     * session is closed deliberately via [close].
     */
    @Volatile
    private var sessionLostListener: (() -> Unit)? = null

    private var listenerThread: Thread? = null
    private val listenerRunning = AtomicBoolean(false)

    /**
     * Lock to serialize [sendRequest] calls — ensures cipher nonces
     * and request/response ordering are correct when multiple coroutines
     * call sendRequest concurrently.
     */
    private val sendMutex = Any()

    /** Guards [close] against concurrent [establishSession]. */
    private val lifecycleLock = Any()

    @Volatile
    private var closed = false

    val isConnected: Boolean
        get() {
            val sock = socket ?: return false
            return !closed && sock.isConnected && !sock.isClosed
        }

    /**
     * Opens the TCP connection, performs the Noise NK handshake, and starts
     * the background reader thread.
     *
     * On failure the socket is closed before the exception is rethrown, so no
     * half-open session is left behind.
     */
    suspend fun establishSession() = withContext(Dispatchers.IO) {
        check(!closed) { "NoiseManager: session already closed" }

        Timber.i("NoiseManager: connecting to $host:$port")
        val sock = Socket()
        try {
            sock.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            sock.soTimeout = 0 // Reader thread blocks until data arrives or the socket is closed.
            val out = sock.getOutputStream()
            val input = sock.getInputStream()
            synchronized(lifecycleLock) {
                check(!closed) { "NoiseManager: closed while connecting" }
                socket = sock
                outputStream = out
                inputStream = input
            }
        } catch (t: Throwable) {
            runCatching { sock.close() }
            throw t
        }

        try {
            Timber.i("NoiseManager: TCP connected, starting NK handshake")
            doHandshake()
            Timber.i("NoiseManager: Noise NK handshake complete")
            startNotificationListener()
            // If close() raced the handshake, do not report a live session.
            check(!closed) { "NoiseManager: closed during connect" }
        } catch (t: Throwable) {
            Timber.w(t, "NoiseManager: session setup failed")
            closeInternal()
            throw t
        }
    }

    private fun doHandshake() {
        val protocolName = "Noise_NK_P256_ChaChaPoly_BLAKE2s"
        var sym = NoiseSymmetry.initialize(protocolName)
        Timber.d("NOISE_DEBUG h_0 (HASH(name)) = ${sym.h.hex()}")
        Timber.d("NOISE_DEBUG ck_0 = ${sym.ck.hex()}")

        sym = sym.mixHash(prologue)
        Timber.d("NOISE_DEBUG h_1 (prologue)  = ${sym.h.hex()}")

        val ephemeral = generateP256KeyPair()
        val epkBytes = p256PublicKeyBytes(ephemeral)

        // Pre-known responder static key (the server's Noise public key)
        sym = sym.mixHash(serverNoisePk)
        Timber.d("NOISE_DEBUG h_2 (+ rs)      = ${sym.h.hex()}")

        // msg1 = e, es
        // e: mix ephemeral key into hash
        sym = sym.mixHash(epkBytes)
        Timber.d("NOISE_DEBUG h_3 (+ re)      = ${sym.h.hex()}")
        Timber.d("NOISE_DEBUG re (eph pk)     = ${epkBytes.hex()}")

        // es: DH(ephemeral, responder_static), mix into key chain
        var temp = ecdh(ephemeral.private, serverNoisePk)
        Timber.d("NOISE_DEBUG shared (DH)     = ${temp.hex()}")
        sym = sym.mixKey(temp)
        Timber.d("NOISE_DEBUG new_ck          = ${sym.ck.hex()}")
        Timber.d("NOISE_DEBUG cipher_key      = ${sym.cs.key?.hex() ?: "null"}")

        // Every Noise message ends with EncryptAndHash(payload). Empty payload
        // with key set produces a 16-byte AEAD tag appended to msg1.
        val (symAfterMsg1, encryptedPayload) = sym.encryptAndHash(ByteArray(0))
        Timber.d("NOISE_DEBUG tag (from msg1) = ${encryptedPayload.hex()}")

        // Construct msg1: ephemeral key + encrypted empty payload tag
        val msg1 = epkBytes + encryptedPayload
        val out = outputStream ?: throw IllegalStateException("Noise: output stream unavailable")
        out.write(msg1)
        out.flush()
        Timber.d("NoiseManager: sent msg1 (${msg1.size} bytes)")

        // Read msg2 = e, ee + encrypted empty payload
        val input = inputStream ?: throw IllegalStateException("Noise: input stream unavailable")
        val respBuf = ByteArray(4096)
        val n = input.read(respBuf)
        if (n <= 0) throw IllegalStateException("Noise: no response")

        var data = respBuf.copyOf(n)
        if (data.size < 65) throw IllegalStateException("Noise: response too short")

        // e: responder's ephemeral key
        val reBytes = data.copyOfRange(0, 65)
        data = data.copyOfRange(65, data.size)

        // Mix responder's ephemeral into hash
        sym = symAfterMsg1.mixHash(reBytes)

        // ee: DH(ephemeral, responder_ephemeral), mix into key chain
        temp = ecdh(ephemeral.private, reBytes)
        sym = sym.mixKey(temp)

        // Decrypt remaining data (encrypted empty payload tag from msg2)
        val decryptResult = sym.decryptAndHash(data)
            ?: throw SecurityException("Noise msg2 decrypt failed")
        sym = decryptResult.first

        val (s1, s2) = sym.split()
        sendCs = s1
        recvCs = s2
    }

    /**
     * Register a listener for unsolicited server notifications.
     *
     * The notification listener thread (started in [establishSession])
     * continuously reads from the Noise transport. Messages with a "type"
     * field are dispatched to the listener; all others go to the response
     * queue for [sendRequest].
     *
     * Call this after [establishSession] to start receiving notifications.
     * Pass null to stop.
     */
    fun setNotificationListener(listener: NotificationListener?) {
        this.notificationListener = listener
    }

    /**
     * Register a listener invoked when the session terminates unexpectedly.
     * It fires on the reader thread, or on the caller's thread when a response
     * timeout makes the session unusable. Pass null to stop.
     */
    fun setSessionLostListener(listener: (() -> Unit)?) {
        this.sessionLostListener = listener
    }

    /**
     * Background reader thread — the sole consumer of the InputStream.
     *
     * Reads length-prefixed encrypted messages, decrypts them, and dispatches:
     * - Messages with a "type" field → [notificationListener]
     * - All other messages → [responseQueue] (for [sendRequest])
     *
     * Any read error, malformed frame, or AEAD failure terminates the session:
     * the cipher nonce sequence can no longer be trusted, and continuing
     * would spin on a dead socket.
     */
    private fun startNotificationListener() {
        val sock = socket ?: return
        listenerRunning.set(true)
        listenerThread = thread(name = "NoiseReader") {
            var sessionFailed = false
            try {
                val input = inputStream ?: throw IllegalStateException("NoiseReader: no input stream")
                val recv = recvCs ?: throw IllegalStateException("NoiseReader: no cipher state")

                while (listenerRunning.get() && !sock.isClosed) {
                    // Read 2-byte length prefix
                    val lenBuf = ByteArray(2)
                    readExact(input, lenBuf)
                    val msgLen = ((lenBuf[0].toInt() and 0xFF) shl 8) or (lenBuf[1].toInt() and 0xFF)
                    if (msgLen !in 1..MAX_FRAME_BYTES) {
                        throw IllegalStateException("NoiseReader: invalid frame length $msgLen")
                    }

                    // Read encrypted message
                    val encBuf = ByteArray(msgLen)
                    readExact(input, encBuf)

                    // Decrypt
                    val plaintext = recv.decrypt(encBuf, ByteArray(0))
                        ?: throw SecurityException("NoiseReader: frame decryption failed")

                    val json = plaintext.toString(Charsets.UTF_8)

                    // Dispatch: notification (has "type" field) or response
                    val notification = tryParseNotification(json)
                    if (notification != null) {
                        runCatching { notificationListener?.onNotification(notification) }
                            .onFailure { Timber.w(it, "NoiseReader: notification listener failed") }
                    } else {
                        responseQueue.put(Inbound.Payload(json))
                    }
                }
            } catch (t: Throwable) {
                if (listenerRunning.get()) {
                    sessionFailed = true
                    Timber.w(t, "NoiseReader: session terminated")
                }
            } finally {
                listenerRunning.set(false)
                closeInternal()
                if (sessionFailed) {
                    runCatching { sessionLostListener?.invoke() }
                        .onFailure { Timber.w(it, "NoiseReader: session-lost listener failed") }
                }
            }
        }
    }

    /**
     * Sends a request and waits for the response that answers it.
     *
     * The whole round trip — encrypt, write, wait — runs while holding
     * [sendMutex], so only one request is ever in flight. That is required for
     * correctness, not just for nonce ordering: the server answers in order and
     * [ApiResponse][com.wingsheep.network.model.ApiResponse] carries no request
     * id, so a second concurrent waiter could claim the wrong frame.
     *
     * If no response arrives within [RESPONSE_TIMEOUT_SECONDS] the session is
     * retired: after a timeout a late frame cannot be told apart from the answer
     * to the next request, so the connection is no longer safe to reuse. The
     * owner is notified through [setSessionLostListener] and must re-establish.
     */
    suspend fun sendRequest(method: String, body: Map<String, Any?>): String {
        return withContext(Dispatchers.IO) {
            if (closed || !listenerRunning.get()) {
                throw IllegalStateException("Noise session not established")
            }

            val fullBody = body.toMutableMap().apply { put("method", method) }
            val jsonBytes = gson.toJson(fullBody).toByteArray(Charsets.UTF_8)

            // Wait for the response from the background reader thread. Close posts
            // [Inbound.Closed], so this never waits out the timeout after the session
            // has gone away. Nothing but the frame is decided under the lock.
            val inbound: Inbound? = synchronized(sendMutex) {
                val send = sendCs ?: throw IllegalStateException("Noise session not established")
                val ciphertext = send.encrypt(jsonBytes, ByteArray(0))

                val out = outputStream ?: throw IllegalStateException("Noise session closed")
                writeLength(out, ciphertext.size)
                out.write(ciphertext)
                out.flush()

                responseQueue.poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }

            // Side effects stay outside the lock: closeInternal invokes the
            // session-lost listener, and a listener that called back into this
            // manager while sendMutex was held would deadlock.
            when (inbound) {
                null -> {
                    // A late frame can no longer be told apart from the next
                    // request's answer, so retire the session rather than hand a
                    // mismatched frame to the next caller.
                    closeInternal(notifySessionLost = true)
                    throw java.util.concurrent.TimeoutException(
                        "Noise response timed out after ${RESPONSE_TIMEOUT_SECONDS}s"
                    )
                }
                Inbound.Closed -> throw IllegalStateException("Noise session closed")
                is Inbound.Payload -> inbound.json
            }
        }
    }

    /**
     * Closes the session: stops the reader thread, closes the socket and
     * releases all buffers.  Idempotent and safe to call from any thread.
     */
    fun close() {
        closeInternal()
    }

    /**
     * @param notifySessionLost when true, [sessionLostListener] is invoked from the
     *   calling thread. Used when a local failure (a response timeout) has already
     *   made the session unusable, so the owner learns about it even though the
     *   reader thread was not the one that noticed.
     */
    private fun closeInternal(notifySessionLost: Boolean = false) {
        val threadToInterrupt: Thread?
        val sockToClose: Socket?
        val wasOpen: Boolean

        synchronized(lifecycleLock) {
            wasOpen = !closed
            closed = true
            listenerRunning.set(false)
            threadToInterrupt = listenerThread
            listenerThread = null
            sockToClose = socket
            socket = null
            outputStream = null
            inputStream = null
            sendCs = null
            recvCs = null
        }

        // Interrupting does not unblock a socket read; closing the socket does.
        if (threadToInterrupt != null && threadToInterrupt !== Thread.currentThread()) {
            threadToInterrupt.interrupt()
        }
        runCatching { sockToClose?.close() }

        // A caller blocked in sendRequest only wakes on an element: drop any
        // stale frame and post the closed sentinel so it fails immediately
        // instead of waiting out the full response timeout.
        responseQueue.clear()
        responseQueue.offer(Inbound.Closed)

        if (notifySessionLost && wasOpen) {
            runCatching { sessionLostListener?.invoke() }
                .onFailure { Timber.w(it, "NoiseManager: session-lost listener failed") }
        }
    }

    private fun tryParseNotification(json: String): Notification? {
        return try {
            val notification = gson.fromJson(json, Notification::class.java)
            if (notification.type.isNotEmpty()) notification else null
        } catch (e: Exception) {
            null
        }
    }

    private fun writeLength(out: OutputStream, len: Int) {
        out.write((len shr 8) and 0xFF)
        out.write(len and 0xFF)
    }

    private fun readExact(input: InputStream, buf: ByteArray) {
        var offset = 0
        while (offset < buf.size) {
            val n = input.read(buf, offset, buf.size - offset)
            if (n == -1) throw IllegalStateException("TCP read failed: stream closed")
            offset += n
        }
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}
