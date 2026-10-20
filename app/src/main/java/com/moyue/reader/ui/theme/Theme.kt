package com.moyue.reader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Moss,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E8DF),
    onPrimaryContainer = Color(0xFF173128),
    secondary = Terracotta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4DED4),
    onSecondaryContainer = Color(0xFF422016),
    tertiary = Gold,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6E4C9),
    onTertiaryContainer = Color(0xFF3F2A0D),
    background = Paper,
    onBackground = Charcoal,
    surface = PaperAlt,
    onSurface = Charcoal,
    surfaceVariant = Sand,
    onSurfaceVariant = Color(0xFF5E574B),
    outline = Color(0xFF8A806F),
    outlineVariant = Color(0xFFD8CEBD),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    surfaceTint = Moss
)

private val DarkColors = darkColorScheme(
    primary = NightPrimary,
    onPrimary = Color(0xFF1B3B2E),
    primaryContainer = Color(0xFF2C4C3E),
    onPrimaryContainer = Color(0xFFC6E7D6),
    secondary = TerracottaLight,
    onSecondary = Color(0xFF4B2418),
    secondaryContainer = Color(0xFF5F3326),
    onSecondaryContainer = Color(0xFFFFDBCE),
    tertiary = Color(0xFFE9C48D),
    onTertiary = Color(0xFF422C0A),
    tertiaryContainer = Color(0xFF5C421D),
    onTertiaryContainer = Color(0xFFFFDEAC),
    background = NightBg,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightSurfaceAlt,
    onSurfaceVariant = NightMuted,
    outline = Color(0xFF7E857D),
    outlineVariant = Color(0xFF373E38),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = NightText,
    inverseOnSurface = NightBg,
    surfaceTint = NightPrimary
)

@Composable
fun MoyueTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MoyueTypography,
        content = content
    )
}
