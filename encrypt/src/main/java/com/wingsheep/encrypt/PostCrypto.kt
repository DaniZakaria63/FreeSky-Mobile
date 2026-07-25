package com.wingsheep.encrypt

import android.util.Log
import com.wingsheep.encrypt.identity.DeviceKeyManager
import com.wingsheep.encrypt.mls.MlsGroupManager
import com.wingsheep.encrypt.model.EncryptedPost
import java.security.MessageDigest
import java.security.PublicKey

object PostCrypto {

    private const val TAG = "PostCrypto"

    fun createPost(
        content: String,
        mlsManager: MlsGroupManager,
        deviceKeyManager: DeviceKeyManager = DeviceKeyManager
    ): EncryptedPost? {
        val plaintext = content.toByteArray(Charsets.UTF_8)

        val encryptResult = mlsManager.encryptPost(plaintext)
        if (encryptResult.isError) {
            val error = encryptResult as CryptoResult.Error
            Log.w(TAG, "MLS encryption failed: ${error.reason}")
            return null
        }
        val ciphertext = encryptResult.getOrThrow()

        val hash = MessageDigest.getInstance("SHA-256").digest(ciphertext)
        val signature = try {
            deviceKeyManager.sign(hash)
        } catch (e: Exception) {
            Log.w(TAG, "Signing failed", e)
            return null
        }

        val epochResult = mlsManager.getCurrentEpoch()
        if (epochResult.isError) {
            val error = epochResult as CryptoResult.Error
            Log.w(TAG, "Failed to get MLS epoch: ${error.reason}")
            return null
        }
        val epoch = epochResult.getOrThrow()

        return EncryptedPost(
            ciphertextComm = ciphertext,
            authorPk = deviceKeyManager.publicKeySec1(),
            authorSig = signature,
            timestamp = System.currentTimeMillis(),
            mlsEpoch = epoch
        )
    }

    fun readPost(
        post: EncryptedPost,
        mlsManager: MlsGroupManager,
        authorPublicKey: PublicKey,
        deviceKeyManager: DeviceKeyManager = DeviceKeyManager
    ): String? {
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