package com.wingsheep.network.rotation

import com.wingsheep.encrypt.crypto.EciesDecryptor
import com.wingsheep.encrypt.identity.DeviceKeyManager
import com.wingsheep.network.api.ApiClient
import android.content.Context
import android.content.pm.PackageManager
import timber.log.Timber
import java.security.MessageDigest

data class RegistrationResult(
    val name: String,
    val color: Int,
    val groupKey: ByteArray,
    val serverNoisePk: ByteArray?
)

class RegistrationHandler(
    private val apiClient: ApiClient,
    private val context: Context,
    val deviceKeyManager: DeviceKeyManager = DeviceKeyManager,
    private val eciesDecryptor: EciesDecryptor = EciesDecryptor
) {
    suspend fun register(): RegistrationResult {
        Timber.i("── Registration start ──")

        deviceKeyManager.generateKeypair()

        val pkDev = deviceKeyManager.publicKeySec1()
        Timber.i("pk_dev (SEC1): ${pkDev.joinToString("") { "%02x".format(it) }}")
        Timber.i("pk_dev length: ${pkDev.size} bytes")

        val apkCertSha1 = computeApkCertSha1()
        Timber.i("apk_cert_sha1: $apkCertSha1")

        Timber.i("POST /register → sending pk_dev + apk_cert_sha1...")
        val response = apiClient.register(pkDev, apkCertSha1)
        val encBytes = response.encryptedSkCommBytes()
        Timber.i("Register response: name=\"${response.name}\" color=${response.color}")
        Timber.i("encrypted_sk_comm: ${encBytes.size} bytes")
        Timber.i("encrypted_sk_comm hex: ${encBytes.joinToString("") { "%02x".format(it) }}")

        val serverNoisePk = response.serverNoisePkBytes()
        if (serverNoisePk != null) {
            Timber.i("server_noise_pk: ${serverNoisePk.size} bytes")
        } else {
            Timber.w("server_noise_pk missing from register response")
        }

        Timber.i("ECIES decrypting group key...")
        val groupKey = eciesDecryptor.decrypt(
            encryptedPayload = encBytes,
            privateKey = deviceKeyManager.getPrivateKey()
        )
        Timber.i("Group key: ${groupKey.joinToString("") { "%02x".format(it) }}")
        Timber.i("Group key length: ${groupKey.size} bytes")

        Timber.i("── Registration complete: ${response.name} ──")
        return RegistrationResult(
            name = response.name,
            color = response.color,
            groupKey = groupKey,
            serverNoisePk = serverNoisePk
        )
    }

    /**
     * Computes the SHA-1 of the APK signing certificate as a 40-char uppercase hex string.
     *
     * Uses [PackageManager.GET_SIGNING_CERTIFICATES] to retrieve the signing
     * cert, then SHA-1 digests it. This is sent to the server so it can
     * verify the request comes from the trusted app (debug or release).
     * Also used as the Noise IK prologue.
     *
     * Reference: [Registration API Contract — apk_cert_sha1 field]
     */
    fun computeApkCertSha1(): String {
        val certBytes: ByteArray
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signingInfo = packageInfo.signingInfo
                ?: throw IllegalStateException("No signing info for ${context.packageName}")
            certBytes = signingInfo.apkContentsSigners[0].toByteArray()
        } else {
            @Suppress("DEPRECATION")
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            val signatures = packageInfo.signatures
                ?: throw IllegalStateException("No signatures for ${context.packageName}")
            certBytes = signatures[0].toByteArray()
        }
        val md = MessageDigest.getInstance("SHA-1")
        val sha1 = md.digest(certBytes)
        return sha1.joinToString("") { "%02X".format(it) }
    }
}