package com.wingsheep.freesky.ui.community

import com.wingsheep.freesky.data.community.DecryptedPost

/** State of the Noise transport. */
sealed interface ConnectionState {
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Error(val message: String) : ConnectionState
}

/** State of the composer's send operation. */
sealed interface PostActionState {
    data object Idle : PostActionState
    data object Sending : PostActionState
    data object Sent : PostActionState
    data class Failed(val message: String) : PostActionState
}

/**
 * Everything the community screen renders, as one immutable value.
 *
 * A single state object instead of a flow per field: every recomposition sees a
 * consistent snapshot (a post list can never be paired with the previous cursor) and
 * the view model has exactly one place to publish to.
 */
data class CommunityUiState(
    val connection: ConnectionState = ConnectionState.Connecting,
    val posts: List<DecryptedPost> = emptyList(),
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val replyingTo: DecryptedPost? = null,
    val postAction: PostActionState = PostActionState.Idle
)
