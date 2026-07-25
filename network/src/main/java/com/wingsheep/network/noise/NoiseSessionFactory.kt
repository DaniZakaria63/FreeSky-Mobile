package com.wingsheep.network.noise

import com.wingsheep.encrypt.identity.DeviceKeyManager
import timber.log.Timber
import java.security.MessageDigest

/**
 * Lazily builds a [NoiseApiClient] for the post-registration Noise transport.
 *
 * The Noise IK handshake requires:
 *  - the device's secp256r1 private key (AndroidKeyStore)
 *  - the device's 65-byte SEC1 public key
 *  - the server's Noise public key (returned in the /register response, persisted)
 *  - the prologue = APK signing-cert SHA-1 (same value the server checks at register)
 *
 * Because the server noise pk is only known after registration, the session
 * is established on demand via [establishSession] using the persisted key.
 *
 * Reference: PROTOCOL_SYNC.md §3.2 — Noise IK Handshake
 */
class NoiseSessionFactory(
    private val host: String,
    private val port: Int,
    private val deviceKeyManager: DeviceKeyManager = DeviceKeyManager
) {
    /**
     * Establish a Noise IK session and return a connected [NoiseApiClient].
     *
     * @param serverNoisePk  65-byte SEC1 server Noise static public key
     * @param apkCertSha1Hex  APK signing-cert SHA-1 hex (the Noise prologue)
     */
    suspend fun establishSession(
        serverNoisePk: ByteArray,
        apkCertSha1Hex: String
    ): NoiseApiClient {
        require(serverNoisePk.size == 65) {
            "server_noise_pk must be 65-byte SEC1, got ${serverNoisePk.size}"
        }
        val prologue = apkCertSha1Hex.uppercase().toByteArray(Charsets.UTF_8)

        val manager = NoiseManager(
            host = host,
            port = port,
            devicePrivateKey = deviceKeyManager.getPrivateKey(),
            devicePublicKeySec1 = deviceKeyManager.publicKeySec1(),
            serverNoisePk = serverNoisePk,
            prologue = prologue
        )
        Timber.i("NoiseSessionFactory: establishing Noise IK session to $host:$port")
        manager.establishSession()
        return NoiseApiClient(manager)
    }
}
