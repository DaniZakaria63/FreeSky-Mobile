package com.wingsheep.network.tor

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.torproject.jni.TorService
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes the `torrc` file Tor loads at start-up.
 *
 * Kept apart from [TorProxyManager]: this is plain file I/O, and any change only
 * takes effect the next time the daemon starts.
 */
@Singleton
class TorConfigStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** Replaces the `torrc`; applies to the next Tor start. */
    @Synchronized
    fun write(config: TorConfig) {
        val torrc = TorService.getTorrc(context)
        torrc.parentFile?.mkdirs()
        torrc.writeText(config.toTorrc())
        Timber.d("torrc written to %s", torrc.absolutePath)
    }

    /** Removes the `torrc` so Tor falls back to the defaults TorService ships. */
    @Synchronized
    fun clear() {
        val torrc = TorService.getTorrc(context)
        if (torrc.exists() && torrc.delete()) {
            Timber.d("torrc removed")
        }
    }
}
