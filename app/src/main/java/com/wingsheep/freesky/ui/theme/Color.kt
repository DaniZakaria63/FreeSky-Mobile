package com.wingsheep.freesky.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Modern terminal palette (Nord-inspired)
val TerminalBackground = Color(0xFF1E2430)
val TerminalSurface = Color(0xFF283044)
val TerminalPrimary = Color(0xFFECEFF4)
val TerminalAccent = Color(0xFF88C0D0)
val TerminalDim = Color(0xFF616E88)
val TerminalBorder = Color(0xFF4C566A)
val TerminalSuccess = Color(0xFFA3BE8C)
val TerminalError = Color(0xFFBF616A)
val TerminalWarning = Color(0xFFEBCB8B)
val TerminalCursor = Color(0xFFA3BE8C)
val TerminalTitle = Color(0xFF8FBCBB)

// 16-color ANSI palette (Nord-adjusted)
private val TERMINAL_16 = listOf(
    Color(0xFF3B4252), //  0: black     (nord1)
    Color(0xFFBF616A), //  1: red       (nord11)
    Color(0xFFA3BE8C), //  2: green     (nord14)
    Color(0xFFEBCB8B), //  3: yellow    (nord13)
    Color(0xFF81A1C1), //  4: blue      (nord9)
    Color(0xFFB48EAD), //  5: magenta   (nord15)
    Color(0xFF88C0D0), //  6: cyan      (nord8)
    Color(0xFFD8DEE9), //  7: white     (nord4)
    Color(0xFF4C566A), //  8: bright black  (nord3)
    Color(0xFFBF616A), //  9: bright red
    Color(0xFFA3BE8C), // 10: bright green
    Color(0xFFEBCB8B), // 11: bright yellow
    Color(0xFF81A1C1), // 12: bright blue
    Color(0xFFB48EAD), // 13: bright magenta
    Color(0xFF88C0D0), // 14: bright cyan
    Color(0xFFECEFF4), // 15: bright white  (nord6)
)

fun terminalColor(index: Int): Color = TERMINAL_16[index.coerceIn(0, 15)]