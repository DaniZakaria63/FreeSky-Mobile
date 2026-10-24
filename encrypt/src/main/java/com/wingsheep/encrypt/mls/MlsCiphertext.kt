package com.wingsheep.encrypt.mls

/**
 * A post ciphertext together with the group epoch that produced it.
 *
 * Both values come from a single snapshot of the group state. Reading the epoch
 * separately can straddle a key rotation and label a post with an epoch that did
 * not encrypt it.
 */
class MlsCiphertext(
    val ciphertext: ByteArray,
    val epoch: Long
) {
    // ByteArray has identity equality; compare the contents instead.
    override fun equals(other: Any?): Boolean =
        other is MlsCiphertext && epoch == other.epoch && ciphertext.contentEquals(other.ciphertext)

    override fun hashCode(): Int = 31 * ciphertext.contentHashCode() + epoch.hashCode()
}
