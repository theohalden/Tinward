package dev.kosh.financetracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tinward colour tokens. Brand values come from the web design system
 * (apps/web globals.css) and the approved Tinward board: paper #F7F5F0,
 * ink #121412, ochre #B8801F (#D5A64F on dark, as in the web app icon).
 * Light and dark are both first-class; screens read tokens through [Tinward.colors].
 */
data class TinwardColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val raised: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** Brand ochre for fills, indicators and the logo square. */
    val accent: Color,
    /** Contrast-safe ochre for small text and links on this theme's background. */
    val accentText: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val positive: Color,
    val negative: Color,
    val transfer: Color,
    val track: Color,
    /** Tonal category palette, ordered; see CategoryVisuals for the mapping. */
    val category: List<Color>,
)

object TinwardPalette {
    val Paper = Color(0xFFF7F5F0)
    val Card = Color(0xFFFDFCF9)
    val Paper2 = Color(0xFFEFECE4)
    val Stone = Color(0xFFE8E2D9)
    val Line = Color(0xFFDEDAD0)
    val Ink = Color(0xFF121412)
    val Ink2 = Color(0xFF4B4E4A)
    val Ink3 = Color(0xFF787A74)
    val Ochre = Color(0xFFB8801F)
    val OchreDeep = Color(0xFF7A4F08)
    val OchreOnDark = Color(0xFFD5A64F)
    val Charcoal = Color(0xFF2A2A28)
    val Muted = Color(0xFF9A9488)

    val Light = TinwardColors(
        isDark = false,
        background = Paper,
        surface = Card,
        raised = Paper2,
        divider = Line,
        textPrimary = Ink,
        textSecondary = Ink2,
        textTertiary = Ink3,
        accent = Ochre,
        accentText = OchreDeep,
        onAccent = Color.White,
        accentSoft = Color(0xFFE9D3A6),
        positive = Color(0xFF2F6B4F),
        negative = Color(0xFFA6422F),
        transfer = Color(0xFF7A5A8E),
        track = Stone,
        category = listOf(
            Color(0xFFB8801F), Color(0xFF5B8DB8), Color(0xFF2F6B4F), Color(0xFF7A5A8E),
            Color(0xFFA6422F), Color(0xFF8A6A3D), Color(0xFF787A74), Color(0xFF3F7F86),
        ),
    )

    val Dark = TinwardColors(
        isDark = true,
        background = Ink,
        surface = Color(0xFF1B1D1A),
        raised = Charcoal,
        divider = Color(0xFF3B3936),
        textPrimary = Paper,
        textSecondary = Color(0xFFB9B6B1),
        textTertiary = Muted,
        accent = OchreOnDark,
        accentText = OchreOnDark,
        onAccent = Ink,
        accentSoft = Color(0xFF3A2F17),
        positive = Color(0xFFB7D5AE),
        negative = Color(0xFFF1A292),
        transfer = Color(0xFFC8A7DB),
        track = Color(0xFF34332F),
        category = listOf(
            Color(0xFFD5A64F), Color(0xFF7FAEDB), Color(0xFF6FBFA3), Color(0xFFC8A7DB),
            Color(0xFFE08A7D), Color(0xFFC9A978), Color(0xFF9A9488), Color(0xFF6FB3BA),
        ),
    )
}
