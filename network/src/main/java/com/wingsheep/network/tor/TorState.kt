package com.wingsheep.network.tor

/**
 * Observable state of the embedded Tor daemon.
 *
 * HTTP does not depend on it: [TorProxyManager.httpClient] falls back to a direct
 * connection whenever a circuit is not up, so a failure here degrades rather than
 * breaks the app.
 */
sealed interface TorState {

    /** No daemon is running; HTTP goes out directly. */
    data object Stopped : TorState

    /** Binding requested; Tor has not reported a circuit yet. */
    data object Starting : TorState

    /** A circuit is up and the SOCKS proxy accepts connections on [socksPort]. */
    data class Ready(val socksPort: Int) : TorState

    /** The daemon refused to start or died unexpectedly. */
    data class Failed(val message: String) : TorState
}
