package com.wingsheep.encrypt.mls

import android.content.Context
import com.wingsheep.encrypt.CryptoError
import com.wingsheep.encrypt.CryptoResult
import java.io.File

/**
 * Layer 3 — MLS (Messaging Layer Security) Group Encryption.
 *
 * Provides group-level encryption where all members share a ratcheting key.
 * Posts are encrypted once with the group key; any member can decrypt.
 *
 * **Dependency note**: The guide specifies `space.zeroxv6:kotlin-mls:1.1.0`,
 * which is not currently published to Maven Central.  The methods below
 * return [CryptoResult.Error] with [CryptoError.MLS_GROUP_NOT_INITIALIZED]
 * until the library is available and the TODOs are implemented.
 *
 * Reference: `docs/android-encryption-guide.md` §4
 */
class MlsGroupManager(private val context: Context) {

    companion object {
        private const val GROUP_STATE_FILE = "mls_group_state.bin"
        private const val CIPHER_SUITE = "MLS_128_X25519_AES128GCM_SHA256_Ed25519"
    }

    // TODO: Replace with actual kotlin-mls types once the dependency is available
    // private var mlsGroup: MlsGroup? = null
    // private var credentialBundle: CredentialBundle? = null
    private var initialized: Boolean = false

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Initialise the MLS group from server-provided key material.
     *
     * Called after registration returns `encrypted_sk_comm`:
     * 1. ECIES-decrypt `encrypted_sk_comm` → `sk_comm`
     * 2. Call this method with `sk_comm` and the device public key
     *
     * @param groupStateBytes The decrypted group key material (`sk_comm`).
     * @param identityKeyBytes The device public key (X.509 encoded).
     */
    fun initFromKeyMaterial(
        groupStateBytes: ByteArray,
        identityKeyBytes: ByteArray
    ): CryptoResult<Unit> {
        // TODO: Implement with kotlin-mls
        // val credentialBundle = CredentialBundle.new(
        //     identityKey = KeyPackage(CIPHER_SUITE, identityKeyBytes),
        //     credentialType = CredentialType.Basic
        // )
        // val group = MlsGroup.new(
        //     version = MlsProtocolVersion.Mls10,
        //     cipherSuite = CIPHER_SUITE,
        //     identity = credentialBundle
        // )
        // this.mlsGroup = group
        // this.credentialBundle = credentialBundle
        // persistGroupState()
        // this.initialized = true
        return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
    }

    /**
     * Restore group state from local storage (app restart).
     */
    fun restoreFromStorage(): CryptoResult<Unit> {
        val bytes = readGroupState() ?: return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
        // TODO: Implement with kotlin-mls
        // val group = MlsGroup.deserialize(bytes)
        // this.mlsGroup = group
        // this.initialized = true
        return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
    }

    /**
     * Encrypt a post plaintext.
     * Returns ciphertext that only MLS members can decrypt.
     */
    fun encryptPost(plaintext: ByteArray): CryptoResult<ByteArray> {
        // TODO: Implement with kotlin-mls
        // val group = requireGroup()
        // return CryptoResult.Success(group.encrypt(plaintext))
        return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
    }

    /**
     * Decrypt a post ciphertext.
     * Returns null if decryption fails (wrong epoch, tampered, etc).
     */
    fun decryptPost(ciphertext: ByteArray): CryptoResult<ByteArray> {
        // TODO: Implement with kotlin-mls
        // val group = requireGroup()
        // return try {
        //     CryptoResult.Success(group.decrypt(ciphertext))
        // } catch (e: Exception) {
        //     CryptoResult.Error(CryptoError.MLS_DECRYPT_FAILED)
        // }
        return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
    }

    /**
     * Process an MLS Commit message (member added/kicked).
     * The group ratchets to a new epoch key.
     */
    fun processCommitMessage(commitBytes: ByteArray): CryptoResult<Unit> {
        // TODO: Implement with kotlin-mls
        // val group = requireGroup()
        // group.processCommit(commitBytes)
        // persistGroupState()
        return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
    }

    /**
     * Process an MLS Welcome message (when joining an existing group).
     */
    fun processWelcomeMessage(welcomeBytes: ByteArray): CryptoResult<Unit> {
        // TODO: Implement with kotlin-mls
        // val group = MlsGroup.fromWelcome(
        //     welcome = Welcome.deserialize(welcomeBytes),
        //     keyPackage = requireCredentialBundle().getKeyPackage()
        // )
        // this.mlsGroup = group
        // this.initialized = true
        // persistGroupState()
        return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
    }

    /** Returns the current MLS epoch number. */
    fun getCurrentEpoch(): CryptoResult<Long> {
        // TODO: Implement with kotlin-mls
        // return CryptoResult.Success(requireGroup().epoch())
        return CryptoResult.Error(CryptoError.MLS_GROUP_NOT_INITIALIZED)
    }

    // ── Private helpers ─────────────────────────────────────────────

    private fun persistGroupState() {
        // TODO: Implement with kotlin-mls
        // val group = mlsGroup ?: return
        // val bytes = group.serialize()
        // context.filesDir.resolve(GROUP_STATE_FILE).writeBytes(bytes)
    }

    private fun readGroupState(): ByteArray? {
        val file = File(context.filesDir, GROUP_STATE_FILE)
        return if (file.exists()) file.readBytes() else null
    }
}
