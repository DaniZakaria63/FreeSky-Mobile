package com.wingsheep.network.api

import com.google.gson.Gson
import com.wingsheep.network.NetworkClient
import com.wingsheep.network.model.RegisterRequest
import com.wingsheep.network.model.RegisterResponse
import com.wingsheep.network.model.apiResponseFrom
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
                val wrapped = gson.apiResponseFrom<RegisterResponse>(body)
                val data = wrapped.data
                    ?: throw IOException("Register failed: ${wrapped.message}")
                Timber.d("Parsed: name=\"${data.name}\" color=${data.color} enc=${data.encrypted_sk_comm.size}B noisePk=${data.serverNoisePkBytes()?.size}B")
                data
            }
        }
    }
}
