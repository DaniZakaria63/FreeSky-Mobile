package com.wingsheep.network.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wingsheep.network.NetworkClient
import com.wingsheep.network.model.ApiResponse
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

/**
 * OkHttp-backed [ApiClient].
 *
 * The base URL and the client are resolved per call through providers: remote
 * config arrives asynchronously and a Tor circuit comes and goes, while this object
 * outlives both.
 */
class OkHttpApiClient(
    private val baseUrlProvider: () -> String,
    private val clientProvider: () -> OkHttpClient = { NetworkClient.defaultClient }
) : ApiClient {

    /** Returns the server's Noise static key, or null when the server is unreachable. */
    override suspend fun fetchServerNoisePk(): ByteArray? {
        val request = Request.Builder()
            .url(url("/server-pk"))
            .get()
            .header(HEADER_ACCEPT, JSON_CONTENT_TYPE)
            .build()

        val body = try {
            execute(request)
        } catch (e: IOException) {
            Timber.w("server-pk failed: %s", e.message)
            return null
        }

        val type = object : TypeToken<ApiResponse<List<Int>>>() {}.type
        val response: ApiResponse<List<Int>> = gson.fromJson(body, type)
        return response.data?.toByteArray()
    }

    override suspend fun register(pkDev: ByteArray, apkCertSha1: String): RegisterResponse {
        val payload = gson.toJson(RegisterRequest.fromBytes(pkDev, apkCertSha1))
        val request = Request.Builder()
            .url(url("/register"))
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .header(HEADER_ACCEPT, JSON_CONTENT_TYPE)
            .build()

        val response = gson.apiResponseFrom<RegisterResponse>(execute(request))
        val data = response.data
            ?: throw IOException("Register failed: ${response.message}")
        Timber.i(
            "Registered as \"%s\" (color=%d, encrypted_sk_comm=%d B)",
            data.name,
            data.color,
            data.encrypted_sk_comm.size
        )
        return data
    }

    private fun url(path: String): String = baseUrlProvider().trimEnd('/') + path

    /** Runs the request off the main thread and returns the body of a 2xx response. */
    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        Timber.d("%s %s", request.method, request.url)
        clientProvider().newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${body.take(MAX_ERROR_BODY_CHARS)}")
            }
            body
        }
    }

    private fun List<Int>.toByteArray(): ByteArray = ByteArray(size) { this[it].toByte() }

    private companion object {
        const val HEADER_ACCEPT = "Accept"
        const val JSON_CONTENT_TYPE = "application/json"
        const val MAX_ERROR_BODY_CHARS = 512

        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val gson = Gson()
    }
}
