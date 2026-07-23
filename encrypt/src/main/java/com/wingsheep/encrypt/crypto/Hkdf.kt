package com.wingsheep.encrypt.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.io.ByteArrayOutputStream

object Hkdf {

    private const val HASH_NAME = "HmacSHA256"
    private const val HASH_LEN = 32

    fun extract(salt: ByteArray?, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance(HASH_NAME)
        val keyBytes = salt?.takeIf { it.isNotEmpty() } ?: ByteArray(HASH_LEN) { 0 }
        mac.init(SecretKeySpec(keyBytes, HASH_NAME))
        return mac.doFinal(ikm)
    }

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

    fun deriveKey(salt: ByteArray?, ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = extract(salt, ikm)
        return expand(prk, info, length)
    }
}