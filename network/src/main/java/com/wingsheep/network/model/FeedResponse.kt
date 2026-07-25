package com.wingsheep.network.model

data class FeedData(
    val posts: List<PostEntryData>,
    val next_cursor: Long?
)

data class PostEntryData(
    val id: Long,
    val ciphertext_comm: List<Int>,
    val author_pk: List<Int>,
    val author_sig: List<Int>,
    val timestamp: Long,
    val mls_epoch: Long
)
