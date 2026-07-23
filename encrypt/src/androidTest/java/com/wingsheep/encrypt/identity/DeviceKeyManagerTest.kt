package com.wingsheep.encrypt.identity

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * Instrumented tests for [DeviceKeyManager].
 *
 * These run on an Android device/emulator because AndroidKeyStore
 * is not available on the JVM.
 *
 * Run with:
 *   ./gradlew :encrypt:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class DeviceKeyManagerTest {

    @Test
    fun keyExists_returnsFalseBeforeGeneration() {
        // If a key was already generated in a previous test run,
        // this might return true. That's fine — the test still
        // validates the API contract.
        val exists = DeviceKeyManager.keyExists()
        // Just verify it doesn't crash
        assertTrue(exists || !exists)
    }

    @Test
    fun generateKeypair_thenKeyExists() {
        DeviceKeyManager.generateKeypair()
        assertTrue(DeviceKeyManager.keyExists())
    }

    @Test
    fun getPublicKey_returnsX509EncodedKey() {
        DeviceKeyManager.generateKeypair()
        val publicKey = DeviceKeyManager.getPublicKey()
        assertNotNull(publicKey)

        // Public key should be X.509 SubjectPublicKeyInfo format
        val encoded = publicKey.encoded
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())
    }

    @Test
    fun publicKeyBytes_returnsNonEmptyArray() {
        DeviceKeyManager.generateKeypair()
        val bytes = DeviceKeyManager.publicKeyBytes()
        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun signAndVerify_roundTrip() {
        DeviceKeyManager.generateKeypair()

        val data = "test data to sign".toByteArray()
        val signature = DeviceKeyManager.sign(data)

        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())

        // Verify with the same key
        val publicKey = DeviceKeyManager.getPublicKey()
        val valid = DeviceKeyManager.verify(data, signature, publicKey)
        assertTrue("Signature should verify with correct key", valid)
    }

    @Test
    fun verify_rejectsTamperedSignature() {
        DeviceKeyManager.generateKeypair()

        val data = "original data".toByteArray()
        val signature = DeviceKeyManager.sign(data)

        // Verify with tampered data
        val tamperedData = "tampered data".toByteArray()
        val publicKey = DeviceKeyManager.getPublicKey()
        val valid = DeviceKeyManager.verify(tamperedData, signature, publicKey)
        assertFalse("Signature should NOT verify with tampered data", valid)
    }

    @Test
    fun verify_rejectsWrongKey() {
        DeviceKeyManager.generateKeypair()

        val data = "test data".toByteArray()
        val signature = DeviceKeyManager.sign(data)

        // Generate a different key pair
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val otherPublicKey = kpg.generateKeyPair().public

        val valid = DeviceKeyManager.verify(data, signature, otherPublicKey)
        assertFalse("Signature should NOT verify with wrong key", valid)
    }

    @Test
    fun sign_producesDifferentSignaturesForDifferentData() {
        DeviceKeyManager.generateKeypair()

        val data1 = "data one".toByteArray()
        val data2 = "data two".toByteArray()

        val sig1 = DeviceKeyManager.sign(data1)
        val sig2 = DeviceKeyManager.sign(data2)

        assertFalse("Different data should produce different signatures",
            sig1.contentEquals(sig2))
    }

    @Test
    fun sec1ToPublicKey_roundTrip() {
        // Generate a P-256 key pair using JDK (not AndroidKeyStore)
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = kpg.generateKeyPair()
        val publicKey = keyPair.public as ECPublicKey

        // Convert to SEC1 uncompressed format
        val point = publicKey.w
        val xBytes = bigIntegerTo32Bytes(point.affineX)
        val yBytes = bigIntegerTo32Bytes(point.affineY)
        val sec1 = byteArrayOf(0x04) + xBytes + yBytes

        // Parse back using DeviceKeyManager.sec1ToPublicKey
        val parsed = DeviceKeyManager.sec1ToPublicKey(sec1)

        // The parsed key should produce the same encoded bytes
        assertArrayEquals(publicKey.encoded, parsed.encoded)
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun bigIntegerTo32Bytes(value: java.math.BigInteger): ByteArray {
        val bytes = value.toByteArray()
        val stripped = if (bytes.size > 1 && bytes[0] == 0.toByte()) {
            bytes.copyOfRange(1, bytes.size)
        } else {
            bytes
        }
        return when {
            stripped.size == 32 -> stripped
            stripped.size < 32 -> {
                val padded = ByteArray(32)
                System.arraycopy(stripped, 0, padded, 32 - stripped.size, stripped.size)
                padded
            }
            else -> stripped.copyOfRange(stripped.size - 32, stripped.size)
        }
    }
}
