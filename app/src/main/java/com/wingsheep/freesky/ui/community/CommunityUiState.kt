package com.wingsheep.freesky.ui.community

import com.wingsheep.encrypt.identity.Identity

/**
 * A decrypted community post ready for display.
 *
 * `authorName`/`authorColor` are derived client-side from `author_pk` via
 * [com.wingsheep.encrypt.identity.IdentityDeriver], mirroring the server's
 * deterministic identity derivation.
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

sealed class CommunityUiState {
    data object Connecting : CommunityUiState()
    data object Connected : CommunityUiState()
    data class Error(val message: String) : CommunityUiState()
}

sealed class PostActionState {
    data object Idle : PostActionState()
    data object Sending : PostActionState()
    data object Sent : PostActionState()
    data class Failed(val message: String) : PostActionState()
}
