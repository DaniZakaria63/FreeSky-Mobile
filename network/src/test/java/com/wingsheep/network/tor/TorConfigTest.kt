package com.wingsheep.network.tor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorConfigTest {

    @Test
    fun `defaults render the SOCKS port and a log line`() {
        val torrc = TorConfig().toTorrc()

        assertEquals(
            """
            SOCKSPort 9050
            Log notice stdout
            """.trimIndent() + "\n",
            torrc
        )
    }

    @Test
    fun `control port and bridges are rendered only when configured`() {
        val torrc = TorConfig(
            socksPort = 9150,
            controlPort = 9151,
            bridges = listOf("obfs4 1.2.3.4:443 FINGERPRINT"),
            logLevel = "info",
            customOptions = mapOf("ClientOnly" to "1")
        ).toTorrc()

        assertTrue(torrc.contains("SOCKSPort 9150"))
        assertTrue(torrc.contains("ControlPort 9151"))
        assertTrue(torrc.contains("UseBridges 1"))
        assertTrue(torrc.contains("Bridge obfs4 1.2.3.4:443 FINGERPRINT"))
        assertTrue(torrc.contains("Log info stdout"))
        assertTrue(torrc.contains("ClientOnly 1"))

        val minimal = TorConfig().toTorrc()
        assertFalse(minimal.contains("ControlPort"))
        assertFalse(minimal.contains("UseBridges"))
    }
}
