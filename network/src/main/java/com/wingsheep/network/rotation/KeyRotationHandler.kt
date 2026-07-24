package com.wingsheep.network.rotation

import com.wingsheep.encrypt.CryptoError
import com.wingsheep.encrypt.CryptoResult
import com.wingsheep.encrypt.crypto.EciesDecryptor
import com.wingsheep.encrypt.crypto.EciesDecryptionException
import com.wingsheep.encrypt.identity.DeviceKeyManager
import com.wingsheep.encrypt.mls.MlsGroupManager
import com.wingsheep.network.api.ApiClient

/**
 * Handles community key rotation on Android.
 *
 * When the admin rotates the community key, every device receives a new
 * `encrypted_sk_comm`.  This class fetches it via [ApiClient], decrypts
 * it with the device key, and re-initialises the MLS group.
 *
 * Reference: `docs/android-encryption-guide.md` §6
 */
class KeyRotationHandler(
    private val apiClient: ApiClient,
    private val mlsManager: MlsGroupManager,
    private val deviceKeyManager: DeviceKeyManager = DeviceKeyManager,
    private val eciesDecryptor: EciesDecryptor = EciesDecryptor
) {

    /**
     * Handles a key rotation event.
     *
     * 1. Fetches the new `encrypted_sk_comm` from the server via [ApiClient].
     * 2. ECIES-decrypts it with the device private key.
     * 3. Re-initialises the MLS group with the new key material.
     *
     * @return [CryptoResult.Success] on completion, [CryptoResult.Error] on failure.
     */
    suspend fun handleKeyRotation(): CryptoResult<Unit> {
        // Step 1: Fetch new encrypted group key from server
        val response = try {
            apiClient.fetchNewGroupKey()
        } catch (e: Exception) {
            return CryptoResult.Error(CryptoError.KEY_ROTATION_FAILED)
        }

        val encryptedSkComm = try {
            hexDecode(response.encryptedSkComm)
        } catch (e: Exception) {
            return CryptoResult.Error(CryptoError.KEY_ROTATION_FAILED)
        }

        // Step 2: ECIES-decrypt with device private key
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

        // Step 3: Re-initialise MLS group with new key material
        val initResult = mlsManager.initFromKeyMaterial(
            groupStateBytes = skComm,
            identityKeyBytes = deviceKeyManager.publicKeySec1()
        )
        if (initResult.isError) {
            return initResult
        }

        return CryptoResult.Success(Unit)
    }

    // ── Private helpers ─────────────────────────────────────────────

    private fun hexDecode(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have even length" }
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
