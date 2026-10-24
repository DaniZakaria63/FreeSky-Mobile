package com.wingsheep.encrypt.mls

import java.nio.ByteBuffer

/**
 * The group key together with the epoch that produced it.
 *
 * The two values are meaningless apart: a post encrypted with the previous key but
 * stamped with the next epoch can never be decrypted again. They are therefore
 * modelled, persisted and published as one immutable unit.
 *
 * Deliberately not a `data class` — `toString()` would print key material into logs.
 */
internal class MlsGroupState(
    val key: ByteArray,
    val epoch: Long
) {

    init {
        require(key.size == KEY_LENGTH_BYTES) {
            "Group key must be $KEY_LENGTH_BYTES bytes, was ${key.size}"
        }
    }

    /** Serialises as `[magic][epoch][key]`, big-endian. */
    fun encode(): ByteArray = ByteBuffer.allocate(ENCODED_LENGTH)
        .putInt(MAGIC)
        .putLong(epoch)
        .put(key)
        .array()

    companion object {
        const val KEY_LENGTH_BYTES = 32

        private const val MAGIC = 0x46534B31 // "FSK1"
        private const val MAGIC_BYTES = Int.SIZE_BYTES
        private const val EPOCH_BYTES = Long.SIZE_BYTES
        private const val ENCODED_LENGTH = MAGIC_BYTES + EPOCH_BYTES + KEY_LENGTH_BYTES

        /** Returns null when [bytes] was not produced by [encode]. */
        fun decode(bytes: ByteArray): MlsGroupState? {
            if (bytes.size != ENCODED_LENGTH) return null
            val buffer = ByteBuffer.wrap(bytes)
            if (buffer.int != MAGIC) return null
            val epoch = buffer.long
            val key = ByteArray(KEY_LENGTH_BYTES).also { buffer.get(it) }
            return MlsGroupState(key, epoch)
        }
    }
}
