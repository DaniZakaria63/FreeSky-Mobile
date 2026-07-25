package com.wingsheep.network.rotation

import com.wingsheep.encrypt.CryptoError
import com.wingsheep.encrypt.CryptoResult
import com.wingsheep.encrypt.crypto.EciesDecryptor
import com.wingsheep.encrypt.crypto.EciesDecryptionException
import com.wingsheep.encrypt.identity.DeviceKeyManager
import com.wingsheep.encrypt.mls.MlsGroupManager
import com.wingsheep.network.noise.NoiseApiClient

class KeyRotationHandler(
    private val noiseApiClient: NoiseApiClient,
    private val mlsManager: MlsGroupManager,
    private val deviceKeyManager: DeviceKeyManager = DeviceKeyManager,
    private val eciesDecryptor: EciesDecryptor = EciesDecryptor
) {

    suspend fun handleKeyRotation(): CryptoResult<Unit> {
        val response = try {
            noiseApiClient.fetchNewGroupKey()
        } catch (e: Exception) {
            return CryptoResult.Error(CryptoError.KEY_ROTATION_FAILED)
        }

        val encryptedSkComm = response.encryptedSkCommBytes()

        val skComm = try {
            eciesDecryptor.decrypt(
                encryptedPayload = encryptedSkComm,
                privateKey = deviceKeyManager.getPrivateKey()
            )
        } catch (e: EciesDecryptionException) {
            return CryptoResult.Error(CryptoError.DECRYPTION_FAILED)
        } catch (e: Exception) {
            return CryptoResult.Error(CryptoError.KEY_ROTATION_FAILED)
        }

        val initResult = mlsManager.initFromKeyMaterial(
            groupStateBytes = skComm,
            identityKeyBytes = deviceKeyManager.publicKeySec1()
        )
        if (initResult.isError) {
            return initResult
        }

        return CryptoResult.Success(Unit)
    }
}
