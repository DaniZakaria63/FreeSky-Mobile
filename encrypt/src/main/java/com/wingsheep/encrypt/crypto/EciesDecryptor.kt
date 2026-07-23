package com.wingsheep.encrypt.crypto

import com.wingsheep.encrypt.identity.DeviceKeyManager
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.KeyFactory
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Layer 2 — ECIES (Elliptic Curve Integrated Encryption Scheme) decryptor.
 *
 * The server encrypts the MLS group key (`sk_comm`) with the device's
 * public key using ECIES.  This class decrypts those payloads on Android.
 *
 * ECIES = ECDH + HKDF + AES-256-GCM
 *
 * Wire format (see [EciesPackage]):
 * ```
 * [ephemeral_pubkey (65 bytes)] [nonce (12 bytes)] [ciphertext]
 * ```
 *
 * Reference: `docs/android-encryption-guide.md` §3
 */
object EciesDecryptor {

    // ── Constants ───────────────────────────────────────────────────

    private const val AES_ALGORITHM = "AES"
    private const val AES_GCM_TRANSFORM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128  // 16-byte tag
    private const val AES_KEY_LENGTH_BYTES = 32  // AES-256
    private const val EC_CURVE = "secp256r1"     // P-256
    private const val ECDH_ALGORITHM = "ECDH"

    // KDF parameters (shared with server)
    private val KDF_SALT = "freesky-ecies-v1".toByteArray(Charsets.UTF_8)
    private val KDF_INFO = "freesky-group-key".toByteArray(Charsets.UTF_8)

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Decrypts an ECIES-encrypted payload.
     *
     * @param encryptedPayload The raw wire-format bytes
     *                         (`ephemeralPubKey || nonce || ciphertext`).
     * @param privateKey       The device's P-256 private key from AndroidKeyStore.
     * @return The decrypted plaintext.
     * @throws [EciesDecryptionException] if decryption fails.
     */
    fun decrypt(
        encryptedPayload: ByteArray,
        privateKey: PrivateKey
    ): ByteArray {
        val pkg = EciesPackage.deserialize(encryptedPayload)

        // Step 1: ECDH with the ephemeral public key
        val ephemeralPubKey = parseEphemeralPublicKey(pkg.ephemeralPublicKey)
        val sharedSecret = performEcdh(privateKey, ephemeralPubKey)

        // Step 2: HKDF-SHA256 to derive the AES-256 key
        val aesKey = Hkdf.deriveKey(
            salt = KDF_SALT,
            ikm = sharedSecret,
            info = KDF_INFO,
            length = AES_KEY_LENGTH_BYTES
        )

        // Step 3: AES-256-GCM decrypt
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

    // ── Private helpers ─────────────────────────────────────────────

    /**
     * Parses a 65-byte SEC1 uncompressed P-256 public key into a [PublicKey].
     * Delegates to [DeviceKeyManager.sec1ToPublicKey] for the actual conversion.
     */
    private fun parseEphemeralPublicKey(sec1: ByteArray): PublicKey {
        return try {
            DeviceKeyManager.sec1ToPublicKey(sec1)
        } catch (e: Exception) {
            throw EciesDecryptionException("Failed to parse ephemeral public key", e)
        }
    }

    /** Performs ECDH key agreement between [privateKey] and [ephemeralPubKey]. */
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

/** Thrown when ECIES decryption fails for any reason. */
class EciesDecryptionException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
