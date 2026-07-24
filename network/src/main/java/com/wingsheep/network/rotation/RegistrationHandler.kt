package com.wingsheep.network.rotation

import com.wingsheep.encrypt.crypto.EciesDecryptor
import com.wingsheep.encrypt.identity.DeviceKeyManager
import com.wingsheep.network.api.ApiClient
import timber.log.Timber

data class RegistrationResult(
    val name: String,
    val color: Int,
    val groupKey: ByteArray
)

class RegistrationHandler(
    private val apiClient: ApiClient,
    private val deviceKeyManager: DeviceKeyManager = DeviceKeyManager,
    private val eciesDecryptor: EciesDecryptor = EciesDecryptor
) {
    suspend fun register(): RegistrationResult {
        Timber.i("── Registration start ──")

        deviceKeyManager.generateKeypair()

        val pkDev = deviceKeyManager.publicKeySec1()
        Timber.i("pk_dev (SEC1): ${pkDev.joinToString("") { "%02x".format(it) }}")
        Timber.i("pk_dev length: ${pkDev.size} bytes")

        Timber.i("POST /register → sending pk_dev...")
        val response = apiClient.register(pkDev)
        val encBytes = response.encryptedSkCommBytes()
        Timber.i("Register response: name=\"${response.name}\" color=${response.color}")
        Timber.i("encrypted_sk_comm: ${encBytes.size} bytes")
        Timber.i("encrypted_sk_comm hex: ${encBytes.joinToString("") { "%02x".format(it) }}")

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
            groupKey = groupKey
        )
    }
}