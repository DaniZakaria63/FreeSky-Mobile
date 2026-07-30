package com.wingsheep.network.api

import com.wingsheep.network.model.RegisterResponse

interface ApiClient {
    suspend fun register(pkDev: ByteArray, apkCertSha1: String): RegisterResponse
    suspend fun fetchServerNoisePk(): ByteArray?
}
