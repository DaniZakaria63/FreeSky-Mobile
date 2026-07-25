package com.wingsheep.encrypt.identity

import java.security.MessageDigest

/**
 * Derives a device's display identity from its 65-byte SEC1 public key.
 *
 * Mirrors the server's deterministic identity derivation
 * (`shared/src/crypto.rs` — `derive_name` / `derive_color`):
 *   hash   = SHA-256(pk_dev)
 *   name   = hex(hash[0..12])
 *   color  = hash[0] % 16
 *
 * Used by the feed UI to show author identity without a lookup round-trip.
 */
object IdentityDeriver {

    fun deriveName(pkDevSec1: ByteArray): String {
        val hash = sha256(pkDevSec1)
        return hash.copyOfRange(0, 12).joinToString("") { "%02x".format(it) }
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
