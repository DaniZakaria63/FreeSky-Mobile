package com.wingsheep.freesky.tor

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.torproject.jni.TorService
import java.io.File
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
 */
@Singleton
class TorProxyManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    data class ProxyConfig(
        val host: String = "127.0.0.1",
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
        val socksPort: Int = 9050,
        val controlPort: Int? = null,
        val bridges: List<String> = emptyList(),
        val logLevel: String = "notice",
        val customOptions: Map<String, String> = emptyMap()
    )

    private var torService: TorService? = null
    private val ready = AtomicBoolean(false)
    private var _socksPort: Int = 9050

    /**
     * Completed when Tor reports [TorService.STATUS_ON] (circuit established)
     * or exceptionally if Tor stops unexpectedly.
     */
    @Volatile
    private var startDeferred: CompletableDeferred<ProxyConfig>? = null

    val isReady: Boolean get() = ready.get()
    val socksPort: Int get() = _socksPort

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
        _socksPort = config.socksPort
        Log.d("TorProxyManager", "torrc written to ${torrc.absolutePath}")
    }

    /**
     * Removes the custom `torrc` file so that Tor falls back to the
     * defaults provided by [TorService].
     */
    fun clearConfig() {
        val torrc: File = TorService.getTorrc(context)
        if (torrc.exists()) {
            torrc.delete()
            Log.d("TorProxyManager", "torrc removed")
        }
        _socksPort = 9050
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            torService = (service as TorService.LocalBinder).service

            // If Tor was already running (service was already bound), the
            // SOCKS port may already be available — complete early.
            val deferred = startDeferred
            if (deferred != null && deferred.isActive) {
                val port = torService?.socksPort ?: -1
                if (port > 0) {
                    _socksPort = port
                    ready.set(true)
                    deferred.complete(ProxyConfig(port = port))
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            torService = null
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(TorService.EXTRA_STATUS)
            Log.d("TorProxyManager", "Tor status: $status")
            when (status) {
                TorService.STATUS_ON -> {
                    val port = torService?.socksPort ?: 9050
                    _socksPort = port
                    ready.set(true)
                    Log.i("TorProxyManager", "Tor is ready, SOCKS port: $port")
                    val deferred = startDeferred
                    if (deferred != null && deferred.isActive) {
                        deferred.complete(ProxyConfig(port = port))
                    }
                }
                TorService.STATUS_OFF -> {
                    ready.set(false)
                    val deferred = startDeferred
                    if (deferred != null && deferred.isActive) {
                        deferred.completeExceptionally(
                            RuntimeException("Tor service stopped unexpectedly")
                        )
                    }
                }
            }
        }
    }

    /**
     * Binds to [TorService] (which starts the Tor daemon automatically) and
     * suspends until Tor reports that a circuit has been established.
     *
     * @return a [Deferred] that completes with the [ProxyConfig] once Tor is
     *         ready, or completes exceptionally if Tor fails to start.
     */
    fun start(scope: CoroutineScope): Deferred<ProxyConfig> = scope.async(Dispatchers.IO) {
        // If Tor is already ready, return immediately
        if (ready.get()) {
            return@async ProxyConfig(port = _socksPort)
        }

        startDeferred = CompletableDeferred()

        // Bind to TorService — onCreate() automatically starts the Tor daemon
        val intent = Intent(context, TorService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        // Register a status receiver on the main thread to track Tor lifecycle
        withContext(Dispatchers.Main) {
            ContextCompat.registerReceiver(
                context,
                statusReceiver,
                IntentFilter(TorService.ACTION_STATUS),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        // Suspend until Tor reports STATUS_ON (circuit established)
        startDeferred!!.await()
    }

    /**
     * Unregisters the status receiver, unbinds from [TorService], and stops
     * the service (which shuts down the Tor daemon).
     */
    fun stop() {
        ready.set(false)
        startDeferred?.cancel()
        startDeferred = null
        try {
            context.unregisterReceiver(statusReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered — safe to ignore
        }
        try {
            context.unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            // Not bound — safe to ignore
        }
        context.stopService(Intent(context, TorService::class.java))
    }
}
