package com.wingsheep.network.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * Body of `POST /register`.
 *
 * Serialised *to* JSON by Gson, so it needs [Keep] just like the response models.
 */
@Keep
data class RegisterRequest(
    @SerializedName("pk_dev") val pk_dev: List<Int>,
    @SerializedName("apk_cert_sha1") val apk_cert_sha1: String
) {
    companion object {
        fun fromBytes(bytes: ByteArray, apkCertSha1: String): RegisterRequest =
            RegisterRequest(
                pk_dev = bytes.map { it.toInt() and 0xFF },
                apk_cert_sha1 = apkCertSha1
            )
    }
}
