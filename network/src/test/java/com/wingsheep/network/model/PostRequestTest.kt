package com.wingsheep.network.model

import com.wingsheep.encrypt.model.EncryptedPost
import org.junit.Assert.assertEquals
import org.junit.Test

class PostRequestTest {

    @Test
    fun fromEncryptedPost_convertsAllFieldsCorrectly() {
        val ciphertext = byteArrayOf(0x01, 0x02, 0xFF.toByte())
        val authorPk = ByteArray(65) { 0x04 }
        val authorSig = byteArrayOf(0x30, 0x45, 0x02, 0x21)
        val timestampMs = 1703123456789L
        val mlsEpoch = 7L

        val encryptedPost = EncryptedPost(
            ciphertextComm = ciphertext,
            authorPk = authorPk,
            authorSig = authorSig,
            timestamp = timestampMs,
            mlsEpoch = mlsEpoch
        )

        val request = PostRequest.fromEncryptedPost(encryptedPost)

        // Byte arrays converted to unsigned int lists
        assertEquals(listOf(1, 2, 255), request.ciphertext_comm)
        assertEquals(65, request.author_pk.size)
        assertEquals(4, request.author_pk[0])
        assertEquals(listOf(48, 69, 2, 33), request.author_sig)

        // Timestamp converted from ms to seconds
        assertEquals(1703123456L, request.timestamp)
        assertEquals(mlsEpoch, request.mls_epoch)
    }

    @Test
    fun fromEncryptedPost_timestampTruncatedToSeconds() {
        val post = EncryptedPost(
            ciphertextComm = byteArrayOf(),
            authorPk = ByteArray(65) { 0x04 },
            authorSig = byteArrayOf(),
            timestamp = 1703123456999L,  // 1 second before next second
            mlsEpoch = 0L
        )

        val request = PostRequest.fromEncryptedPost(post)

        assertEquals(1703123456L, request.timestamp)
    }

    @Test
    fun postResponse_parsesFromJson() {
        // Verify PostResponse can be deserialized by Gson
        val gson = com.google.gson.Gson()
        val json = """{"message":"success","data":null}"""
        val response = gson.fromJson(json, PostResponse::class.java)

        assertEquals("success", response.message)
        assertEquals(null, response.data)
    }

    @Test
    fun postResponse_parsesErrorFromJson() {
        val gson = com.google.gson.Gson()
        val json = """{"message":"invalid signature","data":null}"""
        val response = gson.fromJson(json, PostResponse::class.java)

        assertEquals("invalid signature", response.message)
        assertEquals(null, response.data)
    }
}
