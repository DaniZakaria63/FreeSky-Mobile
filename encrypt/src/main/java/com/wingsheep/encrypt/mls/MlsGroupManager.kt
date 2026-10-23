package com.wingsheep.encrypt.mls

import android.content.Context
import com.wingsheep.encrypt.CryptoError
import com.wingsheep.encrypt.CryptoResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference
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
        /**
         * Single-file group state: [magic][8-byte epoch][32-byte key].
         *
         * The key and the epoch it belongs to must travel together — a key paired
         * with the wrong epoch yields posts nobody can decrypt — so they are
         * written as one unit and published with an atomic rename.
         */
        private const val STATE_FILE = "mls_group_state_v2.bin"
        private const val STATE_TMP_FILE = "mls_group_state_v2.bin.tmp"

        /** Pre-v2 layout: the key and the epoch in two separate files. */
        private const val LEGACY_GROUP_STATE_FILE = "mls_group_state.bin"
        private const val LEGACY_EPOCH_FILE = "mls_group_epoch.bin"

        private const val STATE_MAGIC = 0x46534B31 // "FSK1"
        private const val STATE_HEADER_BYTES = 4
        private const val STATE_EPOCH_BYTES = 8

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

    /**
     * The group key together with the epoch it belongs to.
     *
     * These must never be observed apart: a post encrypted with the previous key
     * but stamped with the next epoch cannot be decrypted by anybody.
     */
    private class GroupState(val key: ByteArray, val epoch: Long)

    /** Published as one immutable reference, so a reader never sees a half-updated pair. */
    private val stateRef = AtomicReference<GroupState?>(null)

    /** Serializes the read-modify-write cycles that change the group state. */
    private val stateLock = Any()

    fun isInitialized(): Boolean = stateRef.get() != null

    fun initFromKeyMaterial(
        groupStateBytes: ByteArray,
        identityKeyBytes: ByteArray
    ): CryptoResult<Unit> {
        if (groupStateBytes.size != AES_KEY_LENGTH_BYTES) {
            return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
        }
        return try {
            synchronized(stateLock) {
                // Persist first, publish second: a state that never reached disk
                // must not become the live one.
                val base = stateRef.get() ?: readState()
                val next = GroupState(groupStateBytes.copyOf(), (base?.epoch ?: 0L) + 1)
                persistState(next)
                stateRef.set(next)
            }
            CryptoResult.Success(Unit)
        } catch (e: Exception) {
            // Disk errors must not escape into ViewModel coroutines as
            // uncaught exceptions (that would crash the app process).
            CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
        }
    }

    fun restoreFromStorage(): CryptoResult<Unit> {
        return try {
            synchronized(stateLock) {
                val restored = readState()
                    ?: return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
                if (restored.key.size != AES_KEY_LENGTH_BYTES) {
                    return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
                }
                stateRef.set(restored)
            }
            CryptoResult.Success(Unit)
        } catch (e: Exception) {
            CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
        }
    }

    fun encryptPost(plaintext: ByteArray): CryptoResult<ByteArray> {
        return when (val result = encryptPostWithEpoch(plaintext)) {
            is CryptoResult.Success -> CryptoResult.Success(result.data.first)
            is CryptoResult.Error -> result
        }
    }

    /**
     * Encrypts [plaintext] and returns the ciphertext together with the epoch that
     * produced it, both taken from a single immutable snapshot.
     *
     * Callers must stamp the epoch returned here rather than querying
     * [getCurrentEpoch] separately: two calls can straddle a key rotation and
     * produce a post encrypted under one key but labelled with the next epoch.
     */
    fun encryptPostWithEpoch(plaintext: ByteArray): CryptoResult<Pair<ByteArray, Long>> {
        val state = stateRef.get()
            ?: return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
        return try {
            val nonce = ByteArray(GCM_NONCE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(state.key, AES_KEY_ALGORITHM),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
            )
            val ciphertext = cipher.doFinal(plaintext)
            CryptoResult.Success((nonce + ciphertext) to state.epoch)
        } catch (e: Exception) {
            CryptoResult.Error(CryptoError.MLS_DECRYPT_FAILED)
        }
    }

    fun decryptPost(ciphertext: ByteArray): CryptoResult<ByteArray> {
        val state = stateRef.get()
            ?: return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
        if (ciphertext.size < GCM_NONCE_LENGTH_BYTES + 16) {
            return CryptoResult.Error(CryptoError.MLS_DECRYPT_FAILED)
        }
        return try {
            val nonce = ciphertext.copyOfRange(0, GCM_NONCE_LENGTH_BYTES)
            val ct = ciphertext.copyOfRange(GCM_NONCE_LENGTH_BYTES, ciphertext.size)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(state.key, AES_KEY_ALGORITHM),
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
        val state = stateRef.get()
            ?: return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
        return CryptoResult.Success(state.epoch)
    }

    /**
     * Writes the whole state to a temporary file, fsyncs it, then publishes it with
     * an atomic rename. A crash can therefore never leave a key on disk paired with
     * the wrong epoch, which is what the previous two-file write allowed.
     */
    private fun persistState(state: GroupState) {
        val payload = ByteArray(STATE_HEADER_BYTES + STATE_EPOCH_BYTES + AES_KEY_LENGTH_BYTES)
        writeMagic(payload)
        writeLong(payload, STATE_HEADER_BYTES, state.epoch)
        System.arraycopy(state.key, 0, payload, STATE_HEADER_BYTES + STATE_EPOCH_BYTES, state.key.size)

        val target = File(context.filesDir, STATE_FILE)
        val tmp = File(context.filesDir, STATE_TMP_FILE)
        FileOutputStream(tmp).use { out ->
            out.write(payload)
            out.flush()
            out.fd.sync()
        }
        // rename(2) is atomic within one filesystem: a reader observes the old state
        // or the new state, never a mixture.
        if (!tmp.renameTo(target)) {
            throw IOException("Failed to publish MLS group state")
        }

        // The two-file layout is superseded; drop it so the retired key does not
        // linger on disk.
        runCatching { File(context.filesDir, LEGACY_GROUP_STATE_FILE).delete() }
        runCatching { File(context.filesDir, LEGACY_EPOCH_FILE).delete() }
    }

    /** Reads the current state, falling back to the pre-v2 two-file layout. */
    private fun readState(): GroupState? = readCombinedState() ?: readLegacyState()

    private fun readCombinedState(): GroupState? {
        val file = File(context.filesDir, STATE_FILE)
        if (!file.exists()) return null
        val bytes = file.readBytes()
        val expected = STATE_HEADER_BYTES + STATE_EPOCH_BYTES + AES_KEY_LENGTH_BYTES
        if (bytes.size != expected) return null
        if (readInt(bytes, 0) != STATE_MAGIC) return null
        val epoch = readLong(bytes, STATE_HEADER_BYTES)
        val key = bytes.copyOfRange(STATE_HEADER_BYTES + STATE_EPOCH_BYTES, bytes.size)
        return GroupState(key, epoch)
    }

    private fun readLegacyState(): GroupState? {
        val file = File(context.filesDir, LEGACY_GROUP_STATE_FILE)
        if (!file.exists()) return null
        val key = file.readBytes()
        if (key.size != AES_KEY_LENGTH_BYTES) return null
        return GroupState(key, readLegacyEpoch())
    }

    private fun readLegacyEpoch(): Long {
        val file = File(context.filesDir, LEGACY_EPOCH_FILE)
        if (!file.exists()) return 0L
        val bytes = file.readBytes()
        if (bytes.size < STATE_EPOCH_BYTES) return 0L
        return readLong(bytes, 0)
    }

    private fun writeMagic(buf: ByteArray) {
        buf[0] = ((STATE_MAGIC shr 24) and 0xFF).toByte()
        buf[1] = ((STATE_MAGIC shr 16) and 0xFF).toByte()
        buf[2] = ((STATE_MAGIC shr 8) and 0xFF).toByte()
        buf[3] = (STATE_MAGIC and 0xFF).toByte()
    }

    private fun readInt(buf: ByteArray, offset: Int): Int {
        var v = 0
        for (i in 0 until STATE_HEADER_BYTES) {
            v = (v shl 8) or (buf[offset + i].toInt() and 0xFF)
        }
        return v
    }

    private fun writeLong(buf: ByteArray, offset: Int, value: Long) {
        for (i in 0 until STATE_EPOCH_BYTES) {
            buf[offset + i] = ((value shr ((7 - i) * 8)) and 0xFF).toByte()
        }
    }

    private fun readLong(buf: ByteArray, offset: Int): Long {
        var v = 0L
        for (i in 0 until STATE_EPOCH_BYTES) {
            v = (v shl 8) or (buf[offset + i].toLong() and 0xFF)
        }
        return v
    }
}
