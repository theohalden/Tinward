package dev.kosh.financetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kosh.financetracker.ui.theme.Tinward

/**
 * Standard card shell: a flat surface with a hairline border. Editorial rather than
 * elevated; no shadow or gradient, so it reads the same in light and dark.
 */
@Composable
fun KoshCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Tinward.colors.surface,
    cornerRadius: Dp = 16.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(1.dp, Tinward.colors.divider, shape),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}
