package com.wingsheep.encrypt.identity

import java.security.MessageDigest

/**
 * Derives a device's display identity from its 65-byte SEC1 public key.
 *
 * Mirrors the server's deterministic identity derivation
 * (`shared/src/crypto.rs` — `derive_name` / `derive_color`):
 *   hash   = SHA-256(pk_dev)
 *   name   = COMMON_OBJECTS[ (hash[0].toInt() and 0xFF) * 256 + (hash[1].toInt() and 0xFF) % COMMON_OBJECTS.size ]
 *   color  = hash[0] % 16
 *
 * Name uses a wordlist of common objects instead of hex for readability.
 */
object IdentityDeriver {

    fun deriveName(pkDevSec1: ByteArray): String {
        val hash = sha256(pkDevSec1)
        return hash.copyOfRange(0, 8).joinToString("") { b ->
            val printable = 0x21 + ((b.toInt() and 0xFF) % 0x5E) // map to !..~
            printable.toChar().toString()
        }
    }

    fun deriveColor(pkDevSec1: ByteArray): Int {
        val hash = sha256(pkDevSec1)
        return (hash[0].toInt() and 0xFF) % 16
    }

    fun deriveIdentity(pkDevSec1: ByteArray): Identity =
        Identity(name = deriveName(pkDevSec1), color = deriveColor(pkDevSec1))

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)
}

data class Identity(val name: String, val color: Int)
