package com.wingsheep.encrypt.identity

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import timber.log.Timber
import java.math.BigInteger
import java.security.*
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec

object DeviceKeyManager {

    private const val KEY_ALIAS = "freesky_device_identity"
    private const val KEYSTORE_TYPE = "AndroidKeyStore"
    private const val KEY_ALGORITHM = "EC"
    private const val CURVE = "secp256r1"
    private const val SIGN_ALGORITHM = "SHA256withECDSA"

    fun keyExists(): Boolean {
        val ks = KeyStore.getInstance(KEYSTORE_TYPE).apply { load(null) }
        val exists = ks.containsAlias(KEY_ALIAS)
        Timber.d("keyExists: $exists")
        return exists
    }

    fun generateKeypair() {
        val ks = KeyStore.getInstance(KEYSTORE_TYPE).apply { load(null) }
        if (ks.containsAlias(KEY_ALIAS)) {
            Timber.d("generateKeypair: removing stale key for purpose update")
            ks.deleteEntry(KEY_ALIAS)
        }

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY or KeyProperties.PURPOSE_AGREE_KEY
        ).apply {
            setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
            setDigests(KeyProperties.DIGEST_SHA256)
            setUserAuthenticationRequired(false)
        }.build()

        val kpg = KeyPairGenerator.getInstance(KEY_ALGORITHM, KEYSTORE_TYPE)
        kpg.initialize(spec)
        kpg.generateKeyPair()
        Timber.i("Device keypair generated — curve: $CURVE, alias: $KEY_ALIAS")
    }

    fun getPublicKey(): PublicKey {
        val ks = KeyStore.getInstance(KEYSTORE_TYPE).apply { load(null) }
        return ks.getCertificate(KEY_ALIAS)?.publicKey
            ?: throw IllegalStateException("Device key not found — call generateKeypair() first")
    }

    fun getPrivateKey(): PrivateKey {
        val ks = KeyStore.getInstance(KEYSTORE_TYPE).apply { load(null) }
        val entry = ks.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Device key not found — call generateKeypair() first")
        return entry.privateKey
    }

    fun publicKeyBytes(): ByteArray = getPublicKey().encoded

    fun publicKeySec1(): ByteArray {
        val pubKey = getPublicKey() as java.security.interfaces.ECPublicKey
        val w = pubKey.w
        val x = normalizeTo32Bytes(w.affineX.toByteArray())
        val y = normalizeTo32Bytes(w.affineY.toByteArray())
        val sec1 = byteArrayOf(0x04) + x + y
        Timber.d("publicKeySec1: ${sec1.size} bytes — 04 ${x.joinToString("") { "%02x".format(it) }}...")
        return sec1
    }

    internal fun normalizeTo32Bytes(input: ByteArray): ByteArray {
        if (input.size == 32) return input
        if (input.size < 32) return ByteArray(32 - input.size) + input
        return input.copyOfRange(input.size - 32, input.size)
    }

    fun sign(data: ByteArray): ByteArray {
        val sig = Signature.getInstance(SIGN_ALGORITHM)
        sig.initSign(getPrivateKey())
        sig.update(data)
        return sig.sign()
    }

    fun verify(data: ByteArray, signature: ByteArray, publicKey: PublicKey): Boolean {
        val sig = Signature.getInstance(SIGN_ALGORITHM)
        sig.initVerify(publicKey)
        sig.update(data)
        return sig.verify(signature)
    }

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

    private val secp256r1Params: java.security.spec.ECParameterSpec by lazy {
        val kpg = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        kpg.initialize(ECGenParameterSpec(CURVE))
        val kp = kpg.generateKeyPair()
        (kp.public as ECPublicKey).params
    }
}