package dev.kosh.financetracker.feature.lock

import androidx.compose.foundation.background
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

/** Filled/empty dots showing how many digits have been entered. */
@Composable
fun PinDots(count: Int, error: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "$count of ${VaultCore.PIN_LENGTH} digits entered" },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(VaultCore.PIN_LENGTH) { i ->
            val filled = i < count
            Box(
                Modifier.size(16.dp).clip(CircleShape)
                    .then(if (filled) Modifier.background(if (error) Tinward.colors.negative else Tinward.colors.accent)
                          else Modifier.border(1.5.dp, if (error) Tinward.colors.negative else Tinward.colors.divider, CircleShape)),
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { d -> Key(d.toString(), enabled) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) { bottomLeft?.invoke() }
            Key("0", enabled) { onDigit('0') }
            Key("⌫", enabled, description = "Delete") { onDelete() }
        }
    }
}

@Composable
private fun Key(label: String, enabled: Boolean, description: String? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(76.dp).clip(CircleShape).background(Tinward.colors.surface)
            .border(1.dp, Tinward.colors.divider, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description ?: label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
            color = if (enabled) Tinward.colors.textPrimary else Tinward.colors.textTertiary,
        )
    }
}
