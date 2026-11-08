package dev.kosh.financetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward

/** Serif page title with optional back arrow and trailing actions. Clears the status bar. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = Tinward.colors.textPrimary) }
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = Tinward.colors.textPrimary, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Tinward.colors.accent,
            contentColor = Tinward.colors.onAccent,
            disabledContainerColor = Tinward.colors.raised,
            disabledContentColor = Tinward.colors.textTertiary,
        ),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Tinward.colors.divider),
    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = Tinward.colors.textPrimary) }
}

/** Pill-style segmented control, e.g. All / Income / Spent. */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Tinward.colors.raised)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Tinward.colors.surface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Tinward.colors.textPrimary else Tinward.colors.textTertiary,
                )
            }
        }
    }
}

@Composable
fun TinwardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    prefix: String? = null,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    secret: Boolean = false,
    digitsOnly: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = Tinward.colors.textTertiary) } },
        prefix = prefix?.let { { Text(it) } },
        singleLine = singleLine,
        visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                secret && digitsOnly -> KeyboardType.NumberPassword
                secret -> KeyboardType.Password
                numeric -> KeyboardType.Decimal
                else -> KeyboardType.Text
            },
        ),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Tinward.colors.surface,
            unfocusedContainerColor = Tinward.colors.surface,
            focusedBorderColor = Tinward.colors.accent,
            unfocusedBorderColor = Tinward.colors.divider,
            focusedTextColor = Tinward.colors.textPrimary,
            unfocusedTextColor = Tinward.colors.textPrimary,
            focusedLabelColor = Tinward.colors.accentText,
            unfocusedLabelColor = Tinward.colors.textTertiary,
            cursorColor = Tinward.colors.accent,
        ),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Tinward.colors.textPrimary, modifier = modifier)
}

/** Label / value pair used in detail and settings lists. */
@Composable
fun FieldRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textTertiary)
        Spacer(Modifier.width(Spacing.md))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textPrimary, maxLines = 2)
    }
}


/** Wrapping row of single-choice pills (used for account type, currency, account pickers). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        options.forEach { o ->
            val on = o == selected
            Text(
                label(o),
                style = MaterialTheme.typography.labelLarge,
                color = if (on) Tinward.colors.onAccent else Tinward.colors.textPrimary,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (on) Tinward.colors.accent else Tinward.colors.raised)
                    .clickable { onSelect(o) }
                    .padding(horizontal = Spacing.md, vertical = Spacing.ms),
            )
        }
    }
}
