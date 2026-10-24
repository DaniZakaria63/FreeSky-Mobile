package com.wingsheep.network.model

/**
 * Response body for POST /post.
 *
 * Constructed by the client from the envelope's `message` and never touched by
 * Gson reflection, so it deliberately carries no keep annotation: R8 may rename or
 * inline it freely.
 *
 * Server returns:
 *   Success:  { "message": "success", "data": null }
 *   Errors:   { "message": "invalid author key", "data": null }
 *             { "message": "invalid signature", "data": null }
 *             { "message": "author is banned", "data": null }
 *             { "message": "internal error", "data": null }
 *
 * Reference: POST /post API contract
 */
data class PostResponse(
    val message: String,
    val data: Any? = null
)
