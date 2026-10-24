package com.wingsheep.encrypt

import com.wingsheep.encrypt.identity.DeviceKeyManager
import com.wingsheep.encrypt.mls.MlsGroupManager
import com.wingsheep.encrypt.model.EncryptedPost
import timber.log.Timber
import java.security.PublicKey
import javax.inject.Inject

/**
 * Creates and reads the signed, encrypted post envelope the server stores.
 *
 * Write path: encrypt with the group key, then sign the RAW ciphertext with the
 * device key. `SHA256withECDSA` hashes once internally, matching the server's
 * `p256` verification which also hashes once — pre-hashing here would double-hash
 * and the server would reject every post.
 *
 * Read path: verify the signature over the raw ciphertext first, then decrypt, so
 * a tampered or forged post is never handed to the cipher.
 */
class PostCrypto @Inject constructor(
    private val deviceKeys: DeviceKeyManager
) {

    fun createPost(
        content: String,
        mls: MlsGroupManager,
        parentId: Long? = null
    ): CryptoResult<EncryptedPost> {
        val sealed = when (val result = mls.encryptPostWithEpoch(content.toByteArray(Charsets.UTF_8))) {
            is CryptoResult.Error -> return failure("MLS encryption failed", result.reason)
            is CryptoResult.Success -> result.data
        }

        val signature = try {
            deviceKeys.sign(sealed.ciphertext)
        } catch (e: Exception) {
            return failure("Signing failed", CryptoError.SIGNING_FAILED, e)
        }

        return CryptoResult.Success(
            EncryptedPost(
                ciphertextComm = sealed.ciphertext,
                authorPk = deviceKeys.publicKeySec1(),
                authorSig = signature,
                timestamp = System.currentTimeMillis(),
                mlsEpoch = sealed.epoch,
                parentId = parentId
            )
        )
    }

    fun readPost(
        post: EncryptedPost,
        mls: MlsGroupManager,
        authorPublicKey: PublicKey
    ): CryptoResult<String> {
        val signatureValid = try {
            deviceKeys.verify(post.ciphertextComm, post.authorSig, authorPublicKey)
        } catch (e: Exception) {
            return failure("Signature verification failed", CryptoError.SIGNATURE_INVALID, e)
        }
        if (!signatureValid) {
            return failure("Post signature invalid — possible tamper or forgery", CryptoError.POST_SIGNATURE_INVALID)
        }

        val plaintext = when (val result = mls.decryptPost(post.ciphertextComm)) {
            is CryptoResult.Error -> return failure("MLS decryption failed", result.reason)
            is CryptoResult.Success -> result.data
        }
        return CryptoResult.Success(plaintext.toString(Charsets.UTF_8))
    }

    private fun failure(
        message: String,
        reason: CryptoError,
        cause: Throwable? = null
    ): CryptoResult.Error {
        if (cause == null) {
            Timber.w("%s (%s)", message, reason)
        } else {
            Timber.w(cause, "%s (%s)", message, reason)
        }
        return CryptoResult.Error(reason)
    }
}
