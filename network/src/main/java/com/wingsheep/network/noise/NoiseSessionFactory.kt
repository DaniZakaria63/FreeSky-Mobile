package com.wingsheep.network.noise

import com.wingsheep.encrypt.identity.DeviceKeyManager
import com.wingsheep.network.config.RemoteConfigManager
import timber.log.Timber
import javax.inject.Inject

/**
 * Builds [NoiseApiClient] sessions for the post-registration Noise transport.
 *
 * The NK handshake needs the device's secp256r1 key (AndroidKeyStore), the server's
 * Noise static key (returned by `/register`, then persisted) and the prologue — the
 * APK signing-cert SHA-1 hex string, the same value the server validates at
 * registration.
 *
 * Reference: PROTOCOL_SYNC.md §3.2 — Noise NK Handshake
 */
class NoiseSessionFactory @Inject constructor(
    private val remoteConfig: RemoteConfigManager,
    private val deviceKeys: DeviceKeyManager
) {

    /**
     * Establishes a session and returns a connected client.
     *
     * @param serverNoisePk 65-byte SEC1 server Noise static public key.
     * @param apkCertSha1Hex APK signing-cert SHA-1 hex, used as the Noise prologue.
     */
    suspend fun establishSession(serverNoisePk: ByteArray, apkCertSha1Hex: String): NoiseApiClient {
        require(serverNoisePk.size == NOISE_STATIC_KEY_BYTES) {
            "server_noise_pk must be $NOISE_STATIC_KEY_BYTES-byte SEC1, was ${serverNoisePk.size}"
        }

        // Resolved per session: the remote config arrives asynchronously, so a
        // snapshot taken at construction would pin the process to the defaults.
        val settings = remoteConfig.settings.value

        val manager = NoiseManager(
            host = settings.serverIp,
            port = settings.noisePort,
            devicePrivateKey = deviceKeys.getPrivateKey(),
            serverStaticKey = serverNoisePk,
            // The prologue is the hex *string*, not a hash of it.
            prologue = apkCertSha1Hex.uppercase().toByteArray(Charsets.UTF_8)
        )

        Timber.i("Establishing Noise NK session to %s:%d", settings.serverIp, settings.noisePort)
        try {
            manager.establishSession()
        } catch (t: Throwable) {
            // Never leave a half-open session (socket, reader thread) behind; this
            // also covers cancellation while connecting.
            manager.close()
            throw t
        }
        return NoiseApiClient(manager)
    }

    private companion object {
        const val NOISE_STATIC_KEY_BYTES = 65
    }
}
