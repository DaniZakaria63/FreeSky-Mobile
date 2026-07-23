package com.wingsheep.encrypt

import android.util.Log
import com.wingsheep.encrypt.identity.DeviceKeyManager
import com.wingsheep.encrypt.mls.MlsGroupManager
import com.wingsheep.encrypt.model.EncryptedPost
import java.security.MessageDigest
import java.security.PublicKey

/**
 * Post encryption and signing helpers.
 *
 * Every post combines MLS encryption (confidentiality) with ECDSA
 * signing (authenticity).  The signature is over the ciphertext —
 * the server can verify authorship without decrypting.
 *
 * Reference: `docs/android-encryption-guide.md` §5
 */
object PostCrypto {

    private const val TAG = "PostCrypto"

    /**
     * Creates an encrypted, signed post.
     *
     * 1. Encrypts [content] with the MLS group key.
     * 2. Signs `SHA-256(ciphertext)` with the device's ECDSA key.
     *
     * @return The [EncryptedPost], or `null` if MLS is not initialised.
     */
    fun createPost(
        content: String,
        mlsManager: MlsGroupManager,
        deviceKeyManager: DeviceKeyManager = DeviceKeyManager
    ): EncryptedPost? {
        val plaintext = content.toByteArray(Charsets.UTF_8)

        // Step 1: Encrypt with MLS group key
        val encryptResult = mlsManager.encryptPost(plaintext)
        if (encryptResult.isError) {
            val error = encryptResult as CryptoResult.Error
            Log.w(TAG, "MLS encryption failed: ${error.reason}")
            return null
        }
        val ciphertext = encryptResult.getOrThrow()

        // Step 2: Sign the ciphertext (not plaintext — server can verify without decrypting)
        val hash = MessageDigest.getInstance("SHA-256").digest(ciphertext)
        val signature = try {
            deviceKeyManager.sign(hash)
        } catch (e: Exception) {
            Log.w(TAG, "Signing failed", e)
            return null
        }

        // Step 3: Get current epoch
        val epochResult = mlsManager.getCurrentEpoch()
        val epoch = epochResult.getOrThrow()

        return EncryptedPost(
            ciphertextComm = ciphertext,
            authorPk = deviceKeyManager.publicKeyBytes(),
            authorSig = signature,
            timestamp = System.currentTimeMillis(),
            mlsEpoch = epoch
        )
    }

    /**
     * Reads and verifies an encrypted post.
     *
     * 1. Verifies the ECDSA signature over `SHA-256(ciphertext)`.
     * 2. Decrypts the ciphertext with the MLS group key.
     *
     * @return The plaintext string, or `null` if verification or decryption fails.
     */
    fun readPost(
        post: EncryptedPost,
        mlsManager: MlsGroupManager,
        authorPublicKey: PublicKey,
        deviceKeyManager: DeviceKeyManager = DeviceKeyManager
    ): String? {
        // Step 1: Verify signature
        val hash = MessageDigest.getInstance("SHA-256").digest(post.ciphertextComm)
        val signatureValid = try {
            deviceKeyManager.verify(hash, post.authorSig, authorPublicKey)
        } catch (e: Exception) {
            Log.w(TAG, "Signature verification error", e)
            false
        }
        if (!signatureValid) {
            Log.w(TAG, "Post signature invalid — possible tamper or forgery")
            return null
        }

        // Step 2: Decrypt with MLS group key
        val decryptResult = mlsManager.decryptPost(post.ciphertextComm)
        if (decryptResult.isError) {
            val error = decryptResult as CryptoResult.Error
            Log.w(TAG, "MLS decryption failed: ${error.reason}")
            return null
        }
        val plaintext = decryptResult.getOrThrow()

        return plaintext.toString(Charsets.UTF_8)
    }
}
