package com.wingsheep.network.noise

import com.google.gson.Gson
import com.wingsheep.encrypt.model.EncryptedPost
import com.wingsheep.network.model.FeedData
import com.wingsheep.network.model.NewGroupKeyData
import com.wingsheep.network.model.PostResponse
import com.wingsheep.network.model.RegisterRequest
import com.wingsheep.network.model.RegisterResponse
import com.wingsheep.network.model.ThreadData
import com.wingsheep.network.model.apiResponseFrom
import timber.log.Timber
import java.io.IOException

class NoiseApiClient(private val noiseManager: NoiseManager) {

    private val gson = Gson()

    /**
     * Register a listener for unsolicited server notifications (e.g. "new_post").
     *
     * The listener is invoked on a background thread. Call this after
     * [NoiseSessionFactory.establishSession] returns.
     */
    fun setNotificationListener(listener: NotificationListener?) {
        noiseManager.setNotificationListener(listener)
    }

    suspend fun register(pkDev: ByteArray, apkCertSha1: String): RegisterResponse {
        val reqBody = RegisterRequest.fromBytes(pkDev, apkCertSha1)
        val json = noiseManager.sendRequest("register", mapOf(
            "pk_dev" to reqBody.pk_dev,
            "apk_cert_sha1" to reqBody.apk_cert_sha1
        ))
        val wrapped = gson.apiResponseFrom<RegisterResponse>(json)
        return wrapped.data
            ?: throw IOException("Noise register failed: ${wrapped.message}")
    }

    suspend fun submitPost(post: EncryptedPost): PostResponse {
        val params = mutableMapOf<String, Any?>(
            "ciphertext_comm" to post.ciphertextComm.map { it.toInt() and 0xFF },
            "author_pk" to post.authorPk.map { it.toInt() and 0xFF },
            "author_sig" to post.authorSig.map { it.toInt() and 0xFF },
            "timestamp" to post.timestamp / 1000,
            "mls_epoch" to post.mlsEpoch
        )
        if (post.parentId != null) {
            params["parent_id"] = post.parentId
        }
        val json = noiseManager.sendRequest("post", params)
        val wrapped = gson.apiResponseFrom<Any?>(json)
        Timber.d("Noise post result: ${wrapped.message}")
        return PostResponse(message = wrapped.message)
    }

    suspend fun fetchNewGroupKey(): NewGroupKeyData {
        val json = noiseManager.sendRequest("new_group_key", emptyMap())
        val wrapped = gson.apiResponseFrom<NewGroupKeyData>(json)
        return wrapped.data
            ?: throw IOException("Noise new_group_key failed: ${wrapped.message}")
    }

    suspend fun getFeed(cursor: Long? = null, limit: Int = 20): FeedData {
        val params = mutableMapOf<String, Any?>()
        if (cursor != null) params["cursor"] = cursor
        params["limit"] = limit
        val json = noiseManager.sendRequest("feed", params)
        val wrapped = gson.apiResponseFrom<FeedData>(json)
        return wrapped.data
            ?: throw IOException("Noise feed failed: ${wrapped.message}")
    }

    suspend fun getThread(postId: Long): ThreadData {
        val json = noiseManager.sendRequest("thread", mapOf(
            "post_id" to postId
        ))
        val wrapped = gson.apiResponseFrom<ThreadData>(json)
        return wrapped.data
            ?: throw IOException("Noise thread failed: ${wrapped.message}")
    }

    suspend fun reportPost(postId: Long, reporterPk: ByteArray, reason: String? = null): PostResponse {
        val json = noiseManager.sendRequest("report", mapOf(
            "post_id" to postId,
            "reporter_pk" to reporterPk.map { it.toInt() and 0xFF },
            "reason" to reason
        ))
        val wrapped = gson.apiResponseFrom<Any?>(json)
        Timber.d("Noise report result: ${wrapped.message}")
        return PostResponse(message = wrapped.message)
    }

    fun close() = noiseManager.close()
}
