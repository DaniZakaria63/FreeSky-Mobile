package com.wingsheep.network.api

/**
 * API client interface for server communication.
 *
 * This is the contract that the network layer implements.
 * All API calls in the app go through this interface — there
 * are no direct HTTP calls elsewhere.
 *
 * Implemented by [com.wingsheep.network.api.OkHttpApiClient]
 * using OkHttp.
 */
interface ApiClient {

    /** Response from key rotation endpoint (`GET /register` or `POST /admin/key-rotated`). */
    data class NewGroupKeyResponse(
        /** Hex-encoded ECIES-encrypted group key for this device. */
        val encryptedSkComm: String,

        /** Hex-encoded server public key (for ECIES encryption). */
        val serverPublicKey: String
    )

    /** Fetches the new encrypted group key from the server. */
    suspend fun fetchNewGroupKey(): NewGroupKeyResponse
}
