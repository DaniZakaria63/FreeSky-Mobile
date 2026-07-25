package com.wingsheep.freesky.ui.registration

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wingsheep.freesky.model.RegistrationUiState
import com.wingsheep.freesky.ui.theme.TerminalAccent
import com.wingsheep.freesky.ui.theme.TerminalBackground
import com.wingsheep.freesky.ui.theme.TerminalBorder
import com.wingsheep.freesky.ui.theme.TerminalCursor
import com.wingsheep.freesky.ui.theme.TerminalDim
import com.wingsheep.freesky.ui.theme.TerminalError
import com.wingsheep.freesky.ui.theme.TerminalPrimary
import com.wingsheep.freesky.ui.theme.TerminalSuccess
import com.wingsheep.freesky.ui.theme.TerminalSurface
import com.wingsheep.freesky.ui.theme.TerminalTitle

@Composable
fun RegistrationScreen(
    viewModel: RegistrationViewModel = viewModel(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by viewModel.state.collectAsState()
    val consentGiven by viewModel.consentGiven.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBackground)
            .padding(contentPadding)
    ) {
        TerminalPanel(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TerminalHeader()
                TerminalStateContent(state = state, consentGiven = consentGiven, onToggleConsent = {
                    viewModel.toggleConsent()
                }, onRegister = {
                    viewModel.register()
                })
            }
        }
        TerminalStatusBar(
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun TerminalStateContent(
    state: RegistrationUiState,
    consentGiven: Boolean,
    onToggleConsent: () -> Unit,
    onRegister: () -> Unit
) {
    AnimatedVisibility(visible = state is RegistrationUiState.Checking) {
        TerminalStateChecking()
    }

    AnimatedVisibility(visible = state is RegistrationUiState.NeedsRegistration) {
        TerminalStateNeedsRegistration(
            consentGiven = consentGiven,
            onToggleConsent = onToggleConsent,
            onRegister = onRegister
        )
    }

    AnimatedVisibility(visible = state is RegistrationUiState.Registered) {
        val s = state as RegistrationUiState.Registered
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TerminalText("$ [check] registration complete", color = TerminalSuccess)
            TerminalText("  welcome, ${s.name}", color = TerminalPrimary)
            TerminalText("  device color index: ${s.color}", color = TerminalDim)
        }
    }

    AnimatedVisibility(visible = state is RegistrationUiState.Error) {
        val s = state as RegistrationUiState.Error
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TerminalText("! error: ${s.message}", color = TerminalError)
            TerminalPromptButton(
                text = "RETRY",
                enabled = true,
                onClick = onRegister,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun TerminalStateChecking() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TerminalText("> connecting to server...", color = TerminalAccent)
        TerminalText("> generating device keys...", color = TerminalDim)
        TerminalText("> [ok] keys generated", color = TerminalSuccess)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                color = TerminalAccent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp)
            )
            TerminalText("  processing...", color = TerminalDim, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun TerminalStateNeedsRegistration(
    consentGiven: Boolean,
    onToggleConsent: () -> Unit,
    onRegister: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TerminalText("> device ready for registration", color = TerminalPrimary)
        TerminalText("> awaiting user consent", color = TerminalDim)

        Spacer(modifier = Modifier.height(6.dp))

        TerminalDisclosurePanel()

        Spacer(modifier = Modifier.height(4.dp))

        TerminalCheckbox(
            checked = consentGiven,
            onToggle = onToggleConsent,
            label = "I have read and understood the above notice"
        )

        Spacer(modifier = Modifier.height(10.dp))

        TerminalPromptButton(
            text = "REGISTER DEVICE",
            enabled = consentGiven,
            onClick = onRegister
        )
    }
}

@Composable
fun TerminalHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalSurface)
            .border(1.dp, TerminalBorder)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TerminalText("freesky", color = TerminalTitle, fontSize = 16.sp, lineHeight = 22.sp)
        Text(
            text = "  ·  register",
            color = TerminalDim,
            fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        TerminalText("[secure]", color = TerminalSuccess, fontSize = 10.sp, lineHeight = 14.sp)
    }
}

@Composable
fun TerminalDisclosurePanel() {
    val accentColor = TerminalAccent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalSurface.copy(alpha = 0.5f))
            .border(1.dp, TerminalBorder)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            TerminalText("┌─ ENCRYPTION NOTICE ───────────────────────────", color = TerminalDim, fontSize = 10.sp, lineHeight = 14.sp)
            TerminalText("│", color = TerminalBorder, fontSize = 12.sp, lineHeight = 16.sp)
            TerminalText("│  This application is fully encrypted", color = TerminalPrimary, fontSize = 11.sp, lineHeight = 16.sp)
            TerminalText("│  end-to-end. The server cannot read your", color = TerminalPrimary, fontSize = 11.sp, lineHeight = 16.sp)
            TerminalText("│  data. All actions are your sole", color = TerminalPrimary, fontSize = 11.sp, lineHeight = 16.sp)
            TerminalText("│  responsibility.", color = TerminalPrimary, fontSize = 11.sp, lineHeight = 16.sp)
            TerminalText("│", color = TerminalBorder, fontSize = 1.sp, lineHeight = 14.sp)
            TerminalText("│  The developer owns:", color = TerminalPrimary, fontSize = 11.sp, lineHeight = 16.sp)
            TerminalText("│    · the app", color = TerminalPrimary, fontSize = 11.sp, lineHeight = 16.sp)
            TerminalText("│    · source code", color = TerminalPrimary, fontSize = 11.sp, lineHeight = 16.sp)
            TerminalText("│    · server service & database (to keep", color = TerminalPrimary, fontSize = 11.sp, lineHeight = 16.sp)
            TerminalText("│      the app alive)", color = TerminalPrimary, fontSize = 11.sp, lineHeight = 16.sp)
            TerminalText("│", color = TerminalBorder, fontSize = 1.sp, lineHeight = 14.sp)
            TerminalText("│  All legal liability remains with you.", color = TerminalPrimary, fontSize = 11.sp, lineHeight = 16.sp)
            TerminalText("│", color = TerminalBorder, fontSize = 1.sp, lineHeight = 14.sp)
            TerminalText("└────────────────────────────────────────────", color = TerminalDim, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
fun TerminalCheckbox(
    checked: Boolean,
    onToggle: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val checkMark = if (checked) "x" else " "
    val checkColor = if (checked) TerminalSuccess else TerminalDim
    val labelColor = if (checked) TerminalPrimary else TerminalDim
    val labelAlpha = if (checked) 1f else 0.5f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggle
            )
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .background(
                if (checked) TerminalSurface else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "[${checkMark}]",
            color = checkColor,
            fontSize = 13.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Text(
            text = " ${label}",
            color = labelColor,
            fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier = Modifier.alpha(labelAlpha)
        )
    }
}

@Composable
fun TerminalPromptButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_blink"
    )

    val bgColor = if (enabled) TerminalAccent.copy(alpha = 0.1f) else TerminalSurface
    val borderColor = if (enabled) TerminalAccent else TerminalBorder
    val textColor = if (enabled) TerminalAccent else TerminalDim

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor, shape = RoundedCornerShape(4.dp))
            .border(1.dp, borderColor, shape = RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TerminalText("▸", color = textColor, fontSize = 13.sp, lineHeight = 18.sp)
        TerminalText(text, color = textColor, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        if (enabled) {
            Text(
                text = "█",
                color = TerminalCursor,
                fontSize = 13.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.alpha(cursorAlpha)
            )
        }
    }
}

@Composable
fun TerminalPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(TerminalBackground)
            .border(1.dp, TerminalBorder, shape = RoundedCornerShape(6.dp))
    ) {
        content()
    }
}

@Composable
fun TerminalStatusBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TerminalSurface)
            .border(1.dp, TerminalBorder, shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = 0.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TerminalText("●", color = TerminalSuccess, fontSize = 8.sp, lineHeight = 10.sp)
            TerminalText("freesky v1.0", color = TerminalDim, fontSize = 10.sp, lineHeight = 14.sp)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TerminalText("  connected", color = TerminalSuccess, fontSize = 10.sp, lineHeight = 14.sp)
            TerminalText("  /? help", color = TerminalDim, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
fun TerminalText(
    text: String,
    color: Color = TerminalPrimary,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 19.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        letterSpacing = 0.3.sp,
        modifier = modifier
    )
}