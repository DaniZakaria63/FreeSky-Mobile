package com.wingsheep.network.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        const val KEY_API_BASE_URL = "api_base_url"
        const val KEY_PRIVACY_URL = "privacy_policy_url"

        /** Default Noise transport host (raw TCP; also the legacy direct-HTTP fallback). */
        const val DEFAULT_SERVER_IP = "139.99.88.23"
        const val DEFAULT_SERVER_PORT = 3000
        const val DEFAULT_NOISE_PORT = 9443
        const val DEFAULT_PRIVACY_URL = "https://www.dream-team.works/freesky/privacy-policy"
    }

    /**
     * Values below are written on the thread that completes the Firebase fetch and
     * read from network/worker threads, so every field is volatile: without it a
     * reader can keep seeing a stale address indefinitely.
     */

    /** Host for the Noise transport (raw TCP). */
    @Volatile
    var serverIp: String = DEFAULT_SERVER_IP
        private set

    @Volatile
    var serverPort: Int = DEFAULT_SERVER_PORT
        private set

    @Volatile
    var noisePort: Int = DEFAULT_NOISE_PORT
        private set

    /**
     * Base URL for the HTTP API (`/register`, `/server-pk`).
     *
     * In production this is an HTTPS Cloudflare-tunnel hostname
     * (`api_base_url` remote config key), while the Noise transport keeps
     * dialing [serverIp]:[noisePort] directly. Falls back to direct HTTP on
     * the Noise host when the key is absent (older configs / dev).
     */
    @Volatile
    var apiBaseUrl: String = "http://$DEFAULT_SERVER_IP:$DEFAULT_SERVER_PORT"
        private set

    private val _privacyUrl = MutableStateFlow(DEFAULT_PRIVACY_URL)

    /**
     * The privacy-policy URL, as an observable value.
     *
     * The URL is only known once the asynchronous fetch in [init] completes, so
     * consumers must collect this flow. Reading a single value at startup — which
     * is what this used to expose — always observes [DEFAULT_PRIVACY_URL] and
     * silently ignores a remote override.
     */
    val privacyUrlFlow: StateFlow<String> = _privacyUrl.asStateFlow()

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
                                // Never let a malformed remote value crash the
                                // main thread this listener runs on.
                                runCatching { applyConfig(remoteConfig) }
                                    .onFailure { Timber.w(it, "Remote config apply failed") }
                            } else {
                                Timber.w("Remote config fetch failed, using defaults")
                            }
                            // Single resume path: addOnCompleteListener fires for
                            // both success and failure.
                            if (cont.isActive) cont.resume(Unit)
                        }
                }
            } catch (e: Exception) {
                Timber.w(e, "Remote config fetch failed, using defaults")
            }
        }
    }

    private fun applyConfig(remoteConfig: FirebaseRemoteConfig) {
        serverIp = remoteConfig.getString(KEY_SERVER_IP).ifEmpty { DEFAULT_SERVER_IP }
        // The previous coerceIn(1, 65535) could never produce 0, so the "if it == 0"
        // guard was unreachable and a missing key would have coerced to port 1.
        serverPort = remoteConfig.getLong(KEY_SERVER_PORT).toInt()
            .takeIf { it in 1..65535 } ?: DEFAULT_SERVER_PORT
        noisePort = remoteConfig.getLong(KEY_NOISE_PORT).toInt()
            .takeIf { it in 1..65535 } ?: DEFAULT_NOISE_PORT
        // HTTPS tunnel hostname in production; direct LAN http for the dev
        // condition. Empty when the key is absent → fall back to direct HTTP.
        apiBaseUrl = remoteConfig
            .getString(KEY_API_BASE_URL)
            .ifEmpty { "http://$serverIp:$serverPort" }
        _privacyUrl.value = remoteConfig.getString(KEY_PRIVACY_URL).ifEmpty { DEFAULT_PRIVACY_URL }
        Timber.d("Remote config applied: api=$apiBaseUrl, noise=$serverIp:$noisePort")
    }
}
