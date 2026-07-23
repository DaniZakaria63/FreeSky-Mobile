package com.wingsheep.freesky

import android.app.Application
import com.wingsheep.freesky.tor.TorProxyManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class FreeskyApplication : Application() {

    @Inject lateinit var torProxyManager: TorProxyManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        torProxyManager.start(scope)
    }

    override fun onTerminate() {
        torProxyManager.stop()
        super.onTerminate()
    }
}