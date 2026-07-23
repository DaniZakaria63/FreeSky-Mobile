package com.wingsheep.network.model

data class RegisterRequest(
    val pk_dev: List<Int>
) {
    companion object {
        fun fromBytes(bytes: ByteArray): RegisterRequest =
            RegisterRequest(bytes.map { it.toInt() and 0xFF })
    }
}