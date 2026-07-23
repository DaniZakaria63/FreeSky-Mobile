package com.wingsheep.encrypt.crypto

/**
 * Wire format for ECIES-encrypted payloads.
 *
 * ```
 * [ephemeralPublicKey (65 bytes)] [nonce (12 bytes)] [ciphertext (variable)]
 * ```
 *
 * - [ephemeralPublicKey]: 65-byte SEC1 uncompressed P-256 point (`0x04 || X || Y`)
 * - [nonce]: 12-byte AES-GCM nonce
 * - [ciphertext]: AES-GCM ciphertext (includes the 16-byte authentication tag)
 *
 * Reference: `docs/android-encryption-guide.md` §3.4
 */
data class EciesPackage(
    val ephemeralPublicKey: ByteArray,  // 65 bytes, SEC1 uncompressed
    val nonce: ByteArray,               // 12 bytes
    val ciphertext: ByteArray           // variable, includes 16-byte GCM tag
) {
    init {
        require(ephemeralPublicKey.size == 65) {
            "ephemeralPublicKey must be 65 bytes (SEC1 uncompressed), got ${ephemeralPublicKey.size}"
        }
        require(nonce.size == 12) {
            "nonce must be 12 bytes, got ${nonce.size}"
        }
    }

    /** Serialises to the wire format: `ephemeralPublicKey || nonce || ciphertext`. */
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

        /** Deserialises from the wire format. */
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
