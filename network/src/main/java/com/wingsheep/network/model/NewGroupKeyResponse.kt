package com.wingsheep.network.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/** Response to `new_group_key`: the rotated key, ECIES-encrypted for this device. */
@Keep
data class NewGroupKeyData(
    @SerializedName("encrypted_sk_comm") val encrypted_sk_comm: String,
    @SerializedName("server_noise_pk") val server_noise_pk: List<Int>? = null
) {
    fun encryptedSkCommBytes(): ByteArray =
        encrypted_sk_comm.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    fun serverNoisePkBytes(): ByteArray? =
        server_noise_pk?.map { it.toByte() }?.toByteArray()
}
