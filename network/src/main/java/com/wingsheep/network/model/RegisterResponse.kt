package com.wingsheep.network.model

data class RegisterResponse(
    val name: String,
    val color: Int,
    val encrypted_sk_comm: List<Int>
) {
    fun encryptedSkCommBytes(): ByteArray =
        encrypted_sk_comm.map { it.toByte() }.toByteArray()
}