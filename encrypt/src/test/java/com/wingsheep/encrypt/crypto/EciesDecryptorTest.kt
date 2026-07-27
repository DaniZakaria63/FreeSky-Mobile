package com.wingsheep.encrypt.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import javax.crypto.KeyAgreement
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class EciesDecryptorTest {

    @Test
    fun roundTrip_decryptReturnsOriginalPlaintext() {
        val recipientKp = generateEcKeyPair()
        val recipientPub = recipientKp.public
        val recipientPriv = recipientKp.private

        val ephemeralKp = generateEcKeyPair()

        val sharedSecret = performEcdh(ephemeralKp.private, recipientPub)

        val aesKey = Hkdf.deriveKey(
            salt = "freesky-ecies-v1".toByteArray(Charsets.UTF_8),
            ikm = sharedSecret,
            info = "freesky-group-key".toByteArray(Charsets.UTF_8),
            length = 32
        )

        val plaintext = "Hello, MLS World! \uD83D\uDD10".toByteArray(Charsets.UTF_8)
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val ciphertext = aesGcmEncrypt(aesKey, nonce, plaintext)

        val ephemeralPubSec1 = publicKeyToSec1(ephemeralKp.public)
        val payload = ephemeralPubSec1 + nonce + ciphertext

        val decrypted = EciesDecryptor.decrypt(payload, recipientPriv)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun roundTrip_emptyPlaintext() {
        val recipientKp = generateEcKeyPair()
        val ephemeralKp = generateEcKeyPair()
        val sharedSecret = performEcdh(ephemeralKp.private, recipientKp.public)
        val aesKey = deriveAesKey(sharedSecret)
        val plaintext = byteArrayOf()
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val ciphertext = aesGcmEncrypt(aesKey, nonce, plaintext)
        val payload = publicKeyToSec1(ephemeralKp.public) + nonce + ciphertext

        val decrypted = EciesDecryptor.decrypt(payload, recipientKp.private)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun endToEnd_registerFlow_recovers32ByteGroupKey() {
        // Simulates the full POST /register → ECIES decrypt flow:
        //   1. Device generates secp256r1 keypair, sends 65-byte SEC1 pk_dev
        //   2. Server encrypts 32-byte group key via ecies_encrypt (crypto.rs)
        //   3. Android EciesDecryptor.decrypt recovers the group key
        //
        // The server's ecies_encrypt (crypto.rs) does:
        //   shared_secret = ECDH(ek_s, pk_dev)       // secp256r1
        //   aes_key = HKDF-SHA256(shared_secret, "freesky-ecies-v1", "freesky-group-key", 32)
        //   ciphertext = AES-256-GCM(aes_key, nonce, plaintext)  // includes 16-byte tag
        //   wire = epk_SEC1(65) || nonce(12) || ciphertext

        // Step 1: Device keypair (what DeviceKeyManager generates)
        val deviceKp = generateEcKeyPair()
        val devicePubSec1 = publicKeyToSec1(deviceKp.public)
        assertEquals(65, devicePubSec1.size)
        assertEquals(0x04.toByte(), devicePubSec1[0])

        // Step 2: Server-side ecies_encrypt simulation
        val ephemeralKp = generateEcKeyPair()
        val sharedSecret = performEcdh(ephemeralKp.private, deviceKp.public)
        val aesKey = Hkdf.deriveKey(
            salt = "freesky-ecies-v1".toByteArray(Charsets.UTF_8),
            ikm = sharedSecret,
            info = "freesky-group-key".toByteArray(Charsets.UTF_8),
            length = 32
        )

        // The "group key" is 32 random bytes (matches server's get_or_create_group_key)
        val groupKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val ciphertext = aesGcmEncrypt(aesKey, nonce, groupKey)

        // Wire format: [65 epk][12 nonce][ciphertext (plaintext + 16-byte tag)]
        val encryptedSkComm = publicKeyToSec1(ephemeralKp.public) + nonce + ciphertext
        assertEquals(65 + 12 + 32 + 16, encryptedSkComm.size)

        // Step 3: Android decryption
        val decryptedGroupKey = EciesDecryptor.decrypt(encryptedSkComm, deviceKp.private)

        // Verify we recovered the exact 32-byte group key
        assertEquals(32, decryptedGroupKey.size)
        assertArrayEquals(groupKey, decryptedGroupKey)
    }

    @Test(expected = IllegalArgumentException::class)
    fun decrypt_payloadBelowServerMin_throws() {
        // Payload of 80 bytes: 65 epk + 12 nonce + 3 ciphertext
        // Server requires >= 93 (65 + 12 + 16 GCM tag). Should throw.
        val deviceKp = generateEcKeyPair()
        val payload = ByteArray(80)
        EciesDecryptor.decrypt(payload, deviceKp.private)
    }

    @Test(expected = EciesDecryptionException::class)
    fun decrypt_withWrongKey_throws() {
        val recipientKp = generateEcKeyPair()
        val wrongKp = generateEcKeyPair()
        val ephemeralKp = generateEcKeyPair()
        val sharedSecret = performEcdh(ephemeralKp.private, recipientKp.public)
        val aesKey = deriveAesKey(sharedSecret)
        val plaintext = "secret".toByteArray()
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val ciphertext = aesGcmEncrypt(aesKey, nonce, plaintext)
        val payload = publicKeyToSec1(ephemeralKp.public) + nonce + ciphertext

        EciesDecryptor.decrypt(payload, wrongKp.private)
    }

    @Test(expected = IllegalArgumentException::class)
    fun decrypt_payloadTooShort_throws() {
        val recipientKp = generateEcKeyPair()
        val shortPayload = ByteArray(10)
        EciesDecryptor.decrypt(shortPayload, recipientKp.private)
    }

    @Test
    fun eciesPackage_serializeDeserialize_roundTrip() {
        val pubKey = ByteArray(65) { it.toByte() }
        val nonce = ByteArray(12) { (it + 1).toByte() }
        val ciphertext = ByteArray(48) { it.toByte() }

        val original = EciesPackage(pubKey, nonce, ciphertext)
        val serialized = original.serialize()
        val deserialized = EciesPackage.deserialize(serialized)

        assertArrayEquals(original.ephemeralPublicKey, deserialized.ephemeralPublicKey)
        assertArrayEquals(original.nonce, deserialized.nonce)
        assertArrayEquals(original.ciphertext, deserialized.ciphertext)
        assertEquals(original, deserialized)
    }

    @Test
    fun eciesPackage_deserialize_correctOffsets() {
        val pubKey = ByteArray(65) { 0x01 }
        val nonce = ByteArray(12) { 0x02 }
        val ciphertext = ByteArray(100) { 0x03 }

        val data = pubKey + nonce + ciphertext
        val pkg = EciesPackage.deserialize(data)

        assertEquals(65, pkg.ephemeralPublicKey.size)
        assertEquals(12, pkg.nonce.size)
        assertEquals(100, pkg.ciphertext.size)
        assertArrayEquals(pubKey, pkg.ephemeralPublicKey)
        assertArrayEquals(nonce, pkg.nonce)
        assertArrayEquals(ciphertext, pkg.ciphertext)
    }

    @Test(expected = IllegalArgumentException::class)
    fun eciesPackage_init_wrongPubKeySize() {
        EciesPackage(
            ephemeralPublicKey = ByteArray(64),
            nonce = ByteArray(12),
            ciphertext = ByteArray(0)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun eciesPackage_init_wrongNonceSize() {
        EciesPackage(
            ephemeralPublicKey = ByteArray(65),
            nonce = ByteArray(11),
            ciphertext = ByteArray(0)
        )
    }

    private fun generateEcKeyPair(): java.security.KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        return kpg.generateKeyPair()
    }

    private fun performEcdh(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(privateKey)
        ka.doPhase(publicKey, true)
        return ka.generateSecret()
    }

    private fun deriveAesKey(sharedSecret: ByteArray): ByteArray {
        return Hkdf.deriveKey(
            salt = "freesky-ecies-v1".toByteArray(Charsets.UTF_8),
            ikm = sharedSecret,
            info = "freesky-group-key".toByteArray(Charsets.UTF_8),
            length = 32
        )
    }

    private fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), spec)
        return cipher.doFinal(plaintext)
    }

    private fun publicKeyToSec1(publicKey: PublicKey): ByteArray {
        val ecPub = publicKey as ECPublicKey
        val point = ecPub.w
        val x = bigIntegerToFixedBytes(point.affineX, 32)
        val y = bigIntegerToFixedBytes(point.affineY, 32)
        return byteArrayOf(0x04) + x + y
    }

    private fun bigIntegerToFixedBytes(value: BigInteger, length: Int): ByteArray {
        val bytes = value.toByteArray()
        val stripped = if (bytes.size > 1 && bytes[0] == 0.toByte()) {
            bytes.copyOfRange(1, bytes.size)
        } else {
            bytes
        }
        return when {
            stripped.size == length -> stripped
            stripped.size < length -> {
                val padded = ByteArray(length)
                System.arraycopy(stripped, 0, padded, length - stripped.size, stripped.size)
                padded
            }
            else -> stripped.copyOfRange(stripped.size - length, stripped.size)
        }
    }
}