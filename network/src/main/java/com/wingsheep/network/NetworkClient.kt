package com.wingsheep.network

import okhttp3.OkHttpClient
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Central OkHttpClient factory — the single source of truth for HTTP timeouts and
 * connection pooling.
 *
 * Clients are not cached here. An OkHttpClient owns a connection pool and a
 * dispatcher thread, so whoever builds a customised one (the Tor route) also takes
 * responsibility for closing it.
 */
object NetworkClient {

    /** Default timeout for every network request. */
    private const val DEFAULT_TIMEOUT_SECONDS = 30L

    /** Shared client for direct, non-proxied calls. */
    val defaultClient: OkHttpClient = newClient()

    /**
     * Builds a client, optionally routed through [proxy] (the Tor SOCKS proxy) and
     * with a custom [timeoutSeconds].
     */
    fun newClient(
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
        proxy: Proxy? = null
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .apply { if (proxy != null) proxy(proxy) }
        .build()
}
