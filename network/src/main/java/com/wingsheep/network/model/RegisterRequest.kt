package com.wingsheep.network.model

data class RegisterRequest(
    val pk_dev: List<Int>,
    val apk_cert_sha1: String
) {
    companion object {
        fun fromBytes(bytes: ByteArray, apkCertSha1: String): RegisterRequest =
            RegisterRequest(
                pk_dev = bytes.map { it.toInt() and 0xFF },
                apk_cert_sha1 = apkCertSha1
            )
    }
}