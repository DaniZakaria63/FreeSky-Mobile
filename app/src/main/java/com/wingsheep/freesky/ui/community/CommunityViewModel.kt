package com.wingsheep.freesky.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wingsheep.encrypt.PostCrypto
import com.wingsheep.encrypt.identity.DeviceKeyManager
import com.wingsheep.encrypt.identity.IdentityDeriver
import com.wingsheep.encrypt.mls.MlsGroupManager
import com.wingsheep.encrypt.model.EncryptedPost
import com.wingsheep.freesky.model.RegistrationStore
import com.wingsheep.network.noise.NoiseApiClient
import com.wingsheep.network.noise.NoiseSessionFactory
import com.wingsheep.network.rotation.RegistrationHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val noiseSessionFactory: NoiseSessionFactory,
    private val registrationHandler: RegistrationHandler,
    private val registrationStore: RegistrationStore,
    private val mlsGroupManager: MlsGroupManager,
    private val postCrypto: PostCrypto
) : ViewModel() {

    private val _connectionState = MutableStateFlow<CommunityUiState>(CommunityUiState.Connecting)
    val connectionState: StateFlow<CommunityUiState> = _connectionState.asStateFlow()

    private val _posts = MutableStateFlow<List<DecryptedPost>>(emptyList())
    val posts: StateFlow<List<DecryptedPost>> = _posts.asStateFlow()

    private val _postAction = MutableStateFlow<PostActionState>(PostActionState.Idle)
    val postAction: StateFlow<PostActionState> = _postAction.asStateFlow()

    private val _replyingTo = MutableStateFlow<DecryptedPost?>(null)
    val replyingTo: StateFlow<DecryptedPost?> = _replyingTo.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _threadReplies = MutableStateFlow<Map<Long, List<DecryptedPost>>>(emptyMap())
    val threadReplies: StateFlow<Map<Long, List<DecryptedPost>>> = _threadReplies.asStateFlow()

    private var noiseClient: NoiseApiClient? = null
    private var connectJob: Job? = null

    /** Only ever read or written while [feedLock] is held. */
    private var nextCursor: Long? = null
    private val pageSize: Int = 20

    /**
     * Serializes feed state.
     *
     * [loadFeed], [loadMore] and [refreshFeed] each perform a read-modify-write on
     * [nextCursor] and the post list. Without this, a notification-driven refresh
     * landing in the middle of pagination lets one overwrite the other, and posts
     * silently disappear from the list until the next full reload.
     */
    private val feedLock = Mutex()
    private val localPkSec1: ByteArray by lazy {
        registrationHandler.deviceKeyManager.publicKeySec1()
    }

    init {
        connect()
    }

    fun connect() {
        // Ignore taps while a connection attempt is already running.
        if (connectJob?.isActive == true) return
        connectJob = viewModelScope.launch { doConnect() }
    }

    private suspend fun doConnect() {
        _connectionState.value = CommunityUiState.Connecting
        // Drop any previous session before establishing a new one.
        releaseSession()

        if (!DeviceKeyManager.keyExists()) {
            _connectionState.value = CommunityUiState.Error("Device not registered — tap Register first")
            return
        }

        try {
            val serverNoisePk = registrationStore.loadServerNoisePk()
            if (serverNoisePk == null) {
                _connectionState.value = CommunityUiState.Error("No server Noise key — re-register required")
                return
            }

            if (!mlsGroupManager.isInitialized()) {
                val restoreResult = mlsGroupManager.restoreFromStorage()
                if (!restoreResult.isSuccess) {
                    val groupKey = registrationStore.loadGroupKey()
                    if (groupKey != null && groupKey.size == 32) {
                        val pkSec1 = registrationHandler.deviceKeyManager.publicKeySec1()
                        mlsGroupManager.initFromKeyMaterial(groupKey, pkSec1)
                    } else {
                        _connectionState.value = CommunityUiState.Error("No group key — re-register required")
                        return
                    }
                }
            }

            openSession(serverNoisePk)
        } catch (e: CancellationException) {
            releaseSession()
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Noise session failed — trying server-pk refresh")
            releaseSession()
            val freshPk = registrationHandler.fetchServerNoisePk()
            if (freshPk != null) {
                registrationStore.saveServerNoisePk(freshPk)
                _connectionState.value = CommunityUiState.Connecting
                try {
                    openSession(freshPk)
                    return
                } catch (retryCancel: CancellationException) {
                    releaseSession()
                    throw retryCancel
                } catch (retryErr: Exception) {
                    Timber.e(retryErr, "Noise retry also failed")
                }
            }
            releaseSession()
            _connectionState.value = CommunityUiState.Error(e.message ?: "Connection failed")
        }
    }

    /**
     * Establishes a Noise session, subscribes to real-time notifications,
     * and loads the first feed page.
     */
    private suspend fun openSession(serverNoisePk: ByteArray) {
        val apkCertSha1 = registrationHandler.computeApkCertSha1()
        val client = noiseSessionFactory.establishSession(
            serverNoisePk = serverNoisePk,
            apkCertSha1Hex = apkCertSha1
        )
        noiseClient = client
        _connectionState.value = CommunityUiState.Connected
        Timber.i("Noise session established — loading feed")

        // If the transport dies later, surface it instead of leaving the UI
        // stuck on "Connected".
        client.setSessionLostListener {
            viewModelScope.launch {
                if (noiseClient === client) {
                    _connectionState.value = CommunityUiState.Error("Connection lost")
                }
            }
        }

        // Subscribe to real-time notifications from the server.
        // When another client posts, the server pushes a "new_post"
        // notification over the Noise transport. We respond by
        // refreshing the feed.
        client.setNotificationListener { notification ->
            if (notification.type == "new_post") {
                Timber.d("Received new_post notification (ts=${notification.timestamp})")
                viewModelScope.launch {
                    refreshFeed()
                }
            }
        }

        loadFeed()
    }

    /** Closes the active Noise session, if any, without touching UI state. */
    private fun releaseSession() {
        val client = noiseClient
        noiseClient = null
        client?.setNotificationListener(null)
        client?.setSessionLostListener(null)
        client?.close()
    }

    fun loadFeed() {
        viewModelScope.launch {
            val client = noiseClient
            if (client == null) {
                _connectionState.value = CommunityUiState.Error("Not connected")
                return@launch
            }
            try {
                feedLock.withLock {
                    val feed = client.getFeed(cursor = null, limit = pageSize)
                    val decrypted = decryptAll(feed.posts)
                    _posts.value = decrypted
                    nextCursor = feed.next_cursor
                    _hasMore.value = feed.next_cursor != null
                    Timber.i("Feed loaded: ${decrypted.size} posts, nextCursor=$nextCursor")
                }
            } catch (e: Exception) {
                Timber.e(e, "Feed load failed")
            }
        }
    }

    /**
     * Refresh the feed from the top — used when a real-time "new_post"
     * notification arrives. Fetches the newest posts and prepends any
     * that aren't already in the list.
     */
    private suspend fun refreshFeed() {
        feedLock.withLock {
            val client = noiseClient ?: return
            try {
                val feed = client.getFeed(cursor = null, limit = pageSize)
                val decrypted = decryptAll(feed.posts)

                // Prepend new posts that aren't already in the list.
                val existingIds = _posts.value.map { it.id }.toSet()
                val newPosts = decrypted.filter { it.id !in existingIds }
                if (newPosts.isNotEmpty()) {
                    _posts.value = newPosts + _posts.value
                    nextCursor = feed.next_cursor
                    _hasMore.value = feed.next_cursor != null
                    Timber.i("Feed refreshed: +${newPosts.size} new posts")
                }
            } catch (e: Exception) {
                Timber.e(e, "Feed refresh failed")
            }
        }
    }

    fun loadThread(postId: Long) {
        viewModelScope.launch {
            val client = noiseClient ?: return@launch
            try {
                val thread = client.getThread(postId)
                val (parent, replies) = withContext(Dispatchers.Default) {
                    decryptPostEntry(thread.post) to thread.replies.mapNotNull { decryptPostEntry(it) }
                }
                if (parent != null) {
                    // Store thread replies so UI can show them expanded. update() is a
                    // CAS loop, so two threads cannot drop each other's entry the way
                    // a get-then-set could.
                    _threadReplies.update { it + (postId to replies) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Thread load failed for post $postId")
            }
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                feedLock.withLock {
                    // Read the cursor under the lock: a concurrent refresh may have
                    // moved it since loadMore() was called.
                    val cursor = nextCursor ?: return@withLock
                    val client = noiseClient ?: return@withLock
                    val feed = client.getFeed(cursor = cursor, limit = pageSize)
                    val decrypted = decryptAll(feed.posts)
                    _posts.value = _posts.value + decrypted
                    nextCursor = feed.next_cursor
                    _hasMore.value = feed.next_cursor != null
                    Timber.i("Loaded more: +${decrypted.size} posts, nextCursor=$nextCursor")
                }
            } catch (e: Exception) {
                Timber.e(e, "Load more failed")
                _hasMore.value = false
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun setReplyingTo(post: DecryptedPost?) {
        _replyingTo.value = post
    }

    fun clearReplyingTo() {
        _replyingTo.value = null
    }

    fun sendPost(content: String, parentId: Long? = null) {
        if (content.isBlank()) return
        viewModelScope.launch {
            _postAction.value = PostActionState.Sending
            try {
                val client = noiseClient
                if (client == null) {
                    _postAction.value = PostActionState.Failed("Not connected")
                    return@launch
                }

                // Signing + AES-GCM via the keystore is far too slow for the main
                // thread, which is where viewModelScope runs by default.
                val encryptedPost = withContext(Dispatchers.Default) {
                    postCrypto.createPost(
                        content = content,
                        mlsManager = mlsGroupManager,
                        deviceKeyManager = DeviceKeyManager
                    )?.copy(parentId = parentId)
                }
                if (encryptedPost == null) {
                    _postAction.value = PostActionState.Failed("Encryption failed")
                    return@launch
                }

                val response = client.submitPost(encryptedPost)
                if (response.message == "success") {
                    _postAction.value = PostActionState.Sent
                    _replyingTo.value = null
                } else {
                    _postAction.value = PostActionState.Failed(response.message)
                }
            } catch (e: Exception) {
                Timber.e(e, "Post submission failed")
                _postAction.value = PostActionState.Failed(e.message ?: "Send failed")
            }
        }
    }

    fun sendReply(content: String) {
        val parent = _replyingTo.value ?: return
        sendPost(content, parentId = parent.id)
    }

    fun reportPost(postId: Long) {
        viewModelScope.launch {
            val client = noiseClient ?: return@launch
            try {
                val pk = registrationHandler.deviceKeyManager.publicKeySec1()
                client.reportPost(postId, pk)
                Timber.i("Reported post $postId")
            } catch (e: Exception) {
                Timber.e(e, "Report failed for post $postId")
            }
        }
    }

    fun resetPostAction() {
        _postAction.value = PostActionState.Idle
    }

    /**
     * Decrypts a page off the main thread.
     *
     * Every entry costs a keystore ECDSA verification plus an AES-GCM decryption, so
     * doing this inline on the UI thread janks proportionally to the page size.
     */
    private suspend fun decryptAll(
        entries: List<com.wingsheep.network.model.PostEntryData>
    ): List<DecryptedPost> = withContext(Dispatchers.Default) {
        entries.mapNotNull { decryptPostEntry(it) }
    }

    private fun decryptPostEntry(entry: com.wingsheep.network.model.PostEntryData): DecryptedPost? {
        val ciphertext = entry.ciphertext_comm.map { it.toByte() }.toByteArray()
        val authorPk = entry.author_pk.map { it.toByte() }.toByteArray()
        val authorSig = entry.author_sig.map { it.toByte() }.toByteArray()

        val encryptedPost = EncryptedPost(
            ciphertextComm = ciphertext,
            authorPk = authorPk,
            authorSig = authorSig,
            timestamp = entry.timestamp,
            mlsEpoch = entry.mls_epoch
        )

        val authorPublicKey = try {
            DeviceKeyManager.sec1ToPublicKey(authorPk)
        } catch (e: Exception) {
            Timber.w("Failed to parse author_pk: ${e.message}")
            null
        } ?: return null

        val plaintext = postCrypto.readPost(
            post = encryptedPost,
            mlsManager = mlsGroupManager,
            authorPublicKey = authorPublicKey
        ) ?: return null

        val isMine = authorPk.contentEquals(localPkSec1)

        return DecryptedPost(
            id = entry.id,
            content = plaintext,
            authorIdentity = IdentityDeriver.deriveIdentity(authorPk),
            timestamp = entry.timestamp * 1000,
            mlsEpoch = entry.mls_epoch,
            isMine = isMine,
            parentId = entry.parent_id
        )
    }

    override fun onCleared() {
        super.onCleared()
        releaseSession()
    }
}
