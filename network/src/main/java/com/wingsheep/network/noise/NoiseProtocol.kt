package com.wingsheep.network.noise

import org.bouncycastle.crypto.digests.Blake2sDigest
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyAgreement

private const val HASH_LEN = 32
private const val AUTH_TAG_LEN = 16
private const val SEC1_UNCOMPRESSED_LEN = 65
private const val P256_PRIV_LEN = 32

internal class NoiseCipherState(
    val key: ByteArray?,
    private var nonce: Long = 0
) {
    fun encrypt(plaintext: ByteArray, ad: ByteArray): ByteArray {
        if (key == null) return plaintext
        val n = nonce++
        val cipher = ChaCha20Poly1305()
        cipher.init(true, AEADParameters(KeyParameter(key), AUTH_TAG_LEN * 8, chaChaNonce(n), ad))
        val out = ByteArray(plaintext.size + AUTH_TAG_LEN)
        val len = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        cipher.doFinal(out, len)
        return out
    }

    fun decrypt(ciphertext: ByteArray, ad: ByteArray): ByteArray? {
        if (key == null) return ciphertext
        val n = nonce++
        val cipher = ChaCha20Poly1305()
        cipher.init(false, AEADParameters(KeyParameter(key), AUTH_TAG_LEN * 8, chaChaNonce(n), ad))
        val out = ByteArray(ciphertext.size - AUTH_TAG_LEN)
        try {
            val len = cipher.processBytes(ciphertext, 0, ciphertext.size, out, 0)
            cipher.doFinal(out, len)
            return out
        } catch (_: Exception) {
            return null
        }
    }

    private fun chaChaNonce(n: Long): ByteArray {
        return ByteArray(4) { 0x00 } + byteArrayOf(
            (n shr 56).toByte(), (n shr 48).toByte(), (n shr 40).toByte(), (n shr 32).toByte(),
            (n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte()
        )
    }
}

private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
    val blockSize = 64
    val k = if (key.size > blockSize) blake2s(key) else key
    val kPad = ByteArray(blockSize) { if (it < k.size) k[it] else 0x00.toByte() }
    val iPad = ByteArray(blockSize) { (kPad[it].toInt() xor 0x36).toByte() }
    val oPad = ByteArray(blockSize) { (kPad[it].toInt() xor 0x5c).toByte() }
    return blake2s(oPad + blake2s(iPad + data))
}

internal fun blake2s(data: ByteArray): ByteArray {
    val d = Blake2sDigest()
    d.update(data, 0, data.size)
    val out = ByteArray(HASH_LEN)
    d.doFinal(out, 0)
    return out
}

internal fun hkdf(ck: ByteArray, input: ByteArray, count: Int): List<ByteArray> {
    val temp = hmac(ck, input + byteArrayOf(0x01.toByte()))
    val out1 = hmac(temp, byteArrayOf(0x02.toByte()))
    if (count == 1) return listOf(out1)
    val out2 = hmac(temp, out1 + byteArrayOf(0x03.toByte()))
    return listOf(out1, out2)
}

internal data class NoiseSymmetry(
    val ck: ByteArray,
    val h: ByteArray,
    val cs: NoiseCipherState
) {
    fun mixKey(inputKeyMaterial: ByteArray): NoiseSymmetry {
        val (newCk, cipherKey) = hkdf(ck, inputKeyMaterial, 2)
        return copy(ck = newCk, cs = NoiseCipherState(cipherKey))
    }

    fun mixHash(data: ByteArray): NoiseSymmetry {
        return copy(h = blake2s(h + data))
    }

    fun encryptAndHash(plaintext: ByteArray): Pair<NoiseSymmetry, ByteArray> {
        val ct = cs.encrypt(plaintext, h)
        val s = mixHash(ct)
        return s to ct
    }

    fun decryptAndHash(ciphertext: ByteArray): Pair<NoiseSymmetry, ByteArray>? {
        val pt = cs.decrypt(ciphertext, h) ?: return null
        val s = mixHash(ciphertext)
        return s to pt
    }

    fun split(): Pair<NoiseCipherState, NoiseCipherState> {
        val (k1, k2) = hkdf(ck, ByteArray(0), 2)
        return NoiseCipherState(k1) to NoiseCipherState(k2)
    }

    companion object {
        fun initialize(protocolName: String): NoiseSymmetry {
            val nameBytes = protocolName.toByteArray()
            val h = if (nameBytes.size <= HASH_LEN)
                nameBytes + ByteArray(HASH_LEN - nameBytes.size)
            else
                blake2s(nameBytes)
            return NoiseSymmetry(ck = h, h = h, cs = NoiseCipherState(null))
        }
    }
}

internal fun generateP256KeyPair(): KeyPair {
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"))
    return kpg.generateKeyPair()
}

internal fun p256PublicKeyBytes(kp: KeyPair): ByteArray {
    val pub = kp.public as ECPublicKey
    val w = pub.w
    val x = normalizeP256(w.affineX.toByteArray())
    val y = normalizeP256(w.affineY.toByteArray())
    return byteArrayOf(0x04) + x + y
}

internal fun ecdh(privateKey: java.security.PrivateKey, publicKeyBytes: ByteArray): ByteArray {
    val pubKey = sec1ToPublicKey(publicKeyBytes)
    val ka = KeyAgreement.getInstance("ECDH")
    ka.init(privateKey)
    ka.doPhase(pubKey, true)
    return ka.generateSecret()
}

private fun sec1ToPublicKey(sec1: ByteArray): java.security.PublicKey {
    val kf = java.security.KeyFactory.getInstance("EC")
    val x = java.math.BigInteger(1, sec1.copyOfRange(1, 33))
    val y = java.math.BigInteger(1, sec1.copyOfRange(33, 65))
    val point = java.security.spec.ECPoint(x, y)
    val params = secp256r1Params()
    return kf.generatePublic(java.security.spec.ECPublicKeySpec(point, params))
}

    private var cachedParams: java.security.spec.ECParameterSpec? = null

private fun secp256r1Params(): java.security.spec.ECParameterSpec {
    if (cachedParams != null) return cachedParams!!
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"))
    val kp = kpg.generateKeyPair()
    val p = (kp.public as ECPublicKey).params
    cachedParams = p
    return p
}

internal fun normalizeP256(input: ByteArray): ByteArray {
    if (input.size == 32) return input
    if (input.size < 32) return ByteArray(32 - input.size) + input
    return input.copyOfRange(input.size - 32, input.size)
}
