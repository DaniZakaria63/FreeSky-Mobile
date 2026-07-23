package com.wingsheep.encrypt.model

data class EncryptedPost(
    val ciphertextComm: ByteArray,
    val authorPk: ByteArray,
    val authorSig: ByteArray,
    val timestamp: Long,
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