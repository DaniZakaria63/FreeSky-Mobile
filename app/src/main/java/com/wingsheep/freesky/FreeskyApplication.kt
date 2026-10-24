package com.wingsheep.freesky

import android.app.Application
import com.wingsheep.freesky.lifecycle.TorLifecycleObserver
import com.wingsheep.network.config.RemoteConfigManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class FreeskyApplication : Application() {

    @Inject lateinit var remoteConfig: RemoteConfigManager
    @Inject lateinit var torLifecycleObserver: TorLifecycleObserver

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        remoteConfig.start()
        torLifecycleObserver.attach()
    }
}
