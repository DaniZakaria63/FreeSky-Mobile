package com.wingsheep.freesky.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wingsheep.freesky.platform.readAppEnvironment
import com.wingsheep.freesky.ui.theme.TerminalAccent
import com.wingsheep.freesky.ui.theme.TerminalBackground
import com.wingsheep.freesky.ui.theme.TerminalBorder
import com.wingsheep.freesky.ui.theme.TerminalDim
import com.wingsheep.freesky.ui.theme.TerminalPrimary
import com.wingsheep.freesky.ui.theme.TerminalTitle
import com.wingsheep.freesky.ui.theme.TerminalWarning

private val DialogShape = RoundedCornerShape(6.dp)

/**
 * About dialog: build, device and policy facts.
 *
 * The privacy-policy URL comes from [AppInfoViewModel] rather than a module-level
 * Compose state holder, so the dialog owns no global mutable state and a remote
 * override recomposes it automatically.
 */
@Composable
fun AppInfoDialog(
    onDismiss: () -> Unit,
    viewModel: AppInfoViewModel = viewModel()
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val environment = remember(context) { readAppEnvironment(context) }
    val privacyPolicyUrl by viewModel.privacyPolicyUrl.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(TerminalBackground, DialogShape)
                .border(1.dp, TerminalBorder, DialogShape)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            TerminalLabel("App Info", TerminalTitle, size = 14.sp, bold = true)
            SectionDivider()

            TerminalLabel(environment.appName, TerminalPrimary, size = 14.sp, bold = true)
            TerminalLabel("version: ${environment.versionName} (${environment.versionCode})")
            TerminalLabel("package: ${environment.packageName}")

            TerminalSection("Device Info", TerminalAccent) {
                TerminalLabel(
                    "${environment.manufacturer} ${environment.model}",
                    TerminalPrimary,
                    size = 12.sp
                )
                TerminalLabel("Android ${environment.osVersion} (API ${environment.sdkInt})")
                TerminalLabel("build: ${environment.buildDisplay}")
            }

            TerminalSection("Privacy Policy", TerminalWarning) {
                TerminalLabel(
                    text = "> $privacyPolicyUrl",
                    color = TerminalAccent,
                    modifier = Modifier
                        .clickableTerminal { uriHandler.openUri(privacyPolicyUrl) }
                        .padding(vertical = 4.dp)
                )
            }

            SectionDivider()
            TerminalLabel(
                text = "[ close ]",
                color = TerminalAccent,
                size = 12.sp,
                bold = true,
                modifier = Modifier
                    .clickableTerminal(onClick = onDismiss)
                    .padding(vertical = 6.dp)
            )
        }
    }
}

/** A titled block, preceded by the divider that separates dialog sections. */
@Composable
private fun TerminalSection(
    title: String,
    color: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    SectionDivider()
    TerminalLabel(title, color, size = 12.sp, bold = true)
    Spacer(Modifier.height(6.dp))
    Column(content = content)
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = TerminalBorder, thickness = 1.dp)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun TerminalLabel(
    text: String,
    color: Color = TerminalDim,
    size: TextUnit = 11.sp,
    bold: Boolean = false,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = color,
        fontSize = size,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
    )
}

/** Terminal-style tap target: no ripple, no indication. */
@Composable
private fun Modifier.clickableTerminal(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}
