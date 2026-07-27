package com.wingsheep.network.model

/**
 * Unsolicited notification pushed from server to connected clients over Noise.
 *
 * The client distinguishes notifications from request responses by the `type`
 * field — responses use `message`/`data`, notifications use `type`.
 *
 * Example JSON:
 * ```json
 * { "type": "new_post", "timestamp": 1721692800000 }
 * ```
 */
data class Notification(
    val type: String,
    val timestamp: Long
)
