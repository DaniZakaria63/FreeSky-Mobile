package com.wingsheep.encrypt.mls

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The persisted layout is a wire format for the app's own storage: a regression here
 * would make every previously issued group key unreadable.
 */
class MlsGroupStateTest {

    @Test
    fun `encode and decode round-trip the key and epoch`() {
        val key = ByteArray(MlsGroupState.KEY_LENGTH_BYTES) { it.toByte() }
        val state = MlsGroupState(key = key, epoch = 42L)

        val restored = MlsGroupState.decode(state.encode())

        assertNotNull(restored)
        assertArrayEquals(key, restored!!.key)
        assertEquals(42L, restored.epoch)
        assertEquals(44, state.encode().size)
    }

    @Test
    fun `decode rejects a payload with the wrong length`() {
        val encoded = MlsGroupState(ByteArray(MlsGroupState.KEY_LENGTH_BYTES), 1L).encode()

        assertNull(MlsGroupState.decode(encoded.copyOf(encoded.size - 1)))
        assertNull(MlsGroupState.decode(encoded + byteArrayOf(0)))
        assertNull(MlsGroupState.decode(ByteArray(0)))
    }

    @Test
    fun `decode rejects a payload without the group-state magic`() {
        val encoded = MlsGroupState(ByteArray(MlsGroupState.KEY_LENGTH_BYTES), 7L).encode()
        encoded[0] = 0x00

        assertNull(MlsGroupState.decode(encoded))
    }

    @Test
    fun `encode writes the epoch big-endian`() {
        val encoded = MlsGroupState(ByteArray(MlsGroupState.KEY_LENGTH_BYTES), 0x0102L).encode()

        // [magic:4][epoch:8][key:32]
        assertEquals(0x00, encoded[4].toInt())
        assertEquals(0x00, encoded[5].toInt())
        assertEquals(0x00, encoded[6].toInt())
        assertEquals(0x00, encoded[7].toInt())
        assertEquals(0x00, encoded[8].toInt())
        assertEquals(0x00, encoded[9].toInt())
        assertEquals(0x01, encoded[10].toInt())
        assertEquals(0x02, encoded[11].toInt())
    }
}
