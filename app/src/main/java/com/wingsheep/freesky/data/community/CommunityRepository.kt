package com.wingsheep.freesky.data.community

import com.wingsheep.encrypt.CryptoResult
import com.wingsheep.encrypt.PostCrypto
import com.wingsheep.encrypt.identity.DeviceKeyManager
import com.wingsheep.encrypt.mls.MlsGroupManager
import com.wingsheep.freesky.model.RegistrationStore
import com.wingsheep.network.noise.NoiseApiClient
import com.wingsheep.network.noise.NoiseSessionFactory
import com.wingsheep.network.rotation.RegistrationHandler
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/** One page of the feed, already decrypted. */
data class FeedPage(val posts: List<DecryptedPost>, val hasMore: Boolean)

/** A failure whose message is fit to show to the user. */
class CommunityException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Asynchronous facts about the session that the UI has to react to. */
sealed interface CommunityEvent {

    /** The transport died on its own; the user has to reconnect. */
    data object SessionLost : CommunityEvent

    /** Another client posted; the feed should refresh. */
    data object NewPost : CommunityEvent
}

/**
 * The community data layer: owns the Noise session, the group key bootstrap and the
 * feed's pagination cursor.
 *
 * The view model owns presentation state only. Everything that outlives a single
 * screen interaction — reconnect-with-a-fresh-server-key, cursor bookkeeping,
 * encrypt-and-sign — lives here, so the screen's state machine stays readable.
 *
 * Scoped to the view model: the session is closed when the screen goes away (see
 * [close]).
 */
@ViewModelScoped
class CommunityRepository @Inject constructor(
    private val sessionFactory: NoiseSessionFactory,
    private val registrationHandler: RegistrationHandler,
    private val registrationStore: RegistrationStore,
    private val mls: MlsGroupManager,
    private val postCrypto: PostCrypto,
    private val postCodec: PostCodec,
    private val deviceKeys: DeviceKeyManager
) {

    private val _events = MutableSharedFlow<CommunityEvent>(extraBufferCapacity = EVENT_BUFFER_SIZE)

    /** Session events; collected by the view model. */
    val events: SharedFlow<CommunityEvent> = _events.asSharedFlow()

    /**
     * Serialises feed state.
     *
     * Pagination and a notification-driven refresh both read-modify-write the cursor
     * and the caller's list. Without this lock one can overwrite the other and posts
     * silently disappear until the next full reload.
     */
    private val feedMutex = Mutex()

    private var session: NoiseApiClient? = null
    private var nextCursor: Long? = null

    /**
     * Connects and authenticates the Noise transport.
     *
     * If the stored server key no longer works (server re-keyed), the key is fetched
     * again and the connection retried once before the failure is reported.
     */
    suspend fun connect() {
        close()
        withContext(Dispatchers.IO) {
            check(deviceKeys.keyExists()) { NOT_REGISTERED }
            ensureGroupKey()

            val serverNoisePk = registrationStore.loadServerNoisePk()
                ?: throw CommunityException("No server Noise key — re-register required")
            try {
                openSession(serverNoisePk)
            } catch (cancellation: CancellationException) {
                close()
                throw cancellation
            } catch (failure: Exception) {
                Timber.e(failure, "Noise session failed — refreshing the server Noise key")
                close()
                val freshKey = registrationHandler.fetchServerNoisePk() ?: throw failure
                registrationStore.saveServerNoisePk(freshKey)
                openSession(freshKey)
            }
        }
    }

    /** Loads the newest page and resets the pagination cursor to it. */
    suspend fun loadFirstPage(): FeedPage = feedMutex.withLock {
        val feed = requireSession().getFeed(cursor = null, limit = PAGE_SIZE)
        nextCursor = feed.next_cursor
        FeedPage(postCodec.decodeAll(feed.posts), hasMore = feed.next_cursor != null)
    }

    /** Loads the page after [loadFirstPage]; null once the feed is exhausted. */
    suspend fun loadNextPage(): FeedPage? = feedMutex.withLock {
        val cursor = nextCursor ?: return@withLock null
        val feed = requireSession().getFeed(cursor = cursor, limit = PAGE_SIZE)
        nextCursor = feed.next_cursor
        FeedPage(postCodec.decodeAll(feed.posts), hasMore = feed.next_cursor != null)
    }

    /**
     * Reloads the top of the feed and returns only the posts the caller does not
     * have yet.
     *
     * [knownIds] comes from the caller's list; the cursor is advanced only when the
     * refresh actually produced something new.
     */
    suspend fun refresh(knownIds: Set<Long>): List<DecryptedPost> = feedMutex.withLock {
        val feed = requireSession().getFeed(cursor = null, limit = PAGE_SIZE)
        val fresh = postCodec.decodeAll(feed.posts).filterNot { it.id in knownIds }
        if (fresh.isNotEmpty()) {
            nextCursor = feed.next_cursor
        }
        fresh
    }

    /** Encrypts, signs and submits a post. Throws [CommunityException] on rejection. */
    suspend fun submit(content: String, parentId: Long?) {
        val client = requireSession()

        // Keystore signing is far too slow for the caller's dispatcher.
        val envelope = withContext(Dispatchers.Default) {
            when (val result = postCrypto.createPost(content, mls, parentId)) {
                is CryptoResult.Error -> {
                    Timber.w("Encryption failed: %s", result.reason)
                    throw CommunityException("Encryption failed")
                }
                is CryptoResult.Success -> result.data
            }
        }

        val response = client.submitPost(envelope)
        if (response.message != SUCCESS_MESSAGE) {
            throw CommunityException(response.message)
        }
    }

    suspend fun report(postId: Long) {
        val client = requireSession()
        // Keystore read: keep it off the caller's (main) thread.
        val reporterPk = withContext(Dispatchers.Default) { deviceKeys.publicKeySec1() }
        client.reportPost(postId, reporterPk)
    }

    /** Releases the session and forgets the cursor. Idempotent. */
    fun close() {
        val client = session ?: return
        session = null
        nextCursor = null
        // Detach the listeners first, so shutting the socket down cannot report the
        // deliberate close as a lost session or a new post.
        client.setNotificationListener(null)
        client.setSessionLostListener(null)
        client.close()
    }

    private suspend fun openSession(serverNoisePk: ByteArray) {
        val client = sessionFactory.establishSession(
            serverNoisePk = serverNoisePk,
            apkCertSha1Hex = registrationHandler.computeApkCertSha1()
        )
        session = client
        // Called from the reader thread: tryEmit is safe there and never blocks it.
        client.setSessionLostListener { _events.tryEmit(CommunityEvent.SessionLost) }
        client.setNotificationListener { notification ->
            if (notification.type == NOTIFICATION_NEW_POST) {
                _events.tryEmit(CommunityEvent.NewPost)
            }
        }
        Timber.i("Noise session established")
    }

    private suspend fun ensureGroupKey() {
        if (mls.isInitialized()) return
        if (mls.restoreFromStorage().isSuccess) return

        val groupKey = registrationStore.loadGroupKey()
        if (groupKey == null || groupKey.size != GROUP_KEY_BYTES) {
            throw CommunityException("No group key — re-register required")
        }
        if (mls.initFromKeyMaterial(groupKey).isError) {
            throw CommunityException("Unable to initialise the group key")
        }
    }

    private fun requireSession(): NoiseApiClient =
        session ?: throw CommunityException("Not connected")

    private companion object {
        const val PAGE_SIZE = 20
        const val EVENT_BUFFER_SIZE = 8
        const val GROUP_KEY_BYTES = 32
        const val NOTIFICATION_NEW_POST = "new_post"
        const val SUCCESS_MESSAGE = "success"
        const val NOT_REGISTERED = "Device not registered — tap Register first"
    }
}
