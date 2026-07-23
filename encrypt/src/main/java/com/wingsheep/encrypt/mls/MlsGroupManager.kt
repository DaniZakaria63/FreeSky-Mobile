package com.wingsheep.encrypt.mls

import android.content.Context
import com.wingsheep.encrypt.CryptoError
import com.wingsheep.encrypt.CryptoResult
import java.io.File

class MlsGroupManager(private val context: Context) {

    companion object {
        private const val GROUP_STATE_FILE = "mls_group_state.bin"
        private const val CIPHER_SUITE = "MLS_128_X25519_AES128GCM_SHA256_Ed25519"
    }

    private var initialized: Boolean = false

    fun initFromKeyMaterial(
        groupStateBytes: ByteArray,
        identityKeyBytes: ByteArray
    ): CryptoResult<Unit> {
        return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
    }

    fun restoreFromStorage(): CryptoResult<Unit> {
        val bytes = readGroupState() ?: return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
        return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
    }

    fun encryptPost(plaintext: ByteArray): CryptoResult<ByteArray> {
        return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
    }

    fun decryptPost(ciphertext: ByteArray): CryptoResult<ByteArray> {
        return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
    }

    fun processCommitMessage(commitBytes: ByteArray): CryptoResult<Unit> {
        return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
    }

    fun processWelcomeMessage(welcomeBytes: ByteArray): CryptoResult<Unit> {
        return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
    }

    fun getCurrentEpoch(): CryptoResult<Long> {
        return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
    }

    private fun persistGroupState() {
    }

    private fun readGroupState(): ByteArray? {
        val file = File(context.filesDir, GROUP_STATE_FILE)
        return if (file.exists()) file.readBytes() else null
    }
}