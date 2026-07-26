package com.wingsheep.network.noise

import com.google.gson.Gson
import com.wingsheep.network.model.Notification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
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

class NoiseManager(
    private val host: String,
    private val port: Int,
    private val devicePrivateKey: java.security.PrivateKey,
    private val devicePublicKeySec1: ByteArray,
    private val serverNoisePk: ByteArray,
    private val prologue: ByteArray
) {

    private val gson = Gson()
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var sendCs: NoiseCipherState? = null
    private var recvCs: NoiseCipherState? = null

    /**
     * Queue for responses to [sendRequest] calls.
     *
     * The notification listener is the sole reader of the InputStream.
     * It dispatches each decrypted message to either the notification
     * listener (if it has a "type" field) or this queue (for responses).
     */
    private val responseQueue = LinkedBlockingQueue<String>()

    private var notificationListener: NotificationListener? = null
    private var listenerThread: Thread? = null
    private val listenerRunning = AtomicBoolean(false)

    /**
     * Lock to serialize [sendRequest] calls — ensures cipher nonces
     * and request/response ordering are correct when multiple coroutines
     * call sendRequest concurrently.
     */
    private val sendMutex = Any()

    val isConnected: Boolean get() = socket?.isConnected == true && !socket!!.isClosed

    suspend fun establishSession() = withContext(Dispatchers.IO) {
        Timber.i("NoiseManager: connecting to $host:$port")
        val sock = Socket(host, port)
        sock.soTimeout = 0 // No timeout — notification listener blocks indefinitely
        socket = sock
        outputStream = sock.getOutputStream()
        inputStream = sock.getInputStream()
        Timber.i("NoiseManager: TCP connected, starting NK handshake")
        doHandshake()
        Timber.i("NoiseManager: Noise NK handshake complete")
        startNotificationListener()
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
        val out = outputStream!!
        out.write(msg1)
        out.flush()
        Timber.d("NoiseManager: sent msg1 (${msg1.size} bytes)")

        // Read msg2 = e, ee + encrypted empty payload
        val input = inputStream!!
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
     * Background reader thread — the sole consumer of the InputStream.
     *
     * Reads length-prefixed encrypted messages, decrypts them, and dispatches:
     * - Messages with a "type" field → [notificationListener]
     * - All other messages → [responseQueue] (for [sendRequest])
     */
    private fun startNotificationListener() {
        listenerRunning.set(true)
        listenerThread = thread(name = "NoiseReader") {
            val input = inputStream ?: return@thread
            val recv = recvCs ?: return@thread

            while (listenerRunning.get() && !socket!!.isClosed) {
                try {
                    // Read 2-byte length prefix
                    val lenBuf = ByteArray(2)
                    readExact(input, lenBuf)
                    val msgLen = ((lenBuf[0].toInt() and 0xFF) shl 8) or (lenBuf[1].toInt() and 0xFF)
                    if (msgLen > 65536) {
                        Timber.w("NoiseReader: message too large ($msgLen bytes), skipping")
                        continue
                    }

                    // Read encrypted message
                    val encBuf = ByteArray(msgLen)
                    readExact(input, encBuf)

                    // Decrypt
                    val plaintext = recv.decrypt(encBuf, ByteArray(0))
                        ?: run {
                            Timber.w("NoiseReader: decrypt failed, skipping")
                            continue
                        }

                    val json = plaintext.toString(Charsets.UTF_8)

                    // Dispatch: notification (has "type" field) or response
                    val notification = tryParseNotification(json)
                    if (notification != null) {
                        notificationListener?.onNotification(notification)
                    } else {
                        responseQueue.put(json)
                    }
                } catch (e: Exception) {
                    if (listenerRunning.get()) {
                        Timber.w(e, "NoiseReader error")
                    }
                }
            }
        }
    }

    private fun stopNotificationListener() {
        listenerRunning.set(false)
        listenerThread?.interrupt()
        listenerThread = null
        responseQueue.clear()
    }

    /**
     * Send a request and wait for the response.
     *
     * The request is encrypted and sent to the server. The response is
     * received by the background reader thread and placed in [responseQueue].
     * This method blocks until the response arrives (or times out).
     *
     * Concurrent calls are serialized via [sendMutex] to ensure correct
     * cipher nonce ordering and request/response matching.
     */
    suspend fun sendRequest(method: String, body: Map<String, Any?>): String {
        return withContext(Dispatchers.IO) {
            if (!listenerRunning.get()) {
                throw IllegalStateException("Noise session not established")
            }

            val fullBody = body.toMutableMap().apply { put("method", method) }
            val jsonBytes = gson.toJson(fullBody).toByteArray(Charsets.UTF_8)

            synchronized(sendMutex) {
                val send = checkNotNull(sendCs) { "Noise session not established" }
                val ciphertext = send.encrypt(jsonBytes, ByteArray(0))

                val out = outputStream!!
                writeLength(out, ciphertext.size)
                out.write(ciphertext)
                out.flush()
            }

            // Wait for the response from the background reader thread.
            // 30-second timeout to avoid blocking forever if the connection dies.
            responseQueue.poll(30, java.util.concurrent.TimeUnit.SECONDS)
                ?: throw java.util.concurrent.TimeoutException("Noise response timed out after 30s")
        }
    }

    fun close() {
        stopNotificationListener()
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        outputStream = null
        inputStream = null
        sendCs = null
        recvCs = null
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
