package com.wingsheep.freesky.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.wingsheep.network.tor.TorProxyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs Tor while the app has visible UI and stops it once the app has been in the
 * background for [IDLE_SHUTDOWN_DELAY_MILLIS].
 *
 * [ProcessLifecycleOwner] collapses the per-activity bookkeeping into a single
 * process lifecycle: ON_START fires when the first activity becomes visible, ON_STOP
 * shortly after the last one is hidden. The extra grace period keeps Tor warm across
 * quick app switches — opening a password manager and coming back must not tear the
 * daemon down and rebuild circuits.
 *
 * Tor is bound (never started as a service), so stopping is simply unbinding, and no
 * app component keeps running in the background.
 */
@Singleton
class TorLifecycleObserver @Inject constructor(
    private val tor: TorProxyManager
) : DefaultLifecycleObserver {

    /**
     * Process-lived and main-confined: the timer must run on the main thread, and
     * this singleton lives as long as the application.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var idleShutdown: Job? = null
    private var attached = false

    /** Registers with the process lifecycle. Idempotent; call from the main thread. */
    fun attach() {
        if (attached) return
        attached = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        val cancelledPending = idleShutdown?.isActive == true
        idleShutdown?.cancel()
        idleShutdown = null
        if (cancelledPending) {
            Timber.i("lifecycle: UI is visible again, cancelled the pending Tor shutdown")
        }
        tor.start()
    }

    override fun onStop(owner: LifecycleOwner) {
        idleShutdown?.cancel()
        idleShutdown = scope.launch {
            Timber.i("lifecycle: app hidden, stopping Tor in %d ms", IDLE_SHUTDOWN_DELAY_MILLIS)
            delay(IDLE_SHUTDOWN_DELAY_MILLIS)
            tor.stop()
        }
    }

    private companion object {
        const val IDLE_SHUTDOWN_DELAY_MILLIS = 20_000L
    }
}
