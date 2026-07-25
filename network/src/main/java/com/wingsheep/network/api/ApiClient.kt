package com.wingsheep.network.api

import com.wingsheep.encrypt.model.EncryptedPost
import com.wingsheep.network.model.PostResponse
import com.wingsheep.network.model.RegisterResponse

interface ApiClient {

    data class NewGroupKeyResponse(
        val encryptedSkComm: String,
        val serverPublicKey: String
    )

    suspend fun fetchNewGroupKey(): NewGroupKeyResponse

    suspend fun register(pkDev: ByteArray, apkCertSha1: String): RegisterResponse

    suspend fun submitPost(post: EncryptedPost): PostResponse
}