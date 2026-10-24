package com.wingsheep.encrypt.mls

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Persists [MlsGroupState] in the app's private storage.
 *
 * The state is written to a temporary file, fsynced and published with an atomic
 * rename, so a crash can never leave a key on disk paired with the wrong epoch
 * (which the previous two-file layout allowed). The pre-v2 layout is read once for
 * migration and then deleted, so a retired key does not linger on disk.
 */
internal class MlsGroupStateStore(private val directory: File) {

    /** Current state, falling back to the migrated pre-v2 layout. */
    fun read(): MlsGroupState? = readCurrentState() ?: readLegacyState()

    @Synchronized
    fun write(state: MlsGroupState) {
        val tempFile = File(directory, TEMP_FILE)
        FileOutputStream(tempFile).use { out ->
            out.write(state.encode())
            out.flush()
            out.fd.sync()
        }
        // rename(2) is atomic within one filesystem: a reader observes either the old
        // state or the new one, never a mixture.
        if (!tempFile.renameTo(File(directory, STATE_FILE))) {
            throw IOException("Unable to publish MLS group state")
        }
        deleteLegacyFiles()
    }

    private fun readCurrentState(): MlsGroupState? {
        val file = File(directory, STATE_FILE)
        if (!file.exists()) return null
        return MlsGroupState.decode(file.readBytes())
    }

    /** Pre-v2 layout: the key and the epoch in two separate files. */
    private fun readLegacyState(): MlsGroupState? {
        val keyFile = File(directory, LEGACY_KEY_FILE)
        if (!keyFile.exists()) return null
        val key = keyFile.readBytes()
        if (key.size != MlsGroupState.KEY_LENGTH_BYTES) return null
        return MlsGroupState(key, readLegacyEpoch())
    }

    private fun readLegacyEpoch(): Long {
        val file = File(directory, LEGACY_EPOCH_FILE)
        if (!file.exists()) return 0L
        val bytes = file.readBytes()
        if (bytes.size < Long.SIZE_BYTES) return 0L
        return ByteBuffer.wrap(bytes).long
    }

    private fun deleteLegacyFiles() {
        runCatching { File(directory, LEGACY_KEY_FILE).delete() }
        runCatching { File(directory, LEGACY_EPOCH_FILE).delete() }
    }

    private companion object {
        const val STATE_FILE = "mls_group_state_v2.bin"
        const val TEMP_FILE = "mls_group_state_v2.bin.tmp"
        const val LEGACY_KEY_FILE = "mls_group_state.bin"
        const val LEGACY_EPOCH_FILE = "mls_group_epoch.bin"
    }
}
