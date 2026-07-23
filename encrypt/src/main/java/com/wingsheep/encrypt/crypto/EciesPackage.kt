package com.wingsheep.encrypt.crypto

data class EciesPackage(
    val ephemeralPublicKey: ByteArray,
    val nonce: ByteArray,
    val ciphertext: ByteArray
) {
    init {
        require(ephemeralPublicKey.size == 65) {
            "ephemeralPublicKey must be 65 bytes (SEC1 uncompressed), got ${ephemeralPublicKey.size}"
        }
        require(nonce.size == 12) {
            "nonce must be 12 bytes, got ${nonce.size}"
        }
    }

    fun serialize(): ByteArray = ephemeralPublicKey + nonce + ciphertext

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EciesPackage) return false
        return ephemeralPublicKey.contentEquals(other.ephemeralPublicKey) &&
            nonce.contentEquals(other.nonce) &&
            ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int {
        var result = ephemeralPublicKey.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }

    companion object {
        private const val PUBKEY_LEN = 65
        private const val NONCE_LEN = 12

        fun deserialize(data: ByteArray): EciesPackage {
            require(data.size >= PUBKEY_LEN + NONCE_LEN) {
                "Payload too short: ${data.size} bytes (need at least ${PUBKEY_LEN + NONCE_LEN})"
            }
            return EciesPackage(
                ephemeralPublicKey = data.copyOfRange(0, PUBKEY_LEN),
                nonce = data.copyOfRange(PUBKEY_LEN, PUBKEY_LEN + NONCE_LEN),
                ciphertext = data.copyOfRange(PUBKEY_LEN + NONCE_LEN, data.size)
            )
        }
    }
}