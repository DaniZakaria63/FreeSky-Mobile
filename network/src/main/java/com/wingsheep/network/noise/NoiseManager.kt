package com.wingsheep.network.noise

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class NoiseManager(
    private val host: String,
    private val port: Int,
    private val devicePrivateKey: java.security.PrivateKey,
    private val devicePublicKeySec1: ByteArray,
    private val serverNoisePk: ByteArray,
    private val prologue: ByteArray
) {

    private val gson = Gson()
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var sendCs: NoiseCipherState? = null
    private var recvCs: NoiseCipherState? = null

    val isConnected: Boolean get() = socket?.isConnected == true && !socket!!.isClosed

    suspend fun establishSession() = withContext(Dispatchers.IO) {
        Timber.i("NoiseManager: connecting to $host:$port")
        val sock = Socket(host, port)
        sock.soTimeout = 30000
        socket = sock
        outputStream = sock.getOutputStream()
        inputStream = sock.getInputStream()
        Timber.i("NoiseManager: TCP connected, starting IK handshake")
        doHandshake()
        Timber.i("NoiseManager: Noise IK handshake complete")
    }

    private fun doHandshake() {
        val protocolName = "Noise_IK_P256_ChaChaPoly_BLAKE2s"
        var sym = NoiseSymmetry.initialize(protocolName)
            .mixHash(prologue)

        val ephemeral = generateP256KeyPair()
        val epkBytes = p256PublicKeyBytes(ephemeral)

        sym = sym.mixHash(serverNoisePk)

        sym = sym.mixHash(epkBytes)

        var temp = ecdh(ephemeral.private, serverNoisePk)
        sym = sym.mixKey(temp)

        val (symAfterS, encryptedS) = sym.encryptAndHash(devicePublicKeySec1)

        temp = ecdh(devicePrivateKey, serverNoisePk)
        sym = symAfterS.mixKey(temp)

        val (symAfterPayload, encryptedPayload) = sym.encryptAndHash(ByteArray(0))

        val msg1 = epkBytes + encryptedS + encryptedPayload
        val out = outputStream!!
        out.write(msg1)
        out.flush()
        Timber.d("NoiseManager: sent msg1 (${msg1.size} bytes)")

        val input = inputStream!!
        val respBuf = ByteArray(4096)
        val n = input.read(respBuf)
        if (n <= 0) throw IllegalStateException("Noise: no response")

        var data = respBuf.copyOf(n)
        if (data.size < 65) throw IllegalStateException("Noise: response too short")

        val reBytes = data.copyOfRange(0, 65)
        data = data.copyOfRange(65, data.size)

        sym = symAfterPayload.mixHash(reBytes)

        temp = ecdh(ephemeral.private, reBytes)
        sym = sym.mixKey(temp)

        temp = ecdh(devicePrivateKey, reBytes)
        sym = sym.mixKey(temp)

        val decryptResult = sym.decryptAndHash(data)
            ?: throw SecurityException("Noise msg2 decrypt failed")
        sym = decryptResult.first

        val (s1, s2) = sym.split()
        sendCs = s1
        recvCs = s2
    }

    suspend fun sendRequest(method: String, body: Map<String, Any?>): String {
        return withContext(Dispatchers.IO) {
            val fullBody = body.toMutableMap().apply { put("method", method) }
            val jsonBytes = gson.toJson(fullBody).toByteArray(Charsets.UTF_8)

            val send = checkNotNull(sendCs) { "Noise session not established" }
            val ciphertext = send.encrypt(jsonBytes, ByteArray(0))

            val out = outputStream!!
            writeLength(out, ciphertext.size)
            out.write(ciphertext)
            out.flush()

            val responseLen = readLength(inputStream!!)
            val encBuf = ByteArray(responseLen)
            readExact(inputStream!!, encBuf)

            val recv = checkNotNull(recvCs) { "Noise session not established" }
            val plaintext = recv.decrypt(encBuf, ByteArray(0))
                ?: throw SecurityException("Noise decrypt failed")
            recvCs = recv

            plaintext.toString(Charsets.UTF_8)
        }
    }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        outputStream = null
        inputStream = null
        sendCs = null
        recvCs = null
    }

    private fun writeLength(out: OutputStream, len: Int) {
        out.write((len shr 8) and 0xFF)
        out.write(len and 0xFF)
    }

    private fun readLength(input: InputStream): Int {
        val buf = ByteArray(2)
        readExact(input, buf)
        return ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)
    }

    private fun readExact(input: InputStream, buf: ByteArray) {
        var offset = 0
        while (offset < buf.size) {
            val n = input.read(buf, offset, buf.size - offset)
            if (n == -1) throw IllegalStateException("TCP read failed: stream closed")
            offset += n
        }
    }
}
