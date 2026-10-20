package com.moyue.reader.ui.reader

import androidx.compose.ui.graphics.Color
import com.moyue.reader.data.ReaderTheme

data class ReaderPalette(
    val background: Color,
    val backgroundAlt: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color,
    val isLight: Boolean
)

fun ReaderTheme.palette(): ReaderPalette = when (this) {
    ReaderTheme.PAPER -> ReaderPalette(
        background = Color(0xFFF8F4EC),
        backgroundAlt = Color(0xFFF1E9DA),
        text = Color(0xFF2B2620),
        secondary = Color(0xFF8A8173),
        accent = Color(0xFFB5644A),
        isLight = true
    )
    ReaderTheme.SEPIA -> ReaderPalette(
        background = Color(0xFFF0E2C8),
        backgroundAlt = Color(0xFFE7D5B4),
        text = Color(0xFF3A2E22),
        secondary = Color(0xFF8B765D),
        accent = Color(0xFF9A5B3C),
        isLight = true
    )
    ReaderTheme.GREEN -> ReaderPalette(
        background = Color(0xFFE3EDE0),
        backgroundAlt = Color(0xFFD6E3D3),
        text = Color(0xFF27342B),
        secondary = Color(0xFF6F7F72),
        accent = Color(0xFF4F7A5E),
        isLight = true
    )
    ReaderTheme.NIGHT -> ReaderPalette(
        background = Color(0xFF121513),
        backgroundAlt = Color(0xFF191D1A),
        text = Color(0xFFBEB9AF),
        secondary = Color(0xFF787F79),
        accent = Color(0xFFC98B4B),
        isLight = false
    )
}
