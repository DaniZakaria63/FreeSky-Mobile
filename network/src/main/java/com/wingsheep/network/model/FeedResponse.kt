package com.wingsheep.network.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/** One page of the feed. Populated by Gson, hence [Keep]. */
@Keep
data class FeedData(
    @SerializedName("posts") val posts: List<PostEntryData>,
    @SerializedName("next_cursor") val next_cursor: Long?
)

/** A post as it travels on the wire (ciphertext, author key, signature). */
@Keep
data class PostEntryData(
    @SerializedName("id") val id: Long,
    @SerializedName("ciphertext_comm") val ciphertext_comm: List<Int>,
    @SerializedName("author_pk") val author_pk: List<Int>,
    @SerializedName("author_sig") val author_sig: List<Int>,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("mls_epoch") val mls_epoch: Long,
    @SerializedName("parent_id") val parent_id: Long? = null
)

/** A parent post plus its replies. Populated by Gson, hence [Keep]. */
@Keep
data class ThreadData(
    @SerializedName("post") val post: PostEntryData,
    @SerializedName("replies") val replies: List<PostEntryData>
)
