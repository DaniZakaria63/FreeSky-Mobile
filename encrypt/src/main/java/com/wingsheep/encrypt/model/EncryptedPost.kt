package com.wingsheep.encrypt.model

/**
 * Encrypted post as stored on the server.
 *
 * Every post is encrypted once with the MLS group key (confidentiality)
 * and signed with the device's ECDSA key (authenticity).
 *
 * Reference: `docs/android-encryption-guide.md` §5
 */
data class EncryptedPost(
    /** MLS-encrypted content (decryptable by all group members). */
    val ciphertextComm: ByteArray,

    /** Serialised device public key (X.509) of the author. */
    val authorPk: ByteArray,

    /** ECDSA signature over SHA-256(ciphertextComm). */
    val authorSig: ByteArray,

    /** Epoch millis at time of posting. */
    val timestamp: Long,

    /** MLS epoch at time of posting. */
    val mlsEpoch: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedPost) return false
        return timestamp == other.timestamp &&
            mlsEpoch == other.mlsEpoch &&
            ciphertextComm.contentEquals(other.ciphertextComm) &&
            authorPk.contentEquals(other.authorPk) &&
            authorSig.contentEquals(other.authorSig)
    }

    override fun hashCode(): Int {
        var result = ciphertextComm.contentHashCode()
        result = 31 * result + authorPk.contentHashCode()
        result = 31 * result + authorSig.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + mlsEpoch.hashCode()
        return result
    }
}
