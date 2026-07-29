package com.wingsheep.network.rotation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wingsheep.network.api.ApiClient
import com.wingsheep.network.model.RegisterResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [RegistrationHandler].
 *
 * These run on an Android device/emulator because [computeApkCertSha1]
 * requires [android.content.pm.PackageManager].
 *
 * Run with:
 *   ./gradlew :network:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class RegistrationHandlerTest {

    private val fakeApiClient = object : ApiClient {
        override suspend fun register(
            pkDev: ByteArray,
            apkCertSha1: String
        ): RegisterResponse {
            throw UnsupportedOperationException("Not used in this test")
        }

        override suspend fun fetchServerNoisePk(): ByteArray? {
            throw UnsupportedOperationException("Not used in this test")
        }
    }

    @Test
    fun computeApkCertSha1_returns40CharHexString() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val handler = RegistrationHandler(
            apiClient = fakeApiClient,
            context = context
        )

        val sha1 = handler.computeApkCertSha1()

        assertEquals(40, sha1.length)
        assertTrue(
            "Expected 40-char uppercase hex, got: $sha1",
            sha1.matches(Regex("^[0-9A-F]{40}$"))
        )
    }

    @Test
    fun computeApkCertSha1_matchesExpectedDebugKeystore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val handler = RegistrationHandler(
            apiClient = fakeApiClient,
            context = context
        )

        val sha1 = handler.computeApkCertSha1()

        // Debug keystore SHA-1 on this machine
        assertEquals("C0F3D6B52B5DC1433D89F1625A672AF8CBA263B2", sha1)
    }
}
