package com.wingsheep.freesky.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val TerminalColorScheme = darkColorScheme(
    primary = TerminalAccent,
    onPrimary = TerminalBackground,
    secondary = TerminalTitle,
    onSecondary = TerminalBackground,
    tertiary = TerminalSuccess,
    onTertiary = TerminalBackground,
    background = TerminalBackground,
    onBackground = TerminalPrimary,
    surface = TerminalSurface,
    onSurface = TerminalPrimary,
    surfaceVariant = Color(0xFF3B4252),
    onSurfaceVariant = TerminalDim,
    outline = TerminalBorder,
    inverseOnSurface = TerminalBackground,
    inverseSurface = TerminalPrimary
)

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun FreeskyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    terminalMode: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        terminalMode -> TerminalColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = if (terminalMode) TerminalTypography else androidx.compose.material3.Typography(),
        content = content
    )
}