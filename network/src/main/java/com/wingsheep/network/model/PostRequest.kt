package com.wingsheep.network.model

import com.wingsheep.encrypt.model.EncryptedPost

/**
 * Request body for POST /post.
 *
 * The production transport builds this payload as a map inside
 * [com.wingsheep.network.noise.NoiseApiClient], so this class is exercised by unit
 * tests only — it deliberately has no keep annotation and R8 may strip it.
 *
 * Field names use snake_case to match the server's serde deserialization
 * of `freesky_shared::types::PostRequest` (Rust `Vec<u8>` → JSON array of ints).
 *
 * Reference: PROTOCOL_SYNC.md §3.2 — Post Endpoints
 */
data class PostRequest(
    val ciphertext_comm: List<Int>,
    val author_pk: List<Int>,
    val author_sig: List<Int>,
    val timestamp: Long,
    val mls_epoch: Long,
    val parent_id: Long? = null
) {
    companion object {
        fun fromEncryptedPost(post: EncryptedPost): PostRequest = PostRequest(
            ciphertext_comm = post.ciphertextComm.map { it.toInt() and 0xFF },
            author_pk = post.authorPk.map { it.toInt() and 0xFF },
            author_sig = post.authorSig.map { it.toInt() and 0xFF },
            timestamp = post.timestamp / 1000,
            mls_epoch = post.mlsEpoch,
            parent_id = post.parentId
        )
    }
}
