package com.wingsheep.network.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/** Response to `POST /register`. Populated by Gson, hence [Keep]. */
@Keep
data class RegisterResponse(
    @SerializedName("name") val name: String,
    @SerializedName("color") val color: Int,
    @SerializedName("encrypted_sk_comm") val encrypted_sk_comm: List<Int>,
    @SerializedName("server_noise_pk") val server_noise_pk: List<Int>? = null
) {
    fun encryptedSkCommBytes(): ByteArray =
        encrypted_sk_comm.map { it.toByte() }.toByteArray()

    fun serverNoisePkBytes(): ByteArray? =
        server_noise_pk?.map { it.toByte() }?.toByteArray()
}
