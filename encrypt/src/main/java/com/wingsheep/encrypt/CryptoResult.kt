package com.wingsheep.encrypt

/**
 * Result wrapper for cryptographic operations.
 *
 * Every crypto operation returns [CryptoResult] instead of throwing exceptions,
 * so callers must explicitly handle success and error cases.
 *
 * @see CryptoError for the list of possible failure reasons.
 */
sealed class CryptoResult<out T> {

    /** Operation succeeded — [data] holds the result. */
    data class Success<T>(val data: T) : CryptoResult<T>()

    /** Operation failed — [reason] explains why. */
    data class Error(val reason: CryptoError) : CryptoResult<Nothing>()

    // ── Convenience helpers ─────────────────────────────────────────

    /** Returns `true` when this is a [Success]. */
    val isSuccess: Boolean get() = this is Success

    /** Returns `true` when this is an [Error]. */
    val isError: Boolean get() = this is Error

    /** Unwraps a [Success], throwing if this is an [Error]. */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw IllegalStateException("Crypto operation failed: $reason")
    }

    /** Returns [default] if this is an [Error], otherwise the success data. */
    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Error -> default
    }
}

/**
 * Enumerates every way a cryptographic operation can fail.
 *
 * These map directly to the error-handling strategy in
 * `docs/android-encryption-guide.md` §8.
 */
enum class CryptoError {

    // ── Device identity key ──
    /** No device keypair exists in AndroidKeyStore. */
    KEY_NOT_FOUND,

    /** AndroidKeyStore is unavailable (e.g. device not initialised). */
    KEYSTORE_UNAVAILABLE,

    /** Key generation failed (TEE / StrongBox error). */
    KEY_GENERATION_FAILED,

    // ── ECIES ──
    /** AES-GCM tag verification failed — ciphertext was tampered or wrong key. */
    DECRYPTION_FAILED,

    /** Ephemeral public key in the ECIES payload is malformed. */
    INVALID_EPHEMERAL_KEY,

    /** Nonce length is not 12 bytes. */
    WRONG_NONCE_LENGTH,

    /** HKDF derivation produced an invalid key. */
    HKDF_FAILED,

    // ── Signing / verification ──
    /** ECDSA signature verification failed. */
    SIGNATURE_INVALID,

    /** Signing operation failed. */
    SIGNING_FAILED,

    // ── MLS ──
    /** MLS group was never initialised. */
    MLS_GROUP_NOT_INITIALIZED,

    /** MLS decryption failed (wrong epoch, tampered ciphertext, etc.). */
    MLS_DECRYPT_FAILED,

    /** MLS epoch mismatch — group state is stale. */
    MLS_EPOCH_MISMATCH,

    // ── Post / feed ──
    /** Post ciphertext could not be decrypted. */
    POST_DECRYPTION_FAILED,

    /** Post signature verification failed — possible forgery. */
    POST_SIGNATURE_INVALID,

    // ── Key rotation ──
    /** Key rotation failed — server response was malformed. */
    KEY_ROTATION_FAILED,
}
