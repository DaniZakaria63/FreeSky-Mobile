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
import android.util.Log
import androidx.core.content.ContextCompat
import com.wingsheep.network.NetworkClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import okhttp3.OkHttpClient
import org.torproject.jni.TorService
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the lifecycle of a Tor daemon via the Guardian Project's
 * [TorService](https://github.com/guardianproject/tor-android).
 *
 * The Tor daemon is started by binding to [TorService], which automatically
 * loads `libtor.so`, configures a SOCKS port (default 9050), and exposes a
 * control connection.  This manager tracks Tor's status via broadcast
 * intents and exposes the SOCKS port so that the OkHttpClient can route
 * traffic through Tor.
 *
 * ## Lifecycle contract
 *
 *  - [start] is idempotent: repeated calls reuse the in-flight binding and
 *    return the same [Deferred].  Call it from the main thread.
 *  - [stop] releases *everything* — status receiver, service binding, cached
 *    proxy client and readiness state.  It is idempotent and safe to call
 *    from any thread (it hops to the main thread when needed).
 *  - Nothing keeps running after [stop]: [TorService] is bound (never
 *    started with `startService`), so unbinding destroys the service, and its
 *    `onDestroy` shuts the Tor daemon down.
 *
 * The application layer decides *when* Tor should run (see
 * `FreeskyApplication`: started while any UI is visible, stopped shortly
 * after the last activity goes to the background).
 */
@Singleton
class TorProxyManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    data class ProxyConfig(
        val host: String = PROXY_HOST,
        val port: Int
    )

    /**
     * Tor daemon configuration options.  These are written to the `torrc`
     * file that [TorService] reads when starting the Tor daemon.
     *
     * Call [configure] before [start] to apply these settings.
     *
     * @param socksPort SOCKS proxy port (default 9050)
     * @param controlPort Optional control port for jtorctl commands
     * @param bridges List of bridge lines for censorship circumvention
     * @param logLevel Tor log level (e.g. "notice", "info", "debug")
     * @param customOptions Additional torrc key-value pairs
     */
    data class TorConfig(
        val socksPort: Int = DEFAULT_SOCKS_PORT,
        val controlPort: Int? = null,
        val bridges: List<String> = emptyList(),
        val logLevel: String = "notice",
        val customOptions: Map<String, String> = emptyMap()
    )

    private companion object {
        const val TAG = "TorProxyManager"
        const val PROXY_HOST = "127.0.0.1"
        const val DEFAULT_SOCKS_PORT = 9050
    }

    /** Guards all mutable lifecycle state below. */
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var torService: TorService? = null

    private val ready = AtomicBoolean(false)

    @Volatile
    private var socksPortValue: Int = DEFAULT_SOCKS_PORT

    /**
     * Completed when Tor reports [TorService.STATUS_ON] (circuit established)
     * or exceptionally if Tor stops unexpectedly.  Doubles as the "start in
     * flight" marker.
     */
    @Volatile
    private var startDeferred: CompletableDeferred<ProxyConfig>? = null

    /** Cached OkHttp client routed through the current SOCKS port. */
    @Volatile
    private var cachedProxyClient: OkHttpClient? = null

    /** SOCKS port [cachedProxyClient] was built for; -1 when there is no cached client. */
    @Volatile
    private var cachedProxyPort: Int = -1

    private var serviceBound = false
    private var receiverRegistered = false

    val isReady: Boolean get() = ready.get()
    val socksPort: Int get() = socksPortValue

    /**
     * OkHttpClient for HTTP calls: routed through the Tor SOCKS proxy when a
     * circuit is up, otherwise the shared direct client.
     *
     * The decision is made per call, so HTTP works whether or not Tor has
     * finished bootstrapping.
     */
    fun httpClient(): OkHttpClient {
        if (!ready.get()) return NetworkClient.defaultClient
        val port = socksPortValue
        cachedProxyClient?.let { if (cachedProxyPort == port) return it }
        synchronized(lock) {
            cachedProxyClient?.let { if (cachedProxyPort == port) return it }
            // Rebuild only when the SOCKS port actually changed, and release the
            // previous client first. Every OkHttpClient owns a connection pool and a
            // dispatcher; because the app stops Tor when it goes idle and starts it
            // again on the next foreground, dropping them without closing leaked
            // keep-alive sockets and threads on every cycle.
            releaseProxyClient()
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(PROXY_HOST, port))
            return NetworkClient.newClient(proxy = proxy).also {
                cachedProxyClient = it
                cachedProxyPort = port
            }
        }
    }

    /**
     * Releases the cached proxy client and closes its sockets. Callers must hold [lock].
     *
     * The dispatcher is shut down gracefully — in-flight calls finish, later
     * submissions are rejected — and the pool is emptied so keep-alive connections
     * to the SOCKS port close immediately instead of lingering until they idle out.
     */
    private fun releaseProxyClient() {
        val client = cachedProxyClient ?: return
        cachedProxyClient = null
        cachedProxyPort = -1
        runCatching { client.connectionPool.evictAll() }
            .onFailure { Log.w(TAG, "evictAll failed: ${it.message}") }
        runCatching { client.dispatcher.executorService.shutdown() }
            .onFailure { Log.w(TAG, "dispatcher shutdown failed: ${it.message}") }
    }

    /**
     * Writes a `torrc` configuration file to the location expected by
     * [TorService].  Must be called **before** [start] for the settings
     * to take effect.
     *
     * If this method is never called, Tor uses the defaults written by
     * [TorService] (SOCKS port 9050, no bridges, no control port).
     */
    fun configure(config: TorConfig = TorConfig()) {
        val torrc: File = TorService.getTorrc(context)
        torrc.parentFile?.mkdirs()
        torrc.writeText(buildString {
            append("SOCKSPort ${config.socksPort}\n")
            config.controlPort?.let { append("ControlPort $it\n") }
            if (config.bridges.isNotEmpty()) {
                append("UseBridges 1\n")
                config.bridges.forEach { bridge ->
                    append("Bridge $bridge\n")
                }
            }
            append("Log ${config.logLevel} stdout\n")
            config.customOptions.forEach { (key, value) ->
                append("$key $value\n")
            }
        })
        socksPortValue = config.socksPort
        Log.d(TAG, "torrc written to ${torrc.absolutePath}")
    }

    /**
     * Removes the custom `torrc` file so that Tor falls back to the
     * defaults provided by [TorService].
     */
    fun clearConfig() {
        val torrc: File = TorService.getTorrc(context)
        if (torrc.exists()) {
            torrc.delete()
            Log.d(TAG, "torrc removed")
        }
        socksPortValue = DEFAULT_SOCKS_PORT
    }

    /**
     * Binds to [TorService] (which starts the Tor daemon automatically) and
     * returns a [Deferred] that completes with the [ProxyConfig] once Tor
     * reports that a circuit has been established.
     *
     * Idempotent: while a start is in flight the same [Deferred] is returned;
     * when Tor is already ready a completed [Deferred] is returned.
     *
     * Call from the main thread.
     */
    fun start(): Deferred<ProxyConfig> {
        synchronized(lock) {
            if (ready.get()) {
                return CompletableDeferred(ProxyConfig(port = socksPortValue))
            }
            val inFlight = startDeferred
            if (inFlight != null && inFlight.isActive) {
                return inFlight
            }

            val deferred = CompletableDeferred<ProxyConfig>()
            startDeferred = deferred

            try {
                // Register before binding so we cannot miss a status intent
                // emitted while the service is starting up.
                registerStatusReceiver()
                if (!serviceBound) {
                    val bound = context.bindService(
                        Intent(context, TorService::class.java),
                        serviceConnection,
                        Context.BIND_AUTO_CREATE
                    )
                    if (!bound) {
                        throw IllegalStateException("TorService refused binding")
                    }
                    serviceBound = true
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to start Tor", t)
                clearState()
                deferred.completeExceptionally(t)
                return deferred
            }

            Log.i(TAG, "Tor start requested")
            return deferred
        }
    }

    /**
     * Unregisters the status receiver, unbinds from [TorService] (which shuts
     * the Tor daemon down since nothing else binds it), stops the service and
     * clears all cached state.
     *
     * Idempotent and safe to call from any thread.
     */
    fun stop() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            stopNow()
        } else {
            mainHandler.post { stopNow() }
        }
    }

    private fun stopNow() {
        synchronized(lock) {
            val hadBinding = serviceBound || receiverRegistered || startDeferred != null
            clearState()
            if (hadBinding) {
                // Belt and braces: with no other clients bound, unbinding
                // already destroys the service; stopService() also covers the
                // (currently unused) started-service case.
                runCatching { context.stopService(Intent(context, TorService::class.java)) }
                    .onFailure { Log.w(TAG, "stopService failed: ${it.message}") }
                Log.i(TAG, "Tor stopped")
            }
        }
    }

    /**
     * Releases binding, receiver and cached state.  Callers must hold [lock].
     */
    private fun clearState() {
        ready.set(false)
        socksPortValue = DEFAULT_SOCKS_PORT
        releaseProxyClient()

        val deferred = startDeferred
        startDeferred = null
        deferred?.cancel()

        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(statusReceiver) }
                .onFailure { Log.w(TAG, "unregisterReceiver failed: ${it.message}") }
            receiverRegistered = false
        }
        if (serviceBound) {
            runCatching { context.unbindService(serviceConnection) }
                .onFailure { Log.w(TAG, "unbindService failed: ${it.message}") }
            serviceBound = false
        }
        torService = null
    }

    /** Callers must hold [lock]. */
    private fun registerStatusReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            context,
            statusReceiver,
            IntentFilter(TorService.ACTION_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? TorService.LocalBinder ?: return
            synchronized(lock) {
                torService = binder.service
                val port = torService?.socksPort ?: -1
                if (port > 0) {
                    socksPortValue = port
                }
                Log.i(TAG, "Bound to TorService (socks port: $port)")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(lock) {
                Log.w(TAG, "TorService disconnected")
                torService = null
                val deferred = startDeferred
                startDeferred = null
                if (deferred != null && deferred.isActive) {
                    deferred.completeExceptionally(
                        IllegalStateException("Tor service disconnected unexpectedly")
                    )
                }
                clearState()
            }
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(TorService.EXTRA_STATUS) ?: return
            Log.d(TAG, "Tor status: $status")
            synchronized(lock) {
                when (status) {
                    TorService.STATUS_ON -> {
                        val port = torService?.socksPort?.takeIf { it > 0 }
                            ?: DEFAULT_SOCKS_PORT
                        socksPortValue = port
                        // No invalidation here: httpClient() compares the port it
                        // built for, and clearing the cache without releasing the
                        // client was the leak.
                        ready.set(true)
                        Log.i(TAG, "Tor is ready, SOCKS port: $port")
                        val deferred = startDeferred
                        startDeferred = null
                        if (deferred != null && deferred.isActive) {
                            deferred.complete(ProxyConfig(port = port))
                        }
                    }

                    TorService.STATUS_OFF -> {
                        Log.i(TAG, "Tor reported OFF")
                        val deferred = startDeferred
                        startDeferred = null
                        if (deferred != null && deferred.isActive) {
                            deferred.completeExceptionally(
                                IllegalStateException("Tor service stopped unexpectedly")
                            )
                        }
                        clearState()
                    }
                }
            }
        }
    }
}
