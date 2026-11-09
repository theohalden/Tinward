package dev.kosh.financetracker.feature.plus

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kosh.financetracker.ui.components.KoshCard
import dev.kosh.financetracker.ui.components.PrimaryButton
import dev.kosh.financetracker.ui.components.ScreenHeader
import dev.kosh.financetracker.ui.components.SecondaryButton
import dev.kosh.financetracker.ui.components.SectionLabel
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import javax.inject.Inject

/** Only features that exist and work in this build are listed. Keep this honest. */
val PLUS_FEATURES = listOf(
    "Money Timeline" to "See your expected balance over 30, 60 or 90 days from your bills and income, with every assumption shown.",
    "Unlimited budgets, with rollover" to "Budget every category, and carry what you did not spend into next month.",
    "More than one savings goal" to "Save for several things at once.",
    "Financial Review" to "Top merchants, what rose or fell since last month, your spending pace, savings rate and how much of your income is already committed to bills.",
    "Debt planner" to "For credit cards: how long debt takes to clear, what it costs in interest, how much of each limit you use month by month, and highest-interest-first versus smallest-first.",
    "Custom rules" to "Write your own rules, like merchant contains swiggy then Food. They label new and existing transactions, show what they will do first, and can be undone. They never move money.",
)

@HiltViewModel
class PaywallViewModel @Inject constructor(val entitlements: Entitlements, val billing: BillingManager) : ViewModel()

@Composable
fun PaywallScreen(onBack: () -> Unit, viewModel: PaywallViewModel = hiltViewModel()) {
    val tier by viewModel.entitlements.tier.collectAsState()
    val offers by viewModel.billing.offers.collectAsState()
    val message by viewModel.billing.message.collectAsState()
    val busy by viewModel.billing.busy.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.billing.refresh() }
    val plus = tier == Tier.PLUS
    val source = viewModel.entitlements.source

    Column(Modifier.fillMaxSize().background(Tinward.colors.background)) {
        ScreenHeader("Tinward Plus", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                if (plus) "You have Tinward Plus." else "Go further with your own numbers.",
                style = MaterialTheme.typography.headlineMedium, color = Tinward.colors.textPrimary,
            )
            Text(
                "Free already includes everything you need to record and understand your money: accounts, cards, SMS and CSV import, budgets, bills, export and backup. Plus adds planning tools on top. It never locks you out of your own data.",
                style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary,
            )
            SectionLabel("What Plus adds")
            KoshCard {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    PLUS_FEATURES.forEach { (title, body) ->
                        Column {
                            Text(title, style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
                            Text(body, style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary)
                        }
                    }
                }
            }

            if (plus) {
                when (source) {
                    EntitlementSource.DEBUG -> Text("Turned on for testing in this debug build.", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.accentText)
                    else -> viewModel.entitlements.graceDaysLeft()?.let {
                        Text("Active. Tinward re-checks with Google Play whenever you are online; it keeps Plus for up to $it more offline days.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary)
                    }
                }
                SecondaryButton("Manage subscription", {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?sku=${BillingManager.PRODUCT_ID}&package=${context.packageName}")))
                }, Modifier.fillMaxWidth())
            } else {
                offers.forEach { o ->
                    PrimaryButton("${o.label} \u00B7 ${o.price} ${o.period}", { (context as? android.app.Activity)?.let { viewModel.billing.purchase(it, o) } }, Modifier.fillMaxWidth())
                }
                if (busy && offers.isEmpty()) Text("Checking Google Play\u2026", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary) }
            }
            TextButton(onClick = { viewModel.billing.refresh() }) { Text("Restore purchases", color = Tinward.colors.accentText) }

            if (viewModel.entitlements.canUseDebugSwitch) {
                var on by remember { mutableStateOf(source == EntitlementSource.DEBUG) }
                SectionLabel("Testing (debug builds only)")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Turn Plus on without buying", style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary, modifier = Modifier.weight(1f))
                    Switch(
                        checked = on, onCheckedChange = { on = it; viewModel.entitlements.setDebugPlus(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Tinward.colors.accent, checkedThumbColor = Tinward.colors.onAccent),
                    )
                }
            }
            Text(
                "Subscriptions are billed and managed by Google Play. Prices are set by Google Play for your country. If you stop, you go back to Free and keep all your data and exports. Anything you made with Plus (extra budgets, goals) stays visible but pauses.",
                style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary,
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
