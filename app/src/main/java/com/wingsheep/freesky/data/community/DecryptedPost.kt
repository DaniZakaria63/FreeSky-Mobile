package com.wingsheep.freesky.data.community

import com.wingsheep.encrypt.identity.Identity

/**
 * A post after signature verification and decryption, ready for display.
 *
 * The author name and colour are derived client-side from `author_pk`, mirroring the
 * server's deterministic identity derivation.
 */
data class DecryptedPost(
    val id: Long,
    val content: String,
    val authorIdentity: Identity,
    val timestamp: Long,
    val mlsEpoch: Long,
    val isMine: Boolean = false,
    val parentId: Long? = null
)
