package com.wingsheep.network.tor

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import com.wingsheep.network.NetworkClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.torproject.jni.TorService
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Proxy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the lifecycle of the embedded Tor daemon (Guardian Project's
 * [TorService](https://github.com/guardianproject/tor-android)).
 *
 * Binding to [TorService] is what starts the daemon: it loads `libtor.so`,
 * configures a SOCKS port and publishes its status as a broadcast. Nothing is ever
 * started with `startService`, so unbinding is enough to shut Tor down.
 *
 * ## Contract
 *
 *  - [start] is idempotent and main-thread only; repeated calls while a start is in
 *    flight are no-ops.
 *  - [stop] releases *everything* — receiver, binding and the cached proxy client.
 *    It is idempotent and safe to call from any thread.
 *  - [state] is a [StateFlow] and can be read from any thread.
 *  - [httpClient] never throws: without a circuit it returns the shared direct
 *    client.
 *
 * *When* Tor should run is a policy decision made by the app layer, not here.
 */
@Singleton
class TorProxyManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<TorState>(TorState.Stopped)

    /** Current daemon state. */
    val state: StateFlow<TorState> = _state.asStateFlow()

    /** Main-thread confined: TorService callbacks and the broadcast arrive here. */
    private var service: TorService? = null
    private var serviceBound = false
    private var receiverRegistered = false

    private val clientLock = Any()
    private var proxyClient: OkHttpClient? = null
    private var proxyClientPort = -1

    /**
     * OkHttpClient for HTTP calls: routed through the Tor SOCKS proxy when a circuit
     * is up, otherwise the shared direct client.
     *
     * The decision is made per call, so HTTP keeps working while Tor bootstraps.
     */
    fun httpClient(): OkHttpClient {
        val ready = _state.value as? TorState.Ready ?: return NetworkClient.defaultClient
        return proxyClient(ready.socksPort)
    }

    /** Suspends until a circuit is up, or throws when Tor reports a failure. */
    suspend fun awaitReady(timeoutMillis: Long = READY_TIMEOUT_MILLIS): TorState.Ready {
        val resolved = withTimeout(timeoutMillis) {
            _state.first { it is TorState.Ready || it is TorState.Failed }
        }
        return when (resolved) {
            is TorState.Ready -> resolved
            is TorState.Failed -> throw IllegalStateException(resolved.message)
            else -> error("Unreachable Tor state: $resolved")
        }
    }

    /**
     * Binds to [TorService], which starts the daemon.
     *
     * Idempotent while a start is in flight or Tor is already up. Call from the main
     * thread — the binding, the receiver and every callback live there.
     */
    @MainThread
    fun start() {
        when (_state.value) {
            is TorState.Starting, is TorState.Ready -> return
            else -> Unit
        }
        _state.value = TorState.Starting
        try {
            registerStatusReceiver()
            bindServiceIfNeeded()
            Timber.i("Tor start requested")
        } catch (t: Throwable) {
            Timber.e(t, "Unable to start Tor")
            teardown()
            _state.value = TorState.Failed(t.message ?: "Unable to start Tor")
        }
    }

    /**
     * Unbinds from [TorService] (which destroys the service and shuts the daemon
     * down) and releases the cached proxy client.
     *
     * Idempotent and safe to call from any thread.
     */
    fun stop() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            stopOnMain()
        } else {
            mainHandler.post { stopOnMain() }
        }
    }

    private fun stopOnMain() {
        val wasActive = serviceBound || receiverRegistered || _state.value !is TorState.Stopped
        teardown()
        if (wasActive) {
            // Belt and braces: with no other client bound, unbinding already destroys
            // the service; stopService() also covers a started (not bound) service.
            runCatching { context.stopService(Intent(context, TorService::class.java)) }
                .onFailure { Timber.w(it, "stopService failed") }
            Timber.i("Tor stopped")
        }
    }

    private fun bindServiceIfNeeded() {
        if (serviceBound) return
        val bound = context.bindService(
            Intent(context, TorService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        check(bound) { "TorService refused binding" }
        serviceBound = true
    }

    /** Releases binding, receiver, cached client and state. Main thread only. */
    private fun teardown() {
        _state.value = TorState.Stopped
        closeProxyClient()

        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(statusReceiver) }
                .onFailure { Timber.w(it, "unregisterReceiver failed") }
            receiverRegistered = false
        }
        if (serviceBound) {
            runCatching { context.unbindService(connection) }
                .onFailure { Timber.w(it, "unbindService failed") }
            serviceBound = false
        }
        service = null
    }

    private fun registerStatusReceiver() {
        if (receiverRegistered) return
        // Registered before binding so a status broadcast emitted during start-up
        // cannot be missed.
        ContextCompat.registerReceiver(
            context,
            statusReceiver,
            IntentFilter(TorService.ACTION_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun proxyClient(socksPort: Int): OkHttpClient = synchronized(clientLock) {
        proxyClient?.takeIf { proxyClientPort == socksPort }?.let { return it }
        closeProxyClientLocked()
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(TorConfig.PROXY_HOST, socksPort))
        NetworkClient.newClient(proxy = proxy).also {
            proxyClient = it
            proxyClientPort = socksPort
        }
    }

    private fun closeProxyClient() = synchronized(clientLock) { closeProxyClientLocked() }

    /**
     * Every OkHttpClient owns a connection pool and a dispatcher thread. The app
     * stops Tor when it goes idle and starts it again on the next foreground, so a
     * client that is merely dropped leaks keep-alive sockets and threads on every
     * cycle.
     */
    private fun closeProxyClientLocked() {
        val client = proxyClient ?: return
        proxyClient = null
        proxyClientPort = -1
        runCatching { client.connectionPool.evictAll() }
            .onFailure { Timber.w(it, "evictAll failed") }
        runCatching { client.dispatcher.executorService.shutdown() }
            .onFailure { Timber.w(it, "dispatcher shutdown failed") }
    }

    private fun onTorReady() {
        val socksPort = service?.socksPort?.takeIf { it > 0 } ?: TorConfig.DEFAULT_SOCKS_PORT
        _state.value = TorState.Ready(socksPort)
        Timber.i("Tor is ready (SOCKS port %d)", socksPort)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val torService = (binder as? TorService.LocalBinder)?.service ?: return
            service = torService
            val socksPort = torService.socksPort.takeIf { it > 0 }
            Timber.i("Bound to TorService (SOCKS port %s)", socksPort ?: "unknown")
            // STATUS_ON can arrive before the bind completes, in which case the ready
            // state is still holding the default port.
            if (socksPort != null && _state.value is TorState.Ready) {
                _state.value = TorState.Ready(socksPort)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.w("TorService disconnected")
            teardown()
            _state.value = TorState.Failed("Tor service disconnected unexpectedly")
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(TorService.EXTRA_STATUS) ?: return
            Timber.d("Tor status: %s", status)
            when (status) {
                TorService.STATUS_ON -> onTorReady()
                TorService.STATUS_OFF -> {
                    Timber.i("Tor reported OFF")
                    teardown()
                }
            }
        }
    }

    private companion object {
        const val READY_TIMEOUT_MILLIS = 60_000L
    }
}
