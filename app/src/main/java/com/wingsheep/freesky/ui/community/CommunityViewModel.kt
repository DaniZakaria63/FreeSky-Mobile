package com.wingsheep.freesky.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wingsheep.encrypt.PostCrypto
import com.wingsheep.encrypt.identity.DeviceKeyManager
import com.wingsheep.encrypt.identity.IdentityDeriver
import com.wingsheep.encrypt.mls.MlsGroupManager
import com.wingsheep.encrypt.model.EncryptedPost
import com.wingsheep.freesky.model.RegistrationStore
import com.wingsheep.network.model.Notification
import com.wingsheep.network.noise.NotificationListener
import com.wingsheep.network.noise.NoiseApiClient
import com.wingsheep.network.noise.NoiseSessionFactory
import com.wingsheep.network.rotation.RegistrationHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private var nextCursor: Long? = null
    private val pageSize: Int = 20
    private val localPkSec1: ByteArray by lazy {
        registrationHandler.deviceKeyManager.publicKeySec1()
    }

    init {
        connect()
    }

    fun connect() {
        viewModelScope.launch {
            _connectionState.value = CommunityUiState.Connecting

            if (!DeviceKeyManager.keyExists()) {
                _connectionState.value = CommunityUiState.Error("Device not registered — tap Register first")
                return@launch
            }

            try {
                val serverNoisePk = registrationStore.loadServerNoisePk()
                if (serverNoisePk == null) {
                    _connectionState.value = CommunityUiState.Error("No server Noise key — re-register required")
                    return@launch
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
                            return@launch
                        }
                    }
                }

                val apkCertSha1 = registrationHandler.computeApkCertSha1()
                val client = noiseSessionFactory.establishSession(
                    serverNoisePk = serverNoisePk,
                    apkCertSha1Hex = apkCertSha1
                )
                noiseClient = client
                _connectionState.value = CommunityUiState.Connected
                Timber.i("Noise session established — loading feed")

                // Subscribe to real-time notifications from the server.
                // When another client posts, the server pushes a "new_post"
                // notification over the Noise transport. We respond by
                // refreshing the feed.
                client.setNotificationListener(object : NotificationListener {
                    override fun onNotification(notification: Notification) {
                        if (notification.type == "new_post") {
                            Timber.d("Received new_post notification (ts=${notification.timestamp})")
                            viewModelScope.launch {
                                refreshFeed()
                            }
                        }
                    }
                })

                loadFeed()
            } catch (e: Exception) {
                Timber.e(e, "Noise session failed — trying server-pk refresh")
                val freshPk = registrationHandler.fetchServerNoisePk()
                if (freshPk != null) {
                    registrationStore.saveServerNoisePk(freshPk)
                    _connectionState.value = CommunityUiState.Connecting
                    try {
                        val apkCertSha1 = registrationHandler.computeApkCertSha1()
                        val client = noiseSessionFactory.establishSession(
                            serverNoisePk = freshPk,
                            apkCertSha1Hex = apkCertSha1
                        )
                        noiseClient = client
                        _connectionState.value = CommunityUiState.Connected
                        client.setNotificationListener(object : NotificationListener {
                            override fun onNotification(notification: Notification) {
                                if (notification.type == "new_post") {
                                    viewModelScope.launch { refreshFeed() }
                                }
                            }
                        })
                        loadFeed()
                        return@launch
                    } catch (retryErr: Exception) {
                        Timber.e(retryErr, "Noise retry also failed")
                    }
                }
                _connectionState.value = CommunityUiState.Error(e.message ?: "Connection failed")
            }
        }
    }

    fun loadFeed() {
        viewModelScope.launch {
            val client = noiseClient
            if (client == null) {
                _connectionState.value = CommunityUiState.Error("Not connected")
                return@launch
            }
            try {
                val feed = client.getFeed(cursor = null, limit = pageSize)
                val decrypted = feed.posts.mapNotNull { decryptPostEntry(it) }
                _posts.value = decrypted
                nextCursor = feed.next_cursor
                _hasMore.value = feed.next_cursor != null
                Timber.i("Feed loaded: ${decrypted.size} posts, nextCursor=$nextCursor")
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
        val client = noiseClient ?: return
        try {
            val feed = client.getFeed(cursor = null, limit = pageSize)
            val decrypted = feed.posts.mapNotNull { decryptPostEntry(it) }

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

    fun loadThread(postId: Long) {
        viewModelScope.launch {
            val client = noiseClient ?: return@launch
            try {
                val thread = client.getThread(postId)
                val parent = decryptPostEntry(thread.post)
                val replies = thread.replies.mapNotNull { decryptPostEntry(it) }
                if (parent != null) {
                    // Store thread replies so UI can show them expanded.
                    _threadReplies.value = _threadReplies.value + (postId to replies)
                }
            } catch (e: Exception) {
                Timber.e(e, "Thread load failed for post $postId")
            }
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value) return
        val cursor = nextCursor ?: return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val client = noiseClient ?: return@launch
                val feed = client.getFeed(cursor = cursor, limit = pageSize)
                val decrypted = feed.posts.mapNotNull { decryptPostEntry(it) }
                _posts.value = _posts.value + decrypted
                nextCursor = feed.next_cursor
                _hasMore.value = feed.next_cursor != null
                Timber.i("Loaded more: +${decrypted.size} posts, nextCursor=$nextCursor")
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

                val encryptedPost = postCrypto.createPost(
                    content = content,
                    mlsManager = mlsGroupManager,
                    deviceKeyManager = DeviceKeyManager
                )?.copy(parentId = parentId)
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
        noiseClient?.setNotificationListener(null)
        noiseClient?.close()
    }
}
