package com.wingsheep.network.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

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
@Keep
data class Notification(
    @SerializedName("type") val type: String,
    @SerializedName("timestamp") val timestamp: Long
)
