package com.wingsheep.network.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Remote-configuration source for server addresses and public links.
 *
 * The values are fetched once per process, asynchronously, and published as an
 * immutable [RemoteSettings] snapshot. Consumers read the flow (or its latest
 * value when they need a synchronous read), never a snapshot taken at
 * construction — the fetch finishes long after the object graph is built.
 */
@Singleton
class RemoteConfigManager @Inject constructor() {

    private val fetchStarted = AtomicBoolean(false)

    /**
     * Process-lived on purpose: this singleton lives as long as the application,
     * so the fetch is intentionally not tied to any screen or component scope.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _settings = MutableStateFlow(RemoteSettings.DEFAULT)

    /** Current endpoints; starts at [RemoteSettings.DEFAULT] and updates once fetched. */
    val settings: StateFlow<RemoteSettings> = _settings.asStateFlow()

    /** Starts the one-shot fetch. Idempotent; the fetch runs at most once per process. */
    fun start() {
        if (!fetchStarted.compareAndSet(false, true)) return
        scope.launch { fetchAndApply() }
    }

    private suspend fun fetchAndApply() {
        val remoteConfig = firebaseOrNull() ?: return

        val activated = try {
            awaitFetch(remoteConfig)
        } catch (e: Exception) {
            Timber.w(e, "Remote config fetch failed, using built-in defaults")
            false
        }
        if (!activated) {
            Timber.w("Remote config not activated, using built-in defaults")
            return
        }

        // A malformed remote value must never crash the process: keep the previous
        // snapshot and log instead.
        runCatching { apply(remoteConfig) }
            .onFailure { Timber.w(it, "Remote config apply failed, keeping previous values") }
    }

    /**
     * Firebase initialisation throws when the app was built without
     * `google-services.json` (unit tests, CI) — fall back to the defaults there.
     */
    private fun firebaseOrNull(): FirebaseRemoteConfig? = runCatching {
        FirebaseRemoteConfig.getInstance().apply {
            setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(MINIMUM_FETCH_INTERVAL_SECONDS)
                    .build()
            )
            setDefaultsAsync(
                mapOf(
                    KEY_SERVER_IP to RemoteSettings.DEFAULT_SERVER_IP,
                    KEY_SERVER_PORT to RemoteSettings.DEFAULT_SERVER_PORT.toLong(),
                    KEY_NOISE_PORT to RemoteSettings.DEFAULT_NOISE_PORT.toLong(),
                    KEY_PRIVACY_POLICY_URL to RemoteSettings.DEFAULT_PRIVACY_POLICY_URL
                )
            )
        }
    }.onFailure { Timber.w(it, "Firebase not configured, using built-in defaults") }
        .getOrNull()

    private suspend fun awaitFetch(remoteConfig: FirebaseRemoteConfig): Boolean =
        suspendCancellableCoroutine { continuation ->
            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                // The listener fires for both success and failure; resume once.
                if (continuation.isActive) continuation.resume(task.isSuccessful)
            }
        }

    private fun apply(remoteConfig: FirebaseRemoteConfig) {
        val defaults = RemoteSettings.DEFAULT
        val serverIp = remoteConfig.getString(KEY_SERVER_IP).ifEmpty { defaults.serverIp }
        val serverPort = remoteConfig.port(KEY_SERVER_PORT, defaults.serverPort)

        val applied = RemoteSettings(
            serverIp = serverIp,
            serverPort = serverPort,
            noisePort = remoteConfig.port(KEY_NOISE_PORT, defaults.noisePort),
            // An HTTPS tunnel hostname in production; direct HTTP on the Noise host
            // when the key is absent (dev builds, older configs).
            apiBaseUrl = remoteConfig.getString(KEY_API_BASE_URL)
                .ifEmpty { "http://$serverIp:$serverPort" },
            privacyPolicyUrl = remoteConfig.getString(KEY_PRIVACY_POLICY_URL)
                .ifEmpty { defaults.privacyPolicyUrl }
        )
        _settings.value = applied
        Timber.i(
            "Remote settings applied: api=%s noise=%s:%d",
            applied.apiBaseUrl,
            applied.serverIp,
            applied.noisePort
        )
    }

    /**
     * Reads a port, falling back to [default] when the key is missing or out of
     * range. Coercion is deliberately not used: `coerceIn` turns a missing key
     * into port 1 and hides the mistake.
     */
    private fun FirebaseRemoteConfig.port(key: String, default: Int): Int =
        getLong(key).toInt().takeIf { it in VALID_PORT_RANGE } ?: default

    private companion object {
        const val KEY_SERVER_IP = "server_ip"
        const val KEY_SERVER_PORT = "server_port"
        const val KEY_NOISE_PORT = "noise_port"
        const val KEY_API_BASE_URL = "api_base_url"
        const val KEY_PRIVACY_POLICY_URL = "privacy_policy_url"

        const val MINIMUM_FETCH_INTERVAL_SECONDS = 3600L

        val VALID_PORT_RANGE = 1..65535
    }
}
