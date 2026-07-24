package com.wingsheep.network.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RegisterRequestTest {

    @Test
    fun fromBytes_createsRequestWithApkCertSha1() {
        val pkDev = byteArrayOf(0x04, 0x01, 0x02, 0x03, 0xFF.toByte())
        val apkCertSha1 = "C0F3D6B52B5DC1433D89F1625A672AF8CBA263B2"

        val request = RegisterRequest.fromBytes(pkDev, apkCertSha1)

        assertEquals(listOf(4, 1, 2, 3, 255), request.pk_dev)
        assertEquals(apkCertSha1, request.apk_cert_sha1)
    }

    @Test
    fun fromBytes_preservesAll65Sec1Bytes() {
        val pkDev = ByteArray(65) { it.toByte() }
        val apkCertSha1 = "AABBCCDDEEFF00112233445566778899AABBCCDDEE"

        val request = RegisterRequest.fromBytes(pkDev, apkCertSha1)

        assertEquals(65, request.pk_dev.size)
        assertEquals(apkCertSha1, request.apk_cert_sha1)
    }
}
