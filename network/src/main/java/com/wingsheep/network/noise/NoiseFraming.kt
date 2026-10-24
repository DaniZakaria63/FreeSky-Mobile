package com.wingsheep.network.noise

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Wire framing wrapped around every Noise transport message.
 *
 * `[2-byte big-endian ciphertext length][ciphertext]`, per PROTOCOL_SYNC.md §3.2.
 * The length prefix is two bytes, so a frame is at most [MAX_FRAME_BYTES] long.
 */
internal object NoiseFraming {

    const val MAX_FRAME_BYTES = 65_535

    private const val LENGTH_BYTES = 2

    fun readFrame(input: InputStream): ByteArray {
        val header = input.readExactly(LENGTH_BYTES)
        val length = ((header[0].toInt() and 0xFF) shl 8) or (header[1].toInt() and 0xFF)
        if (length !in 1..MAX_FRAME_BYTES) {
            throw IOException("Invalid Noise frame length: $length")
        }
        return input.readExactly(length)
    }

    fun writeFrame(output: OutputStream, ciphertext: ByteArray) {
        require(ciphertext.size <= MAX_FRAME_BYTES) { "Noise frame too large: ${ciphertext.size}" }
        output.write(byteArrayOf((ciphertext.size shr 8).toByte(), ciphertext.size.toByte()))
        output.write(ciphertext)
        output.flush()
    }
}

/**
 * Reads exactly [size] bytes.
 *
 * A single [InputStream.read] may return fewer bytes than asked for — TCP does not
 * preserve write boundaries — and a short read means the peer is gone.
 */
internal fun InputStream.readExactly(size: Int): ByteArray {
    val buffer = ByteArray(size)
    var offset = 0
    while (offset < size) {
        val read = read(buffer, offset, size - offset)
        if (read == -1) throw IOException("Noise stream closed after $offset of $size bytes")
        offset += read
    }
    return buffer
}

internal fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it) }
