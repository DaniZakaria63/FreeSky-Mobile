package com.wingsheep.encrypt.mls

import android.content.Context
import com.wingsheep.encrypt.CryptoError
import com.wingsheep.encrypt.CryptoResult
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Group-key-based content encryption.
 *
 * The server currently issues a 32-byte random symmetric key (via ECIES at
 * registration / key-rotation) rather than a real MLS group state. This class
 * implements AES-256-GCM content encryption with that key as a stand-in for
 * MLS until the openmls path is wired on both sides.
 *
 * Wire format (ciphertext_comm): [12-byte nonce][ciphertext + 16-byte GCM tag]
 *
 * Epoch semantics: the server bumps the group key on admin key-rotate; we
 * mirror that by incrementing our local epoch whenever a new key material is
 * loaded via [initFromKeyMaterial].
 *
 * Reference: PROTOCOL_SYNC.md §3.2 — Post Endpoints, §3.2 — Group Key
 */
class MlsGroupManager(private val context: Context) {

    companion object {
        private const val GROUP_STATE_FILE = "mls_group_state.bin"
        private const val EPOCH_FILE = "mls_group_epoch.bin"

        // AES-256-GCM parameters
        private const val AES_KEY_ALGORITHM = "AES"
        private const val AES_GCM_TRANSFORM = "AES/GCM/NoPadding"
        private const val GCM_NONCE_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128

        // HKDF domain separation (mirrors ECIES path for consistency)
        private val KDF_SALT = "freesky-ecies-v1".toByteArray(Charsets.UTF_8)
        private val KDF_INFO = "freesky-group-key".toByteArray(Charsets.UTF_8)
        private const val AES_KEY_LENGTH_BYTES = 32
    }

    private var initialized: Boolean = false
    private var groupKey: ByteArray = ByteArray(0)
    private var epoch: Long = 0L

    fun isInitialized(): Boolean = initialized

    fun initFromKeyMaterial(
        groupStateBytes: ByteArray,
        identityKeyBytes: ByteArray
    ): CryptoResult<Unit> {
        if (groupStateBytes.size != AES_KEY_LENGTH_BYTES) {
            return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
        }
        groupKey = groupStateBytes.copyOf()
        epoch = readEpoch() + 1
        persistState()
        initialized = true
        return CryptoResult.Success(Unit)
    }

    fun restoreFromStorage(): CryptoResult<Unit> {
        val keyBytes = readGroupState() ?: return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
        if (keyBytes.size != AES_KEY_LENGTH_BYTES) {
            return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
        }
        groupKey = keyBytes
        epoch = readEpoch()
        initialized = true
        return CryptoResult.Success(Unit)
    }

    fun encryptPost(plaintext: ByteArray): CryptoResult<ByteArray> {
        if (!initialized) return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
        return try {
            val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(groupKey, AES_KEY_ALGORITHM),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
            )
            val ciphertext = cipher.doFinal(plaintext)
            CryptoResult.Success(nonce + ciphertext)
        } catch (e: Exception) {
            CryptoResult.Error(CryptoError.MLS_DECRYPT_FAILED)
        }
    }

    fun decryptPost(ciphertext: ByteArray): CryptoResult<ByteArray> {
        if (!initialized) return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
        if (ciphertext.size < GCM_NONCE_LENGTH_BYTES + 16) {
            return CryptoResult.Error(CryptoError.MLS_DECRYPT_FAILED)
        }
        return try {
            val nonce = ciphertext.copyOfRange(0, GCM_NONCE_LENGTH_BYTES)
            val ct = ciphertext.copyOfRange(GCM_NONCE_LENGTH_BYTES, ciphertext.size)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(groupKey, AES_KEY_ALGORITHM),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
            )
            CryptoResult.Success(cipher.doFinal(ct))
        } catch (e: Exception) {
            CryptoResult.Error(CryptoError.MLS_DECRYPT_FAILED)
        }
    }

    fun processCommitMessage(commitBytes: ByteArray): CryptoResult<Unit> {
        return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
    }

    fun processWelcomeMessage(welcomeBytes: ByteArray): CryptoResult<Unit> {
        return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
    }

    fun getCurrentEpoch(): CryptoResult<Long> {
        if (!initialized) return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
        return CryptoResult.Success(epoch)
    }

    private fun persistState() {
        File(context.filesDir, GROUP_STATE_FILE).writeBytes(groupKey)
        File(context.filesDir, EPOCH_FILE).writeBytes(epoch.toByteArray())
    }

    private fun readGroupState(): ByteArray? {
        val file = File(context.filesDir, GROUP_STATE_FILE)
        return if (file.exists()) file.readBytes() else null
    }

    private fun readEpoch(): Long {
        val file = File(context.filesDir, EPOCH_FILE)
        if (!file.exists()) return 0L
        val bytes = file.readBytes()
        if (bytes.size < 8) return 0L
        var v = 0L
        for (i in 0 until 8) {
            v = (v shl 8) or (bytes[i].toLong() and 0xFF)
        }
        return v
    }

    private fun Long.toByteArray(): ByteArray = ByteArray(8) { i ->
        ((this shr ((7 - i) * 8)) and 0xFF).toByte()
    }
}
