package com.wingsheep.network.config

/**
 * Immutable snapshot of the endpoints and links controlled by Firebase Remote
 * Config.
 *
 * A snapshot rather than a set of independent properties: a consumer reads one
 * value, so it can never observe a mixture of an old host and a new port.
 */
data class RemoteSettings(
    val serverIp: String,
    val serverPort: Int,
    val noisePort: Int,
    val apiBaseUrl: String,
    val privacyPolicyUrl: String
) {
    companion object {
        /** Default Noise transport host; also the direct-HTTP fallback host. */
        const val DEFAULT_SERVER_IP = "139.99.88.23"
        const val DEFAULT_SERVER_PORT = 3000
        const val DEFAULT_NOISE_PORT = 9443
        const val DEFAULT_PRIVACY_POLICY_URL = "https://www.dream-team.works/freesky/privacy-policy"

        /** Built-in defaults, used until (or unless) the remote fetch succeeds. */
        val DEFAULT = RemoteSettings(
            serverIp = DEFAULT_SERVER_IP,
            serverPort = DEFAULT_SERVER_PORT,
            noisePort = DEFAULT_NOISE_PORT,
            apiBaseUrl = "http://$DEFAULT_SERVER_IP:$DEFAULT_SERVER_PORT",
            privacyPolicyUrl = DEFAULT_PRIVACY_POLICY_URL
        )
    }
}
