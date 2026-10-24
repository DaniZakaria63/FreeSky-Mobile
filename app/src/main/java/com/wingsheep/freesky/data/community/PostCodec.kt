package com.wingsheep.freesky.data.community

import com.wingsheep.encrypt.CryptoResult
import com.wingsheep.encrypt.PostCrypto
import com.wingsheep.encrypt.identity.DeviceKeyManager
import com.wingsheep.encrypt.identity.IdentityDeriver
import com.wingsheep.encrypt.mls.MlsGroupManager
import com.wingsheep.encrypt.model.EncryptedPost
import com.wingsheep.network.model.PostEntryData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Turns wire [PostEntryData] into display-ready [DecryptedPost]s.
 *
 * Every entry costs a keystore ECDSA verification plus an AES-GCM decryption, so
 * decoding always runs on [Dispatchers.Default] — doing it inline on the main thread
 * would jank proportionally to the page size.
 *
 * A post that fails verification or decryption is dropped (and logged), never shown
 * as partial content.
 */
class PostCodec @Inject constructor(
    private val postCrypto: PostCrypto,
    private val mls: MlsGroupManager,
    private val deviceKeys: DeviceKeyManager
) {

    private val localPublicKey: ByteArray by lazy { deviceKeys.publicKeySec1() }

    suspend fun decodeAll(entries: List<PostEntryData>): List<DecryptedPost> =
        withContext(Dispatchers.Default) { entries.mapNotNull(::decode) }

    private fun decode(entry: PostEntryData): DecryptedPost? {
        val authorPublicKeyBytes = entry.author_pk.toByteArray()
        val authorPublicKey = runCatching { deviceKeys.sec1ToPublicKey(authorPublicKeyBytes) }
            .onFailure { Timber.w(it, "Post %d has an unparsable author_pk", entry.id) }
            .getOrNull()
            ?: return null

        val envelope = EncryptedPost(
            ciphertextComm = entry.ciphertext_comm.toByteArray(),
            authorPk = authorPublicKeyBytes,
            authorSig = entry.author_sig.toByteArray(),
            timestamp = entry.timestamp,
            mlsEpoch = entry.mls_epoch,
            parentId = entry.parent_id
        )

        // readPost verifies the author signature before decrypting.
        val content = when (val result = postCrypto.readPost(envelope, mls, authorPublicKey)) {
            is CryptoResult.Error -> {
                Timber.w("Post %d rejected: %s", entry.id, result.reason)
                return null
            }
            is CryptoResult.Success -> result.data
        }

        return DecryptedPost(
            id = entry.id,
            content = content,
            authorIdentity = IdentityDeriver.deriveIdentity(authorPublicKeyBytes),
            timestamp = entry.timestamp * MILLIS_PER_SECOND,
            mlsEpoch = entry.mls_epoch,
            isMine = authorPublicKeyBytes.contentEquals(localPublicKey),
            parentId = entry.parent_id
        )
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}

private fun List<Int>.toByteArray(): ByteArray = ByteArray(size) { this[it].toByte() }
