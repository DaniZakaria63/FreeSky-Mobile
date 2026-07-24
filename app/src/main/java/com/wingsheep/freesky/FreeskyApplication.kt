package com.wingsheep.freesky

import android.app.Application
import com.wingsheep.network.tor.TorProxyManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class FreeskyApplication : Application() {

    @Inject lateinit var torProxyManager: TorProxyManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        torProxyManager.start(scope)
    }

    override fun onTerminate() {
        torProxyManager.stop()
        super.onTerminate()
    }
}