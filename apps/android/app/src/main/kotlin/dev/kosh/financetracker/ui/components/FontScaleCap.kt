package dev.kosh.financetracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Lets body text grow with the user's font size while keeping compact chrome (tab labels, the month
 * pill, the wordmark) usable. At very large sizes such labels otherwise wrap letter by letter and
 * push other content off screen. Text outside these elements is never capped.
 */
@Composable
fun CappedFontScale(max: Float = 1.3f, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale.coerceAtMost(max))) { content() }
}
