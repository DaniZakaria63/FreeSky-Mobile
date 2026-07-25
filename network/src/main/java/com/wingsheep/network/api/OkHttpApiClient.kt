package com.wingsheep.network.api

import com.google.gson.Gson
import com.wingsheep.encrypt.model.EncryptedPost
import com.wingsheep.network.NetworkClient
import com.wingsheep.network.model.PostRequest
import com.wingsheep.network.model.PostResponse
import com.wingsheep.network.model.RegisterRequest
import com.wingsheep.network.model.RegisterResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException

class OkHttpApiClient(
    private val baseUrl: String,
    private val client: OkHttpClient = NetworkClient.defaultClient
) : ApiClient {

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val gson = Gson()
    }

    override suspend fun fetchNewGroupKey(): ApiClient.NewGroupKeyResponse {
        val request = Request.Builder()
            .url("$baseUrl/register")
            .get()
            .addHeader("Accept", "application/json")
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.body?.string()}")
                }
                val body = response.body?.string()
                    ?: throw IOException("Empty response body")
                gson.fromJson(body, ApiClient.NewGroupKeyResponse::class.java)
            }
        }
    }

    override suspend fun register(pkDev: ByteArray, apkCertSha1: String): RegisterResponse {
        val reqBody = RegisterRequest.fromBytes(pkDev, apkCertSha1)
        val jsonBody = gson.toJson(reqBody)

        Timber.d("POST $baseUrl/register")
        Timber.d("Request body: $jsonBody")

        val request = Request.Builder()
            .url("$baseUrl/register")
            .post(jsonBody.toRequestBody(JSON))
            .addHeader("Accept", "application/json")
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    Timber.w("Register failed: HTTP ${response.code} — $err")
                    throw IOException("HTTP ${response.code}: $err")
                }
                val body = response.body?.string()
                    ?: throw IOException("Empty response body")
                Timber.d("Response body: ${body.take(200)}...")
                val result = gson.fromJson(body, RegisterResponse::class.java)
                Timber.d("Parsed: name=\"${result.name}\" color=${result.color} enc=${result.encrypted_sk_comm.size}B")
                result
            }
        }
    }

    override suspend fun submitPost(post: EncryptedPost): PostResponse {
        val reqBody = PostRequest.fromEncryptedPost(post)
        val jsonBody = gson.toJson(reqBody)

        Timber.d("POST $baseUrl/post")
        Timber.d("Request body: $jsonBody")

        val request = Request.Builder()
            .url("$baseUrl/post")
            .post(jsonBody.toRequestBody(JSON))
            .addHeader("Accept", "application/json")
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    Timber.w("Post failed: HTTP ${response.code} — $body")
                    throw IOException("HTTP ${response.code}: $body")
                }
                Timber.d("Response body: $body")
                gson.fromJson(body, PostResponse::class.java)
            }
        }
    }
}