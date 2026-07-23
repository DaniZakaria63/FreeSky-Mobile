package com.wingsheep.encrypt.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Unit tests for [Hkdf].
 */
class HkdfTest {

    @Test
    fun extract_producesCorrectLength() {
        val ikm = "test-input-key-material".toByteArray()
        val salt = "test-salt".toByteArray()

        val prk = Hkdf.extract(salt, ikm)

        // SHA-256 HMAC always produces 32 bytes
        assertEquals(32, prk.size)
    }

    @Test
    fun extract_withNullSalt_usesZeroBytes() {
        val ikm = "test-input-key-material".toByteArray()
        val prk = Hkdf.extract(null, ikm)

        assertEquals(32, prk.size)
    }

    @Test
    fun extract_matchesRawHmacSha256() {
        // Verify that Hkdf.extract is equivalent to raw HMAC-SHA256(salt, ikm)
        val salt = "my-salt-value".toByteArray()
        val ikm = "input-key-material".toByteArray()

        val expected = rawHmacSha256(salt, ikm)
        val actual = Hkdf.extract(salt, ikm)

        assertArrayEquals(expected, actual)
    }

    @Test
    fun extract_deterministic() {
        val salt = "salt".toByteArray()
        val ikm = "ikm".toByteArray()

        val result1 = Hkdf.extract(salt, ikm)
        val result2 = Hkdf.extract(salt, ikm)

        assertArrayEquals(result1, result2)
    }

    @Test
    fun expand_singleBlock() {
        val prk = "prk-key-material-32-bytes!".toByteArray().copyOf(32)
        val info = "context-info".toByteArray()

        val okm = Hkdf.expand(prk, info, 32)

        assertEquals(32, okm.size)
    }

    @Test
    fun expand_multiBlock() {
        val prk = ByteArray(32) { 0x42 }
        val info = "multi-block-test".toByteArray()

        val okm = Hkdf.expand(prk, info, 80)  // > 32 bytes → 3 blocks

        assertEquals(80, okm.size)
    }

    @Test
    fun expand_deterministic() {
        val prk = ByteArray(32) { 0x01 }
        val info = "info".toByteArray()

        val result1 = Hkdf.expand(prk, info, 48)
        val result2 = Hkdf.expand(prk, info, 48)

        assertArrayEquals(result1, result2)
    }

    @Test
    fun deriveKey_roundTrip() {
        val salt = "salt-value".toByteArray()
        val ikm = "input-key-material".toByteArray()
        val info = "context-info".toByteArray()

        val key1 = Hkdf.deriveKey(salt, ikm, info, 32)
        val key2 = Hkdf.deriveKey(salt, ikm, info, 32)

        assertArrayEquals(key1, key2)
        assertEquals(32, key1.size)
    }

    @Test
    fun deriveKey_differentInfo_producesDifferentKeys() {
        val salt = "salt-value".toByteArray()
        val ikm = "input-key-material".toByteArray()

        val key1 = Hkdf.deriveKey(salt, ikm, "context-a".toByteArray(), 32)
        val key2 = Hkdf.deriveKey(salt, ikm, "context-b".toByteArray(), 32)

        assertFalse(key1.contentEquals(key2))
    }

    @Test
    fun deriveKey_differentIkm_producesDifferentKeys() {
        val salt = "salt-value".toByteArray()
        val info = "context-info".toByteArray()

        val key1 = Hkdf.deriveKey(salt, "input-a".toByteArray(), info, 32)
        val key2 = Hkdf.deriveKey(salt, "input-b".toByteArray(), info, 32)

        assertFalse(key1.contentEquals(key2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun expand_tooLargeLength_throws() {
        val prk = ByteArray(32) { 0x01 }
        Hkdf.expand(prk, "info".toByteArray(), 256 * 32)
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /** Computes raw HMAC-SHA256 for test verification. */
    private fun rawHmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
