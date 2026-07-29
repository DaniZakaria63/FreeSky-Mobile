package com.wingsheep.freesky.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wingsheep.freesky.ui.theme.TerminalAccent
import com.wingsheep.freesky.ui.theme.TerminalBackground
import com.wingsheep.freesky.ui.theme.TerminalBorder
import com.wingsheep.freesky.ui.theme.TerminalDim
import com.wingsheep.freesky.ui.theme.TerminalPrimary
import com.wingsheep.freesky.ui.theme.TerminalTitle
import com.wingsheep.freesky.ui.theme.TerminalWarning

private const val PRIVACY_POLICY_URL = "walawe.fun/freesky-privacy-policy"

@Composable
fun AppInfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val appInfo = remember { getAppInfo(context) }
    val deviceInfo = remember { getDeviceInfo() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(TerminalBackground, RoundedCornerShape(6.dp))
                .border(1.dp, TerminalBorder, RoundedCornerShape(6.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "App Info",
                color = TerminalTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = TerminalBorder, thickness = 1.dp)
            Spacer(Modifier.height(10.dp))

            Text(
                text = appInfo.appName,
                color = TerminalPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "version: ${appInfo.versionName} (${appInfo.versionCode})",
                color = TerminalDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "package: ${appInfo.packageName}",
                color = TerminalDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = TerminalBorder, thickness = 1.dp)
            Spacer(Modifier.height(10.dp))

            Text(
                text = "Device Info",
                color = TerminalAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${deviceInfo.manufacturer} ${deviceInfo.model}",
                color = TerminalPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Android ${deviceInfo.osVersion} (API ${deviceInfo.sdkInt})",
                color = TerminalDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "build: ${deviceInfo.buildDisplay}",
                color = TerminalDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = TerminalBorder, thickness = 1.dp)
            Spacer(Modifier.height(10.dp))

            Text(
                text = "Privacy Policy",
                color = TerminalWarning,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "> $PRIVACY_POLICY_URL",
                color = TerminalAccent,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        uriHandler.openUri("https://$PRIVACY_POLICY_URL")
                    }
                    .padding(vertical = 4.dp)
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = TerminalBorder, thickness = 1.dp)
            Spacer(Modifier.height(10.dp))

            Text(
                text = "[ close ]",
                color = TerminalAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                    .padding(vertical = 6.dp)
            )
        }
    }
}

private data class AppInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: String
)

private data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val sdkInt: Int,
    val buildDisplay: String
)

private fun getAppInfo(context: Context): AppInfo {
    val pm = context.packageManager
    val packageName = context.packageName
    return try {
        val info = pm.getPackageInfo(packageName, 0)
        AppInfo(
            appName = info.applicationInfo?.loadLabel(pm)?.toString() ?: "Freesky",
            packageName = packageName,
            versionName = info.versionName ?: "1.0",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toString()
            }
        )
    } catch (_: PackageManager.NameNotFoundException) {
        AppInfo("Freesky", packageName, "1.0", "1")
    }
}

private fun getDeviceInfo(): DeviceInfo {
    return DeviceInfo(
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        osVersion = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        buildDisplay = Build.DISPLAY
    )
}
