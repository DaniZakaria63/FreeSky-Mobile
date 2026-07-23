package com.wingsheep.encrypt.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.io.ByteArrayOutputStream

/**
 * HKDF (HMAC-based Key Derivation Function) — RFC 5869.
 *
 * Implemented with `javax.crypto.Mac` + HmacSHA256 so it works on both
 * the Android JVM and the unit-test JVM without extra dependencies.
 *
 * Usage:
 * ```
 * val key = Hkdf.deriveKey(
 *     salt = sharedSecret,
 *     ikm  = nonce,
 *     info = "freesky-ecies-v1".toByteArray(),
 *     length = 32
 * )
 * ```
 */
object Hkdf {

    private const val HASH_NAME = "HmacSHA256"
    private const val HASH_LEN = 32  // SHA-256 output size in bytes

    /**
     * HKDF-Extract: `PRK = HMAC-Hash(salt, IKM)`.
     *
     * If [salt] is `null` or empty, a string of [HASH_LEN] zero bytes is used
     * (per RFC 5869 §2.1).
     */
    fun extract(salt: ByteArray?, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance(HASH_NAME)
        val keyBytes = salt?.takeIf { it.isNotEmpty() } ?: ByteArray(HASH_LEN) { 0 }
        mac.init(SecretKeySpec(keyBytes, HASH_NAME))
        return mac.doFinal(ikm)
    }

    /**
     * HKDF-Expand: `T(0) = empty, T(i) = HMAC-Hash(PRK, T(i-1) | info | i)`.
     *
     * Returns exactly [length] bytes.  If [length] exceeds 255 * [HASH_LEN]
     * the result is truncated (RFC 5869 limits output to 255 * HashLen).
     */
    fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length <= 255 * HASH_LEN) {
            "HKDF output length too large: $length > ${255 * HASH_LEN}"
        }

        val mac = Mac.getInstance(HASH_NAME)
        mac.init(SecretKeySpec(prk, HASH_NAME))

        val blocks = (length + HASH_LEN - 1) / HASH_LEN
        val out = ByteArrayOutputStream()
        var previous = ByteArray(0)

        for (i in 1..blocks) {
            mac.update(previous)
            mac.update(info)
            mac.update(i.toByte())
            previous = mac.doFinal()
            out.write(previous)
        }

        return out.toByteArray().copyOf(length)
    }

    /**
     * Full HKDF: `deriveKey = expand(extract(salt, ikm), info, length)`.
     */
    fun deriveKey(salt: ByteArray?, ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = extract(salt, ikm)
        return expand(prk, info, length)
    }
}
