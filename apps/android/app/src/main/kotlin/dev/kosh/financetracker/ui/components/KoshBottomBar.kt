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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.kosh.financetracker.navigation.KoshDestination
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward

private data class BottomNavItem(val destination: KoshDestination, val label: String, val icon: ImageVector)

private val LEFT = listOf(
    BottomNavItem(KoshDestination.Overview, "Home", Icons.Filled.Home),
    BottomNavItem(KoshDestination.Trail, "Activity", Icons.Filled.List),
)
private val RIGHT = listOf(
    BottomNavItem(KoshDestination.Plan, "Plan", Icons.Filled.DateRange),
    BottomNavItem(KoshDestination.Accounts, "Accounts", Icons.Filled.AccountBox),
)

/** Four tabs with a centred "+" for adding a transaction. Insights and Review live
 * off Home, so they keep Home highlighted. */
@Composable
fun KoshBottomBar(
    currentRoute: String?,
    onNavigate: (KoshDestination) -> Unit,
    onAdd: () -> Unit,
) {
    val homeFamily = setOf(KoshDestination.Overview.route, KoshDestination.Insights.route, KoshDestination.Review.route)
    Box(modifier = Modifier.fillMaxWidth()) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        color = Tinward.colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, Tinward.colors.divider),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = Spacing.sm, bottom = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
        ) {
            LEFT.forEach { item ->
                val selected = if (item.destination == KoshDestination.Overview) currentRoute in homeFamily else currentRoute == item.destination.route
                NavEntry(item, selected) { onNavigate(item.destination) }
            }
            Spacer(Modifier.size(64.dp))
            RIGHT.forEach { item -> NavEntry(item, currentRoute == item.destination.route) { onNavigate(item.destination) } }
        }
    }
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = (-20).dp)
            .size(56.dp)
            .clip(CircleShape)
            .background(Tinward.colors.accent)
            .clickable(role = Role.Button, onClick = onAdd)
            .semantics { contentDescription = "Add transaction" },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Add, contentDescription = null, tint = Tinward.colors.onAccent, modifier = Modifier.size(28.dp)) }
    }
}

@Composable
private fun NavEntry(item: BottomNavItem, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Tinward.colors.accentText else Tinward.colors.textTertiary
    Column(
        modifier = Modifier
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(item.icon, contentDescription = null, tint = color)
        Spacer(Modifier.height(2.dp))
        Text(item.label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
