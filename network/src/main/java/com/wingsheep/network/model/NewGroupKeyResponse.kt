package com.wingsheep.network.model

data class NewGroupKeyData(
    val encrypted_sk_comm: String,
    val server_noise_pk: List<Int>? = null
) {
    fun encryptedSkCommBytes(): ByteArray =
        encrypted_sk_comm.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    fun serverNoisePkBytes(): ByteArray? =
        server_noise_pk?.map { it.toByte() }?.toByteArray()
}
