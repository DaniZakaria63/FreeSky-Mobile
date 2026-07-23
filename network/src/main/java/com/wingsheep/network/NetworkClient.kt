package com.wingsheep.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Central OkHttpClient provider for the network module.
 *
 * All API clients in this module should obtain their OkHttpClient
 * from [defaultClient] or create a customised instance via
 * [newClient].
 *
 * This is the **single source of truth** for HTTP configuration:
 * timeouts, interceptors, and connection pooling are configured here.
 */
object NetworkClient {

    /** Default timeout for all network requests (30 seconds). */
    private const val DEFAULT_TIMEOUT_SECONDS = 30L

    /**
     * Pre-configured OkHttpClient with sensible defaults.
     *
     * - 30-second connect and read timeouts
     * - Shared connection pool (HTTP/1.1 keep-alive)
     */
    val defaultClient: OkHttpClient = newClient()

    /**
     * Creates a new OkHttpClient with the specified [baseUrl].
     *
     * Override [timeoutSeconds] or add interceptors as needed.
     */
    fun newClient(
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()
}
