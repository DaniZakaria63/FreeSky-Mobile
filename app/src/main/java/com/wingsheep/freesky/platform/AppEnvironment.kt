package com.wingsheep.freesky.platform

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build

/** Static facts about the running build and device, shown in the About dialog. */
data class AppEnvironment(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: String,
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val sdkInt: Int,
    val buildDisplay: String
)

/** Reads the facts above on[cubism-mcp] WARNING 连接 Cubism Editor 失败: [Errno 111] Connect call failed ('127.0.0.1', 22033)ce; cheap enough to call from a `remember` block. */
fun readAppEnvironment(context: Context): AppEnvironment {
    val packageManager = context.packageManager
    val packageName = context.packageName
    val packageInfo = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()

    return AppEnvironment(
        appName = packageInfo?.applicationInfo?.loadLabel(packageManager)?.toString()
            ?: FALLBACK_APP_NAME,
        packageName = packageName,
        versionName = packageInfo?.versionName ?: FALLBACK_VERSION,
        versionCode = packageInfo?.versionCodeCompat() ?: FALLBACK_VERSION_CODE,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        osVersion = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        buildDisplay = Build.DISPLAY
    )
}

private const val FALLBACK_APP_NAME = "Freesky"
private const val FALLBACK_VERSION = "1.0"
private const val FALLBACK_VERSION_CODE = "1"

private fun PackageInfo.versionCodeCompat(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode.toString()
    } else {
        @Suppress("DEPRECATION")
        versionCode.toString()
    }
