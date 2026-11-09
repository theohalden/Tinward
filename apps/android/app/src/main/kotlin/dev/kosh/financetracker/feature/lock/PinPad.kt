package dev.kosh.financetracker.feature.lock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kosh.financetracker.security.VaultCore
import dev.kosh.financetracker.ui.theme.Tinward

/**
 * Filled/empty dots showing how many digits have been entered. Each dot pops as it fills, the row
 * shakes on a wrong PIN, and while [busy] (checking the PIN) all dots pulse so the pause reads as work, not a freeze.
 */
@Composable
fun PinDots(count: Int, error: Boolean = false, busy: Boolean = false, modifier: Modifier = Modifier) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(error) {
        if (error) { for (x in listOf(-10f, 10f, -7f, 7f, -3f, 0f)) shake.animateTo(x, tween(45)) }
    }
    val pulse = rememberInfiniteTransition(label = "busy")
    val busyAlpha by pulse.animateFloat(0.4f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "busyAlpha")
    Row(
        modifier.offset(x = shake.value.dp).semantics { contentDescription = "$count of ${VaultCore.PIN_LENGTH} digits entered" },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(VaultCore.PIN_LENGTH) { i ->
            val filled = busy || i < count
            val scale by animateFloatAsState(if (filled) 1f else 0.85f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "dot")
            val fill by animateColorAsState(
                if (!filled) Color.Transparent else if (error) Tinward.colors.negative else Tinward.colors.accent, tween(120), label = "dotColor",
            )
            Box(
                Modifier.size(14.dp).scale(scale).alpha(if (busy) busyAlpha else 1f).clip(CircleShape)
                    .background(fill)
                    .border(1.5.dp, if (error) Tinward.colors.negative else if (filled) fill else Tinward.colors.divider, CircleShape),
            )
        }
    }
}

/**
 * On-screen number pad, so the system keyboard never opens for the PIN. [bottomLeft] is where the
 * biometric button goes when enabled.
 */
@Composable
fun NumPad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    bottomLeft: (@Composable () -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { d -> Key(d.toString(), enabled) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) { bottomLeft?.invoke() }
            Key("0", enabled) { onDigit('0') }
            Key("⌫", enabled, description = "Delete") { onDelete() }
        }
    }
}

@Composable
private fun Key(label: String, enabled: Boolean, description: String? = null, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.size(64.dp).clip(CircleShape).background(Tinward.colors.surface)
            .border(1.dp, Tinward.colors.divider, CircleShape)
            .clickable(enabled = enabled, onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() })
            .semantics { contentDescription = description ?: label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
            color = if (enabled) Tinward.colors.textPrimary else Tinward.colors.textTertiary,
        )
    }
}
