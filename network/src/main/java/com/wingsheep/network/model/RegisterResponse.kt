package com.wingsheep.network.model

data class RegisterResponse(
    val name: String,
    val color: Int,
    val encrypted_sk_comm: List<Int>,
    val server_noise_pk: List<Int>? = null
) {
    fun encryptedSkCommBytes(): ByteArray =
        encrypted_sk_comm.map { it.toByte() }.toByteArray()

    fun serverNoisePkBytes(): ByteArray? =
        server_noise_pk?.map { it.toByte() }?.toByteArray()
}