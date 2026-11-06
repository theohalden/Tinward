package dev.kosh.financetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Read tokens with `Tinward.colors` inside any composable. */
object Tinward {
    val colors: TinwardColors
        @Composable get() = LocalTinwardColors.current
}

private val LocalTinwardColors = staticCompositionLocalOf { TinwardPalette.Dark }

/** Semantic colours Material3's ColorScheme has no slot for (kept for existing call sites). */
data class KoshExtendedColors(
    val income: Color,
    val expense: Color,
    val accentText: Color,
    val comparison: Color,
    val warmHighlight: Color,
    val burntAmber: Color,
    val raisedSurface: Color,
)

object KoshExtendedTheme {
    val colors: KoshExtendedColors
        @Composable get() {
            val c = Tinward.colors
            return KoshExtendedColors(
                income = c.positive,
                expense = c.negative,
                accentText = c.accentText,
                comparison = c.transfer,
                warmHighlight = c.accent,
                burntAmber = c.accentSoft,
                raisedSurface = c.raised,
            )
        }
}

private fun TinwardColors.toMaterial() = if (isDark) {
    darkColorScheme(
        background = background, surface = surface, surfaceVariant = raised,
        onBackground = textPrimary, onSurface = textPrimary, onSurfaceVariant = textSecondary,
        primary = accent, onPrimary = onAccent, outline = divider,
    )
} else {
    lightColorScheme(
        background = background, surface = surface, surfaceVariant = raised,
        onBackground = textPrimary, onSurface = textPrimary, onSurfaceVariant = textSecondary,
        primary = accent, onPrimary = onAccent, outline = divider,
    )
}

/** Follows the system light/dark setting. */
@Composable
fun KoshTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) TinwardPalette.Dark else TinwardPalette.Light
    CompositionLocalProvider(LocalTinwardColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = KoshTypography,
            content = content,
        )
    }
}
