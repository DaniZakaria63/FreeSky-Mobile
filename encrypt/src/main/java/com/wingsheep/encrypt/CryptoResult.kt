package com.wingsheep.encrypt

sealed class CryptoResult<out T> {

    data class Success<T>(val data: T) : CryptoResult<T>()

    data class Error(val reason: CryptoError) : CryptoResult<Nothing>()

    val isSuccess: Boolean get() = this is Success

    val isError: Boolean get() = this is Error

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw IllegalStateException("Crypto operation failed: $reason")
    }

    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Error -> default
    }
}

enum class CryptoError {

    KEY_NOT_FOUND,
    KEYSTORE_UNAVAILABLE,
    KEY_GENERATION_FAILED,
    DECRYPTION_FAILED,
    INVALID_EPHEMERAL_KEY,
    WRONG_NONCE_LENGTH,
    HKDF_FAILED,
    SIGNATURE_INVALID,
    SIGNING_FAILED,
    MLS_GROUP_NOT_INITIALIZED,
    MLS_DECRYPT_FAILED,
    MLS_EPOCH_MISMATCH,
    POST_DECRYPTION_FAILED,
    POST_SIGNATURE_INVALID,
    KEY_ROTATION_FAILED,
}