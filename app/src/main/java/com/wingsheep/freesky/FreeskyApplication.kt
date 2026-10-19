package com.wingsheep.freesky

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.wingsheep.freesky.ui.updatePrivacyPolicyUrl
import com.wingsheep.network.config.RemoteConfigManager
import com.wingsheep.network.tor.TorProxyManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class FreeskyApplication : Application() {

    @Inject lateinit var torProxyManager: TorProxyManager
    @Inject lateinit var remoteConfigManager: RemoteConfigManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Number of activities currently in the started state (main thread only). */
    private var startedActivities = 0

    /** Pending idle shutdown of the Tor daemon (main thread only). */
    private var pendingTorShutdown: Job? = null

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())

        remoteConfigManager.init(scope)
        // init() only *starts* an asynchronous fetch, so the URL must be observed
        // instead of read once here: a one-shot read always captured the built-in
        // default and a remote override could never take effect.
        scope.launch {
            remoteConfigManager.privacyUrlFlow.collect { updatePrivacyPolicyUrl(it) }
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                pendingTorShutdown?.cancel()
                pendingTorShutdown = null
                // Idempotent: binds TorService once and keeps Tor warm while
                // any app UI is visible.
                torProxyManager.start()
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) {
                    scheduleTorShutdown()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /**
     * Stops the Tor daemon once the app has been in the background for
     * [TOR_IDLE_SHUTDOWN_DELAY_MS].  The grace period keeps Tor warm across
     * quick app switches; after it passes, no app component keeps running in
     * the background.
     */
    private fun scheduleTorShutdown() {
        pendingTorShutdown?.cancel()
        pendingTorShutdown = scope.launch {
            delay(TOR_IDLE_SHUTDOWN_DELAY_MS)
            withContext(Dispatchers.Main) {
                // Re-check on the main thread: if a new activity started in
                // the meantime, its onActivityStarted already cancelled this
                // job — but stopping is harmless only when truly idle.
                if (startedActivities == 0) {
                    torProxyManager.stop()
                }
            }
        }
    }

    override fun onTerminate() {
        pendingTorShutdown?.cancel()
        pendingTorShutdown = null
        // Only reached on emulators; on devices the activity lifecycle above
        // is what tears Tor down.
        torProxyManager.stop()
        super.onTerminate()
    }

    private companion object {
        /** How long Tor is kept alive after the last activity stops. */
        const val TOR_IDLE_SHUTDOWN_DELAY_MS = 20_000L
    }
}
