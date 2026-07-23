package com.wingsheep.encrypt.identity

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.*
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec

/**
 * Layer 1 — Device Identity Keypair.
 *
 * Generates and stores an EC P-256 (secp256r1) keypair in AndroidKeyStore.
 * The private key never leaves hardware-backed storage; all signing and
 * key-agreement operations are performed by the Keystore provider.
 *
 * Key properties:
 * - Hardware-backed on devices with TEE / StrongBox
 * - Cannot be extracted even with root
 * - Survives app data wipe but NOT app uninstall
 *
 * Reference: `docs/android-encryption-guide.md` §2
 */
object DeviceKeyManager {

    // ── Constants ───────────────────────────────────────────────────

    private const val KEY_ALIAS = "freesky_device_identity"
    private const val KEYSTORE_TYPE = "AndroidKeyStore"
    private const val KEY_ALGORITHM = "EC"
    private const val CURVE = "secp256r1"  // NIST P-256
    private const val SIGN_ALGORITHM = "SHA256withECDSA"

    // ── Public API ──────────────────────────────────────────────────

    /** Returns `true` if a device keypair already exists in AndroidKeyStore. */
    fun keyExists(): Boolean {
        val ks = KeyStore.getInstance(KEYSTORE_TYPE).apply { load(null) }
        return ks.containsAlias(KEY_ALIAS)
    }

    /**
     * Generates a new P-256 keypair in AndroidKeyStore.
     *
     * Call this only when [keyExists] returns `false`.
     * If a key already exists, this is a no-op (the old key is not overwritten).
     */
    fun generateKeypair() {
        if (keyExists()) return

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).apply {
            setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
            setDigests(KeyProperties.DIGEST_SHA256)
            setUserAuthenticationRequired(false)
        }.build()

        val kpg = KeyPairGenerator.getInstance(KEY_ALGORITHM, KEYSTORE_TYPE)
        kpg.initialize(spec)
        kpg.generateKeyPair()
    }

    /** Returns the public key from AndroidKeyStore (X.509 SubjectPublicKeyInfo format). */
    fun getPublicKey(): PublicKey {
        val ks = KeyStore.getInstance(KEYSTORE_TYPE).apply { load(null) }
        return ks.getCertificate(KEY_ALIAS)?.publicKey
            ?: throw IllegalStateException("Device key not found — call generateKeypair() first")
    }

    /** Returns the private key reference from AndroidKeyStore. */
    fun getPrivateKey(): PrivateKey {
        val ks = KeyStore.getInstance(KEYSTORE_TYPE).apply { load(null) }
        val entry = ks.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Device key not found — call generateKeypair() first")
        return entry.privateKey
    }

    /** Serialises the public key to X.509 SubjectPublicKeyInfo bytes for the server API. */
    fun publicKeyBytes(): ByteArray = getPublicKey().encoded

    /** Signs [data] with ECDSA (SHA-256) using the KeyStore-backed private key. */
    fun sign(data: ByteArray): ByteArray {
        val sig = Signature.getInstance(SIGN_ALGORITHM)
        sig.initSign(getPrivateKey())
        sig.update(data)
        return sig.sign()
    }

    /** Verifies [signature] against [data] using [publicKey]. */
    fun verify(data: ByteArray, signature: ByteArray, publicKey: PublicKey): Boolean {
        val sig = Signature.getInstance(SIGN_ALGORITHM)
        sig.initVerify(publicKey)
        sig.update(data)
        return sig.verify(signature)
    }

    // ── Internal helpers ────────────────────────────────────────────

    /**
     * Parses a 65-byte SEC1 uncompressed P-256 public key
     * (`0x04 || X(32) || Y(32)`) into a [PublicKey].
     *
     * Used by [com.wingsheep.encrypt.crypto.EciesDecryptor] to parse
     * the ephemeral public key received in an ECIES payload.
     */
    internal fun sec1ToPublicKey(sec1: ByteArray): PublicKey {
        require(sec1.size == 65) {
            "Expected 65-byte SEC1 uncompressed key, got ${sec1.size}"
        }
        require(sec1[0] == 0x04.toByte()) {
            "Expected uncompressed point prefix 0x04, got 0x${sec1[0].toString(16)}"
        }

        val x = BigInteger(1, sec1.copyOfRange(1, 33))
        val y = BigInteger(1, sec1.copyOfRange(33, 65))
        val point = ECPoint(x, y)

        val kf = KeyFactory.getInstance(KEY_ALGORITHM)
        val keySpec = ECPublicKeySpec(point, secp256r1Params)
        return kf.generatePublic(keySpec)
    }

    /** Lazily-initialised P-256 parameter spec (cached to avoid regenerating key pairs). */
    private val secp256r1Params: java.security.spec.ECParameterSpec by lazy {
        val kpg = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        kpg.initialize(ECGenParameterSpec(CURVE))
        val kp = kpg.generateKeyPair()
        (kp.public as ECPublicKey).params
    }
}
