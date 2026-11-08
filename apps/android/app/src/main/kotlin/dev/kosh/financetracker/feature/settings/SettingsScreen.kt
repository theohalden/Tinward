package dev.kosh.financetracker.feature.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.data.settings.Appearance
import dev.kosh.financetracker.ui.components.CategoryIconTile
import dev.kosh.financetracker.ui.components.FieldRow
import dev.kosh.financetracker.ui.components.KoshCard
import dev.kosh.financetracker.ui.components.ScreenHeader
import dev.kosh.financetracker.ui.components.SectionLabel
import dev.kosh.financetracker.ui.components.SegmentedTabs
import dev.kosh.financetracker.ui.components.labelFor
import dev.kosh.financetracker.ui.theme.Spacing
import dev.kosh.financetracker.ui.theme.Tinward
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenDeleted: () -> Unit, onOpenImportCsv: () -> Unit, onOpenPlus: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appearance by viewModel.appearance.collectAsState()
    val baseCurrency by viewModel.baseCurrency.collectAsState()
    val dollarCurrency by viewModel.dollarCurrency.collectAsState()
    val scan by viewModel.scan.collectAsState()
    val plusTier by viewModel.entitlements.tier.collectAsState()
    var showCategories by remember { mutableStateOf(false) }
    var exportNote by remember { mutableStateOf<String?>(null) }
    var securityNote by remember { mutableStateOf<String?>(null) }
    val smsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }

    Column(Modifier.fillMaxSize().background(Tinward.colors.background)) {
        ScreenHeader("Settings", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionLabel("Appearance")
            SegmentedTabs(
                listOf("System", "Light", "Dark"),
                when (appearance) { Appearance.SYSTEM -> 0; Appearance.LIGHT -> 1; Appearance.DARK -> 2 },
                { viewModel.setAppearance(Appearance.values()[it]) },
            )

            if (viewModel.debugOptions.enabled) {
                val scale by viewModel.debugOptions.fontScale.collectAsState()
                SectionLabel("Testing (debug builds only)")
                Text("Text size on top of the system setting, to check large-text layouts.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary)
                dev.kosh.financetracker.ui.components.ChoiceChips(listOf(1f, 1.3f, 1.6f, 2f), scale, { "${it}\u00D7" }, viewModel.debugOptions::setFontScale)
            }

            SectionLabel("Tinward Plus")
            KoshCard {
                ActionRow(if (plusTier == dev.kosh.financetracker.feature.plus.Tier.PLUS) "You have Tinward Plus" else "Tinward Plus", "Planning tools on top of everything in Free.") { onOpenPlus() }
            }

            SectionLabel("Security")
            SecuritySection(viewModel.vault, viewModel.backup, securityNote) { securityNote = it }

            SectionLabel("Data")
            KoshCard {
                ActionRow("Export transactions (CSV)", exportNote ?: "Shares a file from this phone; nothing is uploaded by Tinward.") {
                    scope.launch {
                        val intent = viewModel.buildExport(context)
                        if (intent == null) exportNote = "No transactions to export yet." else { exportNote = null; context.startActivity(intent) }
                    }
                }
                HorizontalDivider(color = Tinward.colors.divider)
                ActionRow("Import a CSV file", "Add transactions from a statement you downloaded, any bank.") { onOpenImportCsv() }
                HorizontalDivider(color = Tinward.colors.divider)
                ActionRow("Recently deleted", "Restore transactions you removed.") { onOpenDeleted() }
                HorizontalDivider(color = Tinward.colors.divider)
                ActionRow("Categories", if (showCategories) "Tap to hide" else "${TransactionCategory.values().size - 1} categories") { showCategories = !showCategories }
                if (showCategories) {
                    TransactionCategory.values().filter { it != TransactionCategory.UNCATEGORIZED }.forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                            CategoryIconTile(c, labelFor(c), size = 28.dp)
                            Spacer(Modifier.padding(start = Spacing.ms))
                            Text(labelFor(c), style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textPrimary)
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                }
            }

            SectionLabel("Privacy and access")
            KoshCard {
                FieldRow("SMS access", if (smsGranted) "Allowed" else "Not allowed")
                if (scan.hasSyncedOnce) {
                    HorizontalDivider(color = Tinward.colors.divider)
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("Last message scan", style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary)
                        Text(
                            "Checked ${scan.lastScannedCount} messages. Recorded ${scan.lastImportedCount} new, ${scan.lastAlreadyRepresentedCount} already known. " +
                                "${scan.lastUnparsedCount + scan.lastUnknownDirectionCount} looked like bank messages but are in a format Tinward does not understand yet.",
                            style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary,
                        )
                        Text("This report shows counts only. It never shows or stores message text.", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary)
                    }
                }
                HorizontalDivider(color = Tinward.colors.divider)
                ActionRow("Manage permissions", "Open Android app settings") {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }
                HorizontalDivider(color = Tinward.colors.divider)
                Text(
                    "Tinward reads bank SMS on this phone only. There is no account, no analytics and no network permission. Each message it records is kept, text included, inside your encrypted data so it can show why a transaction is there. It is never logged or sent anywhere, and it is protected by your PIN. Message reading works for the formats it has been tested on; where it cannot read a message, you can add the transaction by hand or import a CSV.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Tinward.colors.textTertiary,
                    modifier = Modifier.padding(Spacing.md),
                )
            }

            SectionLabel("General")
            KoshCard {
                dev.kosh.financetracker.feature.settings.CurrencyRow(baseCurrency, viewModel::setBaseCurrency)
                HorizontalDivider(color = Tinward.colors.divider)
                DollarRow(dollarCurrency, viewModel::setDollarCurrency)
                HorizontalDivider(color = Tinward.colors.divider)
                FieldRow("Version", version)
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = Tinward.colors.textTertiary)
    }
}
