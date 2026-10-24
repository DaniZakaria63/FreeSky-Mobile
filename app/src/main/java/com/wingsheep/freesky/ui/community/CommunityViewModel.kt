package com.wingsheep.freesky.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wingsheep.freesky.data.community.CommunityEvent
import com.wingsheep.freesky.data.community.CommunityRepository
import com.wingsheep.freesky.data.community.DecryptedPost
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Presentation state machine for the community screen.
 *
 * All transport, crypto and pagination work is delegated to [CommunityRepository];
 * this class only turns results (and asynchronous session events) into
 * [CommunityUiState] and translates user intents into calls.
 */
@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val repository: CommunityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    private var connectJob: Job? = null

    init {
        connect()
        observeRepositoryEvents()
    }

    /** Connects (or reconnects) and loads the newest page. */
    fun connect() {
        // Ignore taps while an attempt is already running.
        if (connectJob?.isActive == true) return
        connectJob = viewModelScope.launch {
            _uiState.update {
                it.copy(connection = ConnectionState.Connecting, postAction = PostActionState.Idle)
            }
            try {
                repository.connect()
                _uiState.update { it.copy(connection = ConnectionState.Connected) }
                loadFirstPage()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                Timber.e(failure, "Noise session failed")
                _uiState.update {
                    it.copy(connection = ConnectionState.Error(failure.message ?: "Connection failed"))
                }
            }
        }
    }

    /** Appends the next page; no-op while a page is already loading. */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore || state.connection !is ConnectionState.Connected) {
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val page = repository.loadNextPage() ?: return@launch
                _uiState.update { it.copy(posts = it.posts + page.posts, hasMore = page.hasMore) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                // Stop trying: a failing page is usually the transport, not a
                // transient hiccup, and the next refresh resets hasMore.
                Timber.w(failure, "Loading more posts failed")
                _uiState.update { it.copy(hasMore = false) }
            } finally {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun sendPost(content: String) {
        submit(content, parentId = null)
    }

    fun sendReply(content: String) {
        val parent = _uiState.value.replyingTo ?: return
        submit(content, parentId = parent.id)
    }

    fun reportPost(postId: Long) {
        viewModelScope.launch {
            try {
                repository.report(postId)
                Timber.i("Reported post %d", postId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                Timber.w(failure, "Reporting post %d failed", postId)
            }
        }
    }

    fun setReplyingTo(post: DecryptedPost?) {
        _uiState.update { it.copy(replyingTo = post) }
    }

    fun clearReplyingTo() {
        setReplyingTo(null)
    }

    fun resetPostAction() {
        _uiState.update { it.copy(postAction = PostActionState.Idle) }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    private suspend fun loadFirstPage() {
        try {
            val page = repository.loadFirstPage()
            _uiState.update { it.copy(posts = page.posts, hasMore = page.hasMore) }
            Timber.i("Feed loaded: %d posts", page.posts.size)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            // The session is usable; only the first page failed.
            Timber.w(failure, "Loading the feed failed")
        }
    }

    private fun submit(content: String, parentId: Long?) {
        if (content.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(postAction = PostActionState.Sending) }
            try {
                repository.submit(content, parentId)
                _uiState.update {
                    it.copy(postAction = PostActionState.Sent, replyingTo = null)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                Timber.w(failure, "Post submission failed")
                _uiState.update {
                    it.copy(postAction = PostActionState.Failed(failure.message ?: "Send failed"))
                }
            }
        }
    }

    private fun observeRepositoryEvents() {
        viewModelScope.launch {
            repository.events.collect { event ->
                when (event) {
                    CommunityEvent.SessionLost -> onSessionLost()
                    CommunityEvent.NewPost -> refreshFeed()
                }
            }
        }
    }

    private fun onSessionLost() {
        _uiState.update { state ->
            // A reconnect may already be running; only a live session is affected.
            if (state.connection is ConnectionState.Connected) {
                state.copy(connection = ConnectionState.Error("Connection lost"))
            } else {
                state
            }
        }
    }

    private suspend fun refreshFeed() {
        try {
            val knownIds = _uiState.value.posts.mapTo(HashSet()) { it.id }
            val fresh = repository.refresh(knownIds)
            if (fresh.isNotEmpty()) {
                _uiState.update { it.copy(posts = fresh + it.posts) }
                Timber.i("Feed refreshed: +%d posts", fresh.size)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Timber.w(failure, "Feed refresh failed")
        }
    }
}
