package com.wingsheep.network.api

import com.wingsheep.network.NetworkClient
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OkHttp-based implementation of [ApiClient].
 *
 * Uses a single [OkHttpClient] instance (from [NetworkClient])
 * for all requests.  Each API method wraps the callback-based
 * OkHttp API into a Kotlin `suspend` function using
 * [suspendCancellableCoroutine].
 *
 * Usage:
 * ```
 * val client = OkHttpApiClient(baseUrl = "https://api.freesky.app")
 * val response = client.fetchNewGroupKey()
 * ```
 */
class OkHttpApiClient(
    private val baseUrl: String,
    private val client: OkHttpClient = NetworkClient.defaultClient
) : ApiClient {

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Fetches the new encrypted group key from the server.
     *
     * Makes a GET request to `{baseUrl}/register` and parses
     * the JSON response into [ApiClient.NewGroupKeyResponse].
     *
     * @throws IOException on network errors
     * @throws org.json.JSONException on malformed JSON
     */
    override suspend fun fetchNewGroupKey(): ApiClient.NewGroupKeyResponse {
        val request = Request.Builder()
            .url("$baseUrl/register")
            .get()
            .addHeader("Accept", "application/json")
            .build()

        return suspendCancellableCoroutine { cont ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!cont.isCancelled) {
                        cont.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        response.close()
                        cont.resumeWithException(
                            IOException("HTTP ${response.code}: $errorBody")
                        )
                        return
                    }

                    try {
                        val body = response.body?.string()
                            ?: throw IOException("Empty response body")
                        val json = JSONObject(body)

                        val result = ApiClient.NewGroupKeyResponse(
                            encryptedSkComm = json.getString("encrypted_sk_comm"),
                            serverPublicKey = json.getString("server_public_key")
                        )
                        cont.resume(result)
                    } catch (e: Exception) {
                        response.close()
                        cont.resumeWithException(e)
                    }
                }
            })
        }
    }
}
