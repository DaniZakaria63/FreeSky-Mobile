package com.wingsheep.encrypt.crypto

import com.wingsheep.encrypt.identity.DeviceKeyManager
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EciesDecryptor {

    private const val AES_ALGORITHM = "AES"
    private const val AES_GCM_TRANSFORM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val AES_KEY_LENGTH_BYTES = 32
    private const val ECDH_ALGORITHM = "ECDH"

    private val KDF_SALT = "freesky-ecies-v1".toByteArray(Charsets.UTF_8)
    private val KDF_INFO = "freesky-group-key".toByteArray(Charsets.UTF_8)

    fun decrypt(
        encryptedPayload: ByteArray,
        privateKey: PrivateKey
    ): ByteArray {
        val pkg = EciesPackage.deserialize(encryptedPayload)

        val ephemeralPubKey = parseEphemeralPublicKey(pkg.ephemeralPublicKey)
        val sharedSecret = performEcdh(privateKey, ephemeralPubKey)

        val aesKey = Hkdf.deriveKey(
            salt = KDF_SALT,
            ikm = sharedSecret,
            info = KDF_INFO,
            length = AES_KEY_LENGTH_BYTES
        )

        return try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, pkg.nonce)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, AES_ALGORITHM), spec)
            cipher.doFinal(pkg.ciphertext)
        } catch (e: Exception) {
            throw EciesDecryptionException(
                "AES-GCM decryption failed — ciphertext may be tampered or key mismatch",
                e
            )
        }
    }

    private fun parseEphemeralPublicKey(sec1: ByteArray): PublicKey {
        return try {
            DeviceKeyManager.sec1ToPublicKey(sec1)
        } catch (e: Exception) {
            throw EciesDecryptionException("Failed to parse ephemeral public key", e)
        }
    }

    private fun performEcdh(privateKey: PrivateKey, ephemeralPubKey: PublicKey): ByteArray {
        return try {
            val ka = KeyAgreement.getInstance(ECDH_ALGORITHM)
            ka.init(privateKey)
            ka.doPhase(ephemeralPubKey, true)
            ka.generateSecret()
        } catch (e: Exception) {
            throw EciesDecryptionException("ECDH key agreement failed", e)
        }
    }
}

class EciesDecryptionException(message: String, cause: Throwable? = null) :
    Exception(message, cause)