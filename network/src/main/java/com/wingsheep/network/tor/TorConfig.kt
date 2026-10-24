package com.wingsheep.network.tor

/**
 * Options written to the `torrc` file read by [org.torproject.jni.TorService] when
 * it starts the daemon.
 */
data class TorConfig(
    val socksPort: Int = DEFAULT_SOCKS_PORT,
    val controlPort: Int? = null,
    val bridges: List<String> = emptyList(),
    val logLevel: String = "notice",
    val customOptions: Map<String, String> = emptyMap()
) {

    /** Renders this configuration as `torrc` lines. */
    fun toTorrc(): String = buildString {
        appendLine("SOCKSPort $socksPort")
        controlPort?.let { appendLine("ControlPort $it") }
        if (bridges.isNotEmpty()) {
            appendLine("UseBridges 1")
            bridges.forEach { appendLine("Bridge $it") }
        }
        appendLine("Log $logLevel stdout")
        customOptions.forEach { (key, value) -> appendLine("$key $value") }
    }

    companion object {
        const val PROXY_HOST = "127.0.0.1"
        const val DEFAULT_SOCKS_PORT = 9050
    }
}
