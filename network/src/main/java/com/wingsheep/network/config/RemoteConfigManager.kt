package com.wingsheep.network.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class RemoteConfigManager @Inject constructor() {

    companion object {
        const val KEY_SERVER_IP = "server_ip"
        const val KEY_SERVER_PORT = "server_port"
        const val KEY_NOISE_PORT = "noise_port"
        const val KEY_PRIVACY_URL = "privacy_policy_url"

        const val DEFAULT_SERVER_IP = "208.76.40.197"
        const val DEFAULT_SERVER_PORT = 3000
        const val DEFAULT_NOISE_PORT = 9443
        const val DEFAULT_PRIVACY_URL = "https://antinormies.github.io/tech-nerd/freesky-privacy-policy/"
    }

    var serverIp: String = DEFAULT_SERVER_IP
        private set

    var serverPort: Int = DEFAULT_SERVER_PORT
        private set

    var noisePort: Int = DEFAULT_NOISE_PORT
        private set

    var privacyUrl: String = DEFAULT_PRIVACY_URL
        private set

    fun init(scope: CoroutineScope) {
        val remoteConfig = try {
            FirebaseRemoteConfig.getInstance().apply {
                setConfigSettingsAsync(
                    FirebaseRemoteConfigSettings.Builder()
                        .setMinimumFetchIntervalInSeconds(3600)
                        .build()
                )
                setDefaultsAsync(
                    mapOf(
                        KEY_SERVER_IP to DEFAULT_SERVER_IP,
                        KEY_SERVER_PORT to DEFAULT_SERVER_PORT,
                        KEY_NOISE_PORT to DEFAULT_NOISE_PORT,
                        KEY_PRIVACY_URL to DEFAULT_PRIVACY_URL
                    )
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Firebase not configured, using defaults")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                suspendCancellableCoroutine<Unit> { cont ->
                    remoteConfig.fetchAndActivate()
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                applyConfig(remoteConfig)
                            }
                            cont.resume(Unit)
                        }
                        .addOnFailureListener { cont.resume(Unit) }
                }
            } catch (e: Exception) {
                Timber.w(e, "Remote config fetch failed, using defaults")
            }
        }
    }

    private fun applyConfig(remoteConfig: FirebaseRemoteConfig) {
        serverIp = remoteConfig.getString(KEY_SERVER_IP).ifEmpty { DEFAULT_SERVER_IP }
        serverPort = remoteConfig.getLong(KEY_SERVER_PORT).toInt().coerceIn(1, 65535).let {
            if (it == 0) DEFAULT_SERVER_PORT else it
        }
        noisePort = remoteConfig.getLong(KEY_NOISE_PORT).toInt().coerceIn(1, 65535).let {
            if (it == 0) DEFAULT_NOISE_PORT else it
        }
        privacyUrl = remoteConfig.getString(KEY_PRIVACY_URL).ifEmpty { DEFAULT_PRIVACY_URL }
        Timber.d("Remote config applied: server=$serverIp:$serverPort, noise=$noisePort")
    }
}
