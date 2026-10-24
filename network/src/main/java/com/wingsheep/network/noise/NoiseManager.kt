package com.wingsheep.network.noise

import com.google.gson.Gson
import com.wingsheep.network.model.Notification
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.PrivateKey
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Receives unsolicited server notifications (e.g. `new_post`) on a background thread. */
fun interface NotificationListener {
    fun onNotification(notification: Notification)
}

/**
 * A single Noise NK session over one TCP connection.
 *
 * Sessions are single-use: after [close] the instance cannot be reused. [close] is
 * idempotent and safe to call from any thread; it unblocks the reader and releases
 * the socket.
 *
 * Exactly one request may be in flight. The protocol is a strictly ordered
 * request/response stream and responses carry no request id, so [sendRequest] holds
 * [sendMutex] for the whole round trip — sending *and* waiting — which is the only
 * way to guarantee a caller receives the frame that answers its own request.
 *
 * One reader thread is the sole consumer of the input stream. It dispatches each
 * decrypted message either to the notification listener or to the waiting request.
 */
class NoiseManager(
    private val host: String,
    private val port: Int,
    private val devicePrivateKey: PrivateKey,
    private val serverStaticKey: ByteArray,
    private val prologue: ByteArray
) {

    private val gson = Gson()

    @Volatile private var socket: Socket? = null
    @Volatile private var outputStream: OutputStream? = null
    @Volatile private var sendCipher: NoiseCipherState? = null
    @Volatile private var receiveCipher: NoiseCipherState? = null
    @Volatile private var notificationListener: NotificationListener? = null
    @Volatile private var sessionLostListener: (() -> Unit)? = null

    /** The request awaiting its response; set and cleared under [sendMutex]. */
    @Volatile private var pendingResponse: CompletableDeferred<String>? = null

    private var readerThread: Thread? = null
    private val readerRunning = AtomicBoolean(false)

    private val sendMutex = Mutex()

    /** Guards [close] against a concurrent [establishSession]. */
    private val lifecycleLock = Any()

    @Volatile private var closed = false

    val isConnected: Boolean
        get() {
            val current = socket ?: return false
            return !closed && current.isConnected && !current.isClosed
        }

    /**
     * Opens the TCP connection, performs the NK handshake and starts the reader.
     *
     * On failure the socket is closed before the exception is rethrown, so no
     * half-open session is left behind.
     */
    suspend fun establishSession() = withContext(Dispatchers.IO) {
        check(!closed) { "Noise session already closed" }

        Timber.i("Noise: connecting to %s:%d", host, port)
        val tcpSocket = Socket()
        try {
            tcpSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS)
            // The reader blocks on read(); closing the socket is what unblocks it.
            tcpSocket.soTimeout = 0
            val input = tcpSocket.getInputStream()
            val output = tcpSocket.getOutputStream()
            synchronized(lifecycleLock) {
                check(!closed) { "Noise session closed while connecting" }
                socket = tcpSocket
                outputStream = output
            }

            Timber.i("Noise: TCP connected, starting NK handshake")
            val keys = NoiseHandshake.perform(
                input = input,
                output = output,
                devicePrivateKey = devicePrivateKey,
                serverStaticKey = serverStaticKey,
                prologue = prologue
            )
            sendCipher = keys.send
            receiveCipher = keys.receive
            startReader(input, keys.receive, tcpSocket)

            // close() may have raced the handshake; do not report a live session.
            check(!closed) { "Noise session closed during connect" }
        } catch (t: Throwable) {
            Timber.w(t, "Noise: session setup failed")
            closeInternal()
            throw t
        }
    }

    /** Registers the notification listener. Pass null to stop receiving. */
    fun setNotificationListener(listener: NotificationListener?) {
        notificationListener = listener
    }

    /**
     * Registers a listener invoked when the session terminates *unexpectedly* — the
     * connection dropped, a frame failed authentication, or a response timed out.
     *
     * Not invoked when [close] is called deliberately. Pass null to stop.
     */
    fun setSessionLostListener(listener: (() -> Unit)?) {
        sessionLostListener = listener
    }

    /**
     * Sends a request and waits for the response that answers it.
     *
     * The whole round trip runs under [sendMutex]: the server answers in order and
     * responses carry no request id, so a second concurrent waiter could claim the
     * wrong frame.
     *
     * A timeout retires the session — a late frame can no longer be told apart from
     * the answer to the next request — and the owner is notified through
     * [setSessionLostListener].
     */
    suspend fun sendRequest(method: String, body: Map<String, Any?>): String =
        withContext(Dispatchers.IO) {
            if (closed) throw IOException("Noise session is closed")
            try {
                sendMutex.withLock { roundTrip(method, body) }
            } catch (timeout: TimeoutCancellationException) {
                // Side effects stay outside the lock; the listener may call back in.
                closeInternal(notifySessionLost = true)
                throw SocketTimeoutException(
                    "Noise response timed out after ${RESPONSE_TIMEOUT_MILLIS} ms"
                )
            }
        }

    private suspend fun roundTrip(method: String, body: Map<String, Any?>): String {
        val output = outputStream ?: throw IOException("Noise session is closed")
        val send = sendCipher ?: throw IOException("Noise session is not established")

        val plaintext = gson.toJson(body + mapOf(METHOD_FIELD to method))
            .toByteArray(Charsets.UTF_8)
        val response = CompletableDeferred<String>()
        pendingResponse = response
        try {
            NoiseFraming.writeFrame(output, send.encrypt(plaintext, EMPTY))
            return withTimeout(RESPONSE_TIMEOUT_MILLIS) { response.await() }
        } finally {
            pendingResponse = null
        }
    }

    /** Closes the session. Idempotent and safe to call from any thread. */
    fun close() = closeInternal()

    /**
     * @param notifySessionLost when true, [sessionLostListener] is invoked from the
     *   calling thread. Used when a local failure (a response timeout) has already
     *   made the session unusable, so the owner learns about it even though the
     *   reader was not the one that noticed.
     */
    private fun closeInternal(notifySessionLost: Boolean = false) {
        val wasOpen: Boolean
        val socketToClose: Socket?
        val threadToInterrupt: Thread?

        synchronized(lifecycleLock) {
            wasOpen = !closed
            closed = true
            readerRunning.set(false)
            threadToInterrupt = readerThread
            readerThread = null
            socketToClose = socket
            socket = null
            outputStream = null
            sendCipher = null
            receiveCipher = null
        }

        // Unblock a caller waiting on a response: it should fail now, not after the
        // full timeout.
        pendingResponse?.completeExceptionally(IOException("Noise session closed"))
        pendingResponse = null

        // Interrupting does not unblock a socket read; closing the socket does.
        if (threadToInterrupt != null && threadToInterrupt !== Thread.currentThread()) {
            threadToInterrupt.interrupt()
        }
        runCatching { socketToClose?.close() }

        if (notifySessionLost && wasOpen) {
            runCatching { sessionLostListener?.invoke() }
                .onFailure { Timber.w(it, "Noise: session-lost listener failed") }
        }
    }

    /**
     * Starts the sole reader of [input].
     *
     * Any read error, malformed frame or failed authentication terminates the
     * session: the nonce sequence can no longer be trusted, and continuing would
     * spin on a dead socket.
     */
    private fun startReader(input: InputStream, receive: NoiseCipherState, tcpSocket: Socket) {
        readerRunning.set(true)
        readerThread = thread(name = "NoiseReader", isDaemon = true) {
            var sessionFailed = false
            try {
                while (readerRunning.get() && !tcpSocket.isClosed) {
                    val frame = NoiseFraming.readFrame(input)
                    val plaintext = receive.decrypt(frame, EMPTY)
                        ?: throw SecurityException("Noise frame failed authentication")
                    dispatch(plaintext.toString(Charsets.UTF_8))
                }
            } catch (t: Throwable) {
                // Closing the socket on purpose makes the blocking read throw; that
                // is not a session failure.
                if (readerRunning.get()) {
                    sessionFailed = true
                    Timber.w(t, "Noise: session terminated")
                }
            } finally {
                readerRunning.set(false)
                closeInternal(notifySessionLost = sessionFailed)
            }
        }
    }

    private fun dispatch(json: String) {
        val notification = parseNotification(json)
        if (notification != null) {
            val listener = notificationListener
            if (listener == null) {
                Timber.d("Noise: dropping %s notification (no listener)", notification.type)
            } else {
                runCatching { listener.onNotification(notification) }
                    .onFailure { Timber.w(it, "Noise: notification listener failed") }
            }
            return
        }

        val waiting = pendingResponse
        if (waiting == null) {
            Timber.w("Noise: dropping frame with no request in flight")
        } else {
            waiting.complete(json)
        }
    }

    /**
     * Responses carry `message`/`data`, notifications carry `type`, so the JSON is
     * only parsed as a notification when the field is present.
     */
    private fun parseNotification(json: String): Notification? {
        if (!json.contains(TYPE_FIELD)) return null
        return runCatching {
            gson.fromJson(json, Notification::class.java)?.takeIf { it.type.isNotEmpty() }
        }.getOrNull()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val RESPONSE_TIMEOUT_MILLIS = 30_000L
        const val METHOD_FIELD = "method"
        const val TYPE_FIELD = "\"type\""
        val EMPTY = ByteArray(0)
    }
}
