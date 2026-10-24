package com.wingsheep.encrypt.mls

import android.content.Context
import com.wingsheep.encrypt.CryptoError
import com.wingsheep.encrypt.CryptoResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

/**
 * Group-key content encryption, standing in for MLS until the openmls path is
 * wired on both sides.
 *
 * The server issues a 32-byte symmetric key (ECIES at registration and on admin
 * key rotation) rather than a real MLS group state; this class performs
 * AES-256-GCM with that key and tracks the epoch it belongs to.
 *
 * Ciphertext layout: `[12-byte nonce][ciphertext + 16-byte GCM tag]`.
 *
 * The state is held as one immutable snapshot and every encryption reads it once,
 * so ciphertext and epoch can never come from two different rotations.
 *
 * Reference: PROTOCOL_SYNC.md §3.2 — Post Endpoints, §3.2 — Group Key
 */
class MlsGroupManager internal constructor(
    private val store: MlsGroupStateStore
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(MlsGroupStateStore(context.filesDir))

    private val random = SecureRandom()

    /** Published as one immutable reference: a reader never sees a half-updated pair. */
    private val state = AtomicReference<MlsGroupState?>(null)

    /** Serialises the read-modify-write cycles that replace the state. */
    private val stateLock = Any()

    fun isInitialized(): Boolean = state.get() != null

    /**
     * Installs a freshly received group key and bumps the epoch.
     *
     * The new epoch is derived from the live state, falling back to the persisted
     * one, so a rotation that arrives before the first restore still advances.
     */
    fun initFromKeyMaterial(groupKey: ByteArray): CryptoResult<Unit> {
        if (groupKey.size != MlsGroupState.KEY_LENGTH_BYTES) {
            return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
        }
        return try {
            synchronized(stateLock) {
                val previousEpoch = state.get()?.epoch ?: store.read()?.epoch ?: 0L
                val next = MlsGroupState(key = groupKey.copyOf(), epoch = previousEpoch + 1)
                // Persist first, publish second: a state that never reached disk
                // must not become the live one.
                store.write(next)
                state.set(next)
            }
            CryptoResult.Success(Unit)
        } catch (e: Exception) {
            // A disk failure must not escape into a ViewModel coroutine as an
            // uncaught exception.
            CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
        }
    }

    /** Restores the persisted state, if there is one. */
    fun restoreFromStorage(): CryptoResult<Unit> = try {
        synchronized(stateLock) {
            val restored = store.read()
                ?: return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
            state.set(restored)
        }
        CryptoResult.Success(Unit)
    } catch (e: Exception) {
        CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
    }

    fun encryptPost(plaintext: ByteArray): CryptoResult<ByteArray> =
        when (val result = encryptPostWithEpoch(plaintext)) {
            is CryptoResult.Error -> result
            is CryptoResult.Success -> CryptoResult.Success(result.data.ciphertext)
        }

    /**
     * Encrypts [plaintext] and returns the ciphertext with the epoch that produced
     * it, both taken from one immutable snapshot.
     *
     * Callers must stamp the returned epoch rather than query [getCurrentEpoch]
     * separately: two calls can straddle a key rotation.
     */
    fun encryptPostWithEpoch(plaintext: ByteArray): CryptoResult<MlsCiphertext> {
        val snapshot = state.get() ?: return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
        return try {
            val nonce = ByteArray(GCM_NONCE_BYTES).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance(GCM_TRANSFORM).apply {
                init(Cipher.ENCRYPT_MODE, snapshot.secretKey(), GCMParameterSpec(GCM_TAG_BITS, nonce))
            }
            CryptoResult.Success(MlsCiphertext(nonce + cipher.doFinal(plaintext), snapshot.epoch))
        } catch (e: Exception) {
            CryptoResult.Error(CryptoError.MLS_DECRYPT_FAILED)
        }
    }

    fun decryptPost(ciphertext: ByteArray): CryptoResult<ByteArray> {
        val snapshot = state.get() ?: return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
        if (ciphertext.size < GCM_NONCE_BYTES + GCM_TAG_BYTES) {
            return CryptoResult.Error(CryptoError.MLS_DECRYPT_FAILED)
        }
        return try {
            val nonce = ciphertext.copyOfRange(0, GCM_NONCE_BYTES)
            val cipher = Cipher.getInstance(GCM_TRANSFORM).apply {
                init(Cipher.DECRYPT_MODE, snapshot.secretKey(), GCMParameterSpec(GCM_TAG_BITS, nonce))
            }
            val payloadSize = ciphertext.size - GCM_NONCE_BYTES
            CryptoResult.Success(cipher.doFinal(ciphertext, GCM_NONCE_BYTES, payloadSize))
        } catch (e: Exception) {
            CryptoResult.Error(CryptoError.MLS_DECRYPT_FAILED)
        }
    }

    fun getCurrentEpoch(): CryptoResult<Long> {
        val snapshot = state.get() ?: return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
        return CryptoResult.Success(snapshot.epoch)
    }

    /**
     * MLS commits are not implemented yet; the server only rotates the group key.
     * Kept so the future openmls migration has a single call site to fill in.
     */
    fun processCommitMessage(commitBytes: ByteArray): CryptoResult<Unit> =
        CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)

    /** Welcome messages land here once real MLS is wired. */
    fun processWelcomeMessage(welcomeBytes: ByteArray): CryptoResult<Unit> =
        CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)

    private companion object {
        const val AES_KEY_ALGORITHM = "AES"
        const val GCM_TRANSFORM = "AES/GCM/NoPadding"
        const val GCM_NONCE_BYTES = 12
        const val GCM_TAG_BYTES = 16
        const val GCM_TAG_BITS = GCM_TAG_BYTES * 8
    }
}

private fun MlsGroupState.secretKey(): SecretKeySpec = SecretKeySpec(key, "AES")
