package com.wingsheep.network.noise

import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.security.PrivateKey

/** Transport cipher states produced by a completed handshake. */
internal class NoiseSessionKeys(
    val send: NoiseCipherState,
    val receive: NoiseCipherState
)

/**
 * Initiator half of `Noise_NK_P256_ChaChaPoly_BLAKE2s`.
 *
 * NK authenticates the responder only: the initiator proves nothing about itself,
 * which is why the server identifies clients by `pk_dev` rather than by a Noise
 * static key. Both messages carry an empty payload and are therefore exactly 81
 * bytes on the wire (65-byte SEC1 key + 16-byte AEAD tag); they are read with an
 * exact-length read so a split TCP segment cannot corrupt the parser.
 *
 * Reference: PROTOCOL_SYNC.md §3.2 — Noise NK Handshake
 */
internal object NoiseHandshake {

    private const val PROTOCOL_NAME = "Noise_NK_P256_ChaChaPoly_BLAKE2s"
    private const val PUBLIC_KEY_BYTES = 65
    private const val AUTH_TAG_BYTES = 16
    private const val MESSAGE_BYTES = PUBLIC_KEY_BYTES + AUTH_TAG_BYTES

    private val EMPTY = ByteArray(0)

    fun perform(
        input: InputStream,
        output: OutputStream,
        devicePrivateKey: PrivateKey,
        serverStaticKey: ByteArray,
        prologue: ByteArray
    ): NoiseSessionKeys {
        require(serverStaticKey.size == PUBLIC_KEY_BYTES) {
            "server Noise key must be $PUBLIC_KEY_BYTES-byte SEC1, was ${serverStaticKey.size}"
        }

        // h = HASH(protocol name), then the prologue and the responder static key
        // (known in advance in NK).
        var symmetric = NoiseSymmetry.initialize(PROTOCOL_NAME)
        trace("h_0 (protocol)", symmetric)
        symmetric = symmetric.mixHash(prologue)
        trace("h_1 (prologue)", symmetric)
        symmetric = symmetric.mixHash(serverStaticKey)
        trace("h_2 (rs)", symmetric)

        // msg1 = e, es
        val ephemeral = generateP256KeyPair()
        val ephemeralPublic = p256PublicKeyBytes(ephemeral)
        symmetric = symmetric.mixHash(ephemeralPublic)
        trace("h_3 (re)", symmetric)
        symmetric = symmetric.mixKey(ecdh(ephemeral.private, serverStaticKey))
        trace("ck after es", symmetric)

        // Every Noise message ends with EncryptAndHash(payload); with an empty
        // payload that is just the AEAD tag.
        val (afterMsg1, tag) = symmetric.encryptAndHash(EMPTY)
        output.write(ephemeralPublic + tag)
        output.flush()
        Timber.d("Noise: sent msg1 (%d bytes)", MESSAGE_BYTES)

        // msg2 = e, ee
        val response = input.readExactly(MESSAGE_BYTES)
        val responderEphemeral = response.copyOfRange(0, PUBLIC_KEY_BYTES)
        val responderTag = response.copyOfRange(PUBLIC_KEY_BYTES, MESSAGE_BYTES)

        symmetric = afterMsg1
            .mixHash(responderEphemeral)
            .mixKey(ecdh(ephemeral.private, responderEphemeral))
        trace("ck after ee", symmetric)

        val authenticated = symmetric.decryptAndHash(responderTag)
            ?: throw SecurityException("Noise msg2 failed authentication")
        symmetric = authenticated.first

        val (send, receive) = symmetric.split()
        Timber.i("Noise NK handshake complete")
        return NoiseSessionKeys(send = send, receive = receive)
    }

    /**
     * Handshake trace, enabled by filtering logcat for `NOISE_DEBUG`.
     *
     * Worth keeping: a mismatched prologue or static key is invisible from the
     * outside, and these hashes are the only way to diff the two sides' transcripts.
     */
    private fun trace(label: String, symmetric: NoiseSymmetry) {
        Timber.d(
            "NOISE_DEBUG %-14s h=%s ck=%s",
            label,
            symmetric.h.toHex(),
            symmetric.ck.toHex()
        )
    }
}
