package dev.kosh.financetracker.feature.overview

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import dev.kosh.financetracker.core.finance.CategorySlice
import dev.kosh.financetracker.core.finance.DaySpend
import dev.kosh.financetracker.core.finance.MonthSummary
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionType
import dev.kosh.financetracker.feature.importstatement.StatementImportDialog
import dev.kosh.financetracker.feature.importstatement.StatementImportViewModel
import dev.kosh.financetracker.ui.components.AccountPositionCard
import dev.kosh.financetracker.ui.components.CategoryIconTile
import dev.kosh.financetracker.ui.components.ImportIcon
import dev.kosh.financetracker.ui.components.KoshCard
import dev.kosh.financetracker.ui.components.KoshWordmark
import dev.kosh.financetracker.ui.components.MonthSelectorPill
import dev.kosh.financetracker.ui.components.SpendSparkline
import dev.kosh.financetracker.ui.components.VisibilityToggle
import dev.kosh.financetracker.ui.format.formatMoney
import dev.kosh.financetracker.ui.format.formatSignedMoney
import dev.kosh.financetracker.ui.theme.Tinward
import dev.kosh.financetracker.ui.theme.KoshExtendedTheme
import dev.kosh.financetracker.ui.theme.Spacing
import java.math.BigDecimal
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun OverviewScreen(
    onOpenTransaction: (Long) -> Unit,
    onOpenReview: () -> Unit,
    onSeeAllActivity: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    onOpenAccounts: () -> Unit,
    viewModel: OverviewViewModel = hiltViewModel(),
    importViewModel: StatementImportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val importState by importViewModel.uiState.collectAsState()
    var amountsHidden by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileName = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "statement"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes != null) importViewModel.onFileSelected(fileName, bytes)
    }

    fun hasAllSmsPermissions(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    var hasPermission by remember { mutableStateOf(hasAllSmsPermissions()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasPermission = hasAllSmsPermissions()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Tinward.colors.background),
    ) {
        when {
            !state.loaded -> LoadingState()

            !hasPermission && !state.hasAnyData -> PermissionGate(
                onGrant = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                },
            )

            syncState.isSyncing && !state.hasAnyData -> LoadingState(syncing = true)

            !state.hasAnyData -> EmptyHomeState(onScan = viewModel::sync)

            else -> HomeContent(
                state = state,
                onSmsScan = {
                    if (hasPermission) viewModel.sync()
                    else permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                },
                isSyncing = syncState.isSyncing,
                syncSummary = syncState.error ?: if (syncState.hasSyncedOnce) {
                    "SMS scan: ${syncState.lastScannedCount} checked, ${syncState.lastImportedCount} added, " +
                        "${syncState.lastAlreadyRepresentedCount} already represented. " +
                        "${syncState.lastUnparsedCount} financial-looking messages could not be parsed; " +
                        "${syncState.lastUnknownDirectionCount} had no clear debit or credit."
                } else null,
                amountsHidden = amountsHidden,
                onToggleAmountsHidden = { amountsHidden = !amountsHidden },
                onSelectMonth = viewModel::selectMonth,
                onOpenTransaction = onOpenTransaction,
                onOpenReview = onOpenReview,
                onSeeAllActivity = onSeeAllActivity,
                onOpenInsights = onOpenInsights,
                onOpenSettings = onOpenSettings,
                onOpenPlan = onOpenPlan,
                onOpenAccount = onOpenAccount,
                onOpenAccounts = onOpenAccounts,
                onImportClick = {
                    filePickerLauncher.launch(
                        arrayOf(
                            "text/csv",
                            "text/comma-separated-values",
                            "application/vnd.ms-excel",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "*/*",
                        ),
                    )
                },
            )
        }

        StatementImportDialog(state = importState, onConfirm = importViewModel::confirmImport, onDismiss = importViewModel::dismiss)
    }
}

private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String? =
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

@Composable
private fun PermissionGate(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.lg))
        KoshWordmark()
        Spacer(Modifier.height(Spacing.xl))
        Text("All your money.", style = MaterialTheme.typography.displaySmall, color = Tinward.colors.textPrimary)
        Text(
            "In perspective.",
            style = MaterialTheme.typography.displaySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
            color = Tinward.colors.accentText,
        )
        Text(
            "Tinward reads your bank SMS on this phone to build your transaction history. Nothing leaves your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = Tinward.colors.textSecondary,
        )
        PrimaryButton("Allow SMS access", onGrant)
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Tinward.colors.accent, contentColor = Tinward.colors.onAccent),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun LoadingState(syncing: Boolean = false) {
    // Placeholder blocks in the shape of Home, so unlocking settles into the real screen instead of flashing a different one.
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(
        0.35f, 0.7f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "alpha",
    )
    val block = Tinward.colors.raised.copy(alpha = alpha)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        KoshWordmark()
        Spacer(Modifier.height(Spacing.sm))
        Box(Modifier.width(120.dp).height(14.dp).background(block, RoundedCornerShape(7.dp)))
        Box(Modifier.width(240.dp).height(48.dp).background(block, RoundedCornerShape(12.dp)))
        Box(Modifier.fillMaxWidth().height(14.dp).background(block, RoundedCornerShape(7.dp)))
        Spacer(Modifier.height(Spacing.sm))
        Box(Modifier.fillMaxWidth().height(72.dp).background(block, RoundedCornerShape(16.dp)))
        Box(Modifier.fillMaxWidth().height(160.dp).background(block, RoundedCornerShape(16.dp)))
        if (syncing) Text("Reading your bank SMS\u2026", style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
    }
}

@Composable
private fun EmptyHomeState(onScan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        KoshWordmark()
        Spacer(Modifier.height(Spacing.xxl))
        Text("No transactions yet", style = MaterialTheme.typography.titleLarge, color = Tinward.colors.textPrimary)
        Text(
            "Scan your SMS history to find bank transactions. Opening Tinward does not rescan or change your existing history.",
            style = MaterialTheme.typography.bodyMedium,
            color = Tinward.colors.textSecondary,
        )
        PrimaryButton("Scan SMS history", onScan)
    }
}

@Composable
private fun HomeContent(
    state: OverviewUiState,
    onSmsScan: () -> Unit,
    isSyncing: Boolean,
    syncSummary: String?,
    amountsHidden: Boolean,
    onToggleAmountsHidden: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenReview: () -> Unit,
    onSeeAllActivity: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    onOpenAccounts: () -> Unit,
    onImportClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xxl),
    ) {
        item {
            Column(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = Spacing.md),
            ) {
                Spacer(Modifier.height(Spacing.sm))
                HomeHeader(
                    selectedMonth = state.selectedMonth,
                    availableMonths = state.availableMonths,
                    monthsWithData = state.monthsWithData,
                    onSelectMonth = onSelectMonth,
                    onImportClick = onImportClick,
                    onOpenSettings = onOpenSettings,
                )
                Spacer(Modifier.height(Spacing.lg))
                LeftToSpendHero(
                    summary = state.monthSummary,
                    plannedIncome = state.plannedIncome,
                    amountsHidden = amountsHidden,
                    onToggleAmountsHidden = onToggleAmountsHidden,
                    onOpenInsights = onOpenInsights,
                )
                state.available?.let { a ->
                    Spacer(Modifier.height(Spacing.md))
                    KoshCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPlan)) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Cash after bills and goals", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                                Text("your accounts, less what is due", style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textSecondary)
                            }
                            Text(
                                formatMoney(a.available, amountsHidden, a.currency), style = MaterialTheme.typography.titleMedium,
                                color = if (a.available.signum() < 0) Tinward.colors.negative else Tinward.colors.textPrimary,
                            )
                        }
                    }
                }
                if (state.dailySpend.any { it.amount.signum() > 0 }) {
                    Spacer(Modifier.height(Spacing.md))
                    KoshCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenInsights)) {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Spending pace", style = MaterialTheme.typography.titleSmall, color = Tinward.colors.textPrimary)
                                Text("Insights \u203A", style = MaterialTheme.typography.labelLarge, color = Tinward.colors.accentText)
                            }
                            dev.kosh.financetracker.ui.components.SpendPaceChart(
                                days = state.dailySpend, previousDays = state.previousDailySpend, month = state.selectedMonth,
                                currency = state.available?.currency ?: dev.kosh.financetracker.ui.format.BaseCurrency.code,
                                hidden = amountsHidden, chartHeight = 130.dp,
                            )
                        }
                    }
                }
                if (state.needsReviewCount > 0) {
                    Spacer(Modifier.height(Spacing.md))
                    NeedsReviewRow(state.needsReviewCount, onClick = onOpenReview)
                }
            }
        }

        item {
            SectionHeader(
                title = "Recent activity",
                action = "See all",
                onAction = onSeeAllActivity,
                modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.lg, bottom = Spacing.sm),
            )
            RecentActivityCard(
                transactions = state.recentActivity,
                amountsHidden = amountsHidden,
                onOpenTransaction = onOpenTransaction,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        }

        if (state.upcoming.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Coming up",
                    action = "Plan",
                    onAction = onOpenPlan,
                    modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.lg, bottom = Spacing.sm),
                )
                ComingUpCard(
                    items = state.upcoming,
                    amountsHidden = amountsHidden,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
            }
        }

        if (state.positions.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Accounts",
                    action = "All",
                    onAction = onOpenAccounts,
                    modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.lg, bottom = Spacing.sm),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(state.positions, key = { it.account.id }) { p -> AccountPositionCard(p, amountsHidden) { onOpenAccount(p.account.id) } }
                }
                Text(
                    "Tracked from your messages and entries, not live bank balances.",
                    style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.lg)) {
                Text(
                    "Recorded totals are provisional. Card history may be incomplete; unmatched overlapping records are excluded until reconciled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Tinward.colors.textTertiary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TextButton(onClick = onSmsScan, enabled = !isSyncing) {
                        Text(
                            if (isSyncing) "Scanning SMS\u2026" else "Scan SMS history",
                            color = Tinward.colors.accentText,
                        )
                    }
                    TextButton(onClick = onImportClick) {
                        Text("Import statement", color = Tinward.colors.accentText)
                    }
                }
                if (syncSummary != null) {
                    Text(syncSummary, style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary)
                }
                // Keeps the last links clear of the floating + button.
                Spacer(Modifier.height(88.dp))
            }
        }
    }
    // Opaque status-bar scrim so scrolled content never runs under the clock/icons.
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(Tinward.colors.background),
    )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Tinward.colors.textPrimary)
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = Tinward.colors.accentText,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onAction)
                    .wrapContentHeight(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
private fun HomeHeader(
    selectedMonth: YearMonth,
    availableMonths: List<YearMonth>,
    monthsWithData: Set<YearMonth>,
    onSelectMonth: (YearMonth) -> Unit,
    onImportClick: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KoshWordmark()

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            MonthSelectorPill(
                selectedMonth = selectedMonth,
                availableMonths = availableMonths,
                monthsWithData = monthsWithData,
                onSelectMonth = onSelectMonth,
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenSettings)
                    .semantics { contentDescription = "Settings" },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Filled.Settings,
                    contentDescription = null,
                    tint = Tinward.colors.textSecondary,
                )
            }
        }
    }
}

/**
 * The one number that answers "where do I stand?": recorded income minus recorded
 * spending for the month. With no income recorded it falls back to spending alone.
 */
@Composable
private fun LeftToSpendHero(
    summary: MonthSummary,
    plannedIncome: java.math.BigDecimal?,
    amountsHidden: Boolean,
    onToggleAmountsHidden: () -> Unit,
    onOpenInsights: () -> Unit,
) {
    val basis = dev.kosh.financetracker.core.finance.IncomeBasisRule.choose(summary.income, plannedIncome)
    val income = basis.amount
    val hasIncome = income.signum() > 0
    val left = income.subtract(summary.expense)
    val headline = if (hasIncome) left else summary.expense
    val label = when {
        !hasIncome -> "Spent this month"
        left.signum() < 0 -> "Over your income by"
        else -> "Left this month"
    }
    val caption = when (basis.source) {
        dev.kosh.financetracker.core.finance.IncomeSource.RECORDED -> "income received minus what you have spent"
        dev.kosh.financetracker.core.finance.IncomeSource.PLANNED ->
            "planned income minus what you have spent" + if (basis.receivedSoFar.signum() > 0) " \u00B7 ${formatMoney(basis.receivedSoFar, amountsHidden)} received so far" else ""
        dev.kosh.financetracker.core.finance.IncomeSource.NONE -> "no income recorded this month"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Tinward.colors.textSecondary)
            Spacer(Modifier.width(Spacing.xs))
            VisibilityToggle(
                visible = !amountsHidden,
                onClick = onToggleAmountsHidden,
                tint = Tinward.colors.textSecondary,
            )
        }
        Text(
            formatMoney(headline.abs(), amountsHidden),
            style = MaterialTheme.typography.displayLarge,
            color = if (hasIncome && left.signum() < 0) Tinward.colors.negative else Tinward.colors.textPrimary,
            modifier = Modifier.clickable(onClick = onOpenInsights),
        )
        Text(caption, style = MaterialTheme.typography.bodySmall, color = Tinward.colors.textTertiary)

        Spacer(Modifier.height(Spacing.md))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatColumn("Spent", formatMoney(summary.expense, amountsHidden), Modifier.weight(1f))
            StatColumn(if (basis.source == dev.kosh.financetracker.core.finance.IncomeSource.PLANNED) "Planned income" else "Income", formatMoney(income, amountsHidden), Modifier.weight(1f))
        }
        Spacer(Modifier.height(Spacing.sm))
        val fraction = if (hasIncome) (summary.expense.toFloat() / income.toFloat()).coerceIn(0f, 1f) else 0f
        ProgressBar(fraction = fraction, color = Tinward.colors.accent)
    }
}

@Composable
private fun StatColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Tinward.colors.textPrimary)
    }
}

@Composable
private fun ProgressBar(fraction: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Tinward.colors.track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .background(color, RoundedCornerShape(3.dp)),
        )
    }
}

@Composable
private fun NeedsReviewRow(count: Int, onClick: () -> Unit) {
    KoshCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier.size(8.dp).clip(CircleShape).background(Tinward.colors.accent),
                )
                Spacer(Modifier.width(Spacing.ms))
                Text(
                    if (count == 1) "1 transaction needs review" else "$count transactions need review",
                    style = MaterialTheme.typography.labelLarge,
                    color = Tinward.colors.textPrimary,
                )
            }
            Text("\u203A", style = MaterialTheme.typography.titleMedium, color = Tinward.colors.textTertiary)
        }
    }
}

@Composable
private fun RecentActivityCard(
    transactions: List<Transaction>,
    amountsHidden: Boolean,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (transactions.isEmpty()) {
        Text(
            "No activity in this month yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = Tinward.colors.textSecondary,
            modifier = modifier,
        )
        return
    }

    KoshCard(modifier = modifier.fillMaxWidth()) {
        Column {
            transactions.forEachIndexed { index, transaction ->
                RecentActivityRow(transaction, amountsHidden, onClick = { onOpenTransaction(transaction.id) })
                if (index != transactions.lastIndex) {
                    HorizontalDivider(color = Tinward.colors.divider, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun ComingUpCard(
    items: List<Upcoming>,
    amountsHidden: Boolean,
    modifier: Modifier = Modifier,
) {
    val today = java.time.LocalDate.now()
    KoshCard(modifier = modifier.fillMaxWidth()) {
        Column {
            items.take(4).forEach { o ->
                val days = java.time.temporal.ChronoUnit.DAYS.between(today, o.date)
                val whenText = when {
                    days < 0 -> "Overdue"
                    days == 0L -> "Today"
                    days == 1L -> "Tomorrow"
                    else -> "In $days days"
                }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(o.name, style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary, maxLines = 1)
                        Text(whenText + if (o.isCard) " \u00B7 card balance" else "", style = MaterialTheme.typography.bodySmall, color = if (days < 0) Tinward.colors.negative else Tinward.colors.textSecondary)
                    }
                    Text(formatMoney(o.amount, amountsHidden, o.currency), style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textSecondary)
                }
                HorizontalDivider(color = Tinward.colors.divider, thickness = 1.dp)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Due in the next 2 weeks", style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary)
                Text(
                    formatMoney(items.fold(java.math.BigDecimal.ZERO) { a, b -> a + b.amount }, amountsHidden),
                    style = MaterialTheme.typography.labelLarge, color = Tinward.colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun RecentActivityRow(transaction: Transaction, amountsHidden: Boolean, onClick: () -> Unit) {
    val isTransfer = transaction.type == TransactionType.TRANSFER || transaction.type == TransactionType.CREDIT_CARD_PAYMENT ||
        transaction.type == TransactionType.CASH_WITHDRAWAL || transaction.type == TransactionType.ADJUSTMENT
    val isDebit = transaction.direction == TransactionDirection.DEBIT
    val merchant = transaction.merchant ?: "Unknown"
    val subtitle = when (transaction.type) {
        TransactionType.CREDIT_CARD_PAYMENT -> "Card repayment \u00B7 not spending"
        TransactionType.CREDIT_CARD_PURCHASE -> "Card purchase"
        TransactionType.TRANSFER -> "Transfer \u00B7 not spending"
        TransactionType.ADJUSTMENT -> "Balance adjustment \u00B7 not income"
        TransactionType.CASH_WITHDRAWAL -> "Moved to cash \u00B7 not spending"
        else -> transaction.timestamp.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
    val amountColor = when {
        isTransfer -> Tinward.colors.textTertiary
        isDebit -> Tinward.colors.textPrimary
        else -> Tinward.colors.positive
    }
    val amountText = formatSignedMoney(transaction.amount, if (isTransfer) null else isDebit, amountsHidden, transaction.currency)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.ms)
            .semantics(mergeDescendants = true) {
                contentDescription = "$merchant, $subtitle, $amountText"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIconTile(transaction.category, merchant)
        Spacer(Modifier.width(Spacing.ms))
        Column(modifier = Modifier.weight(1f)) {
            Text(merchant, style = MaterialTheme.typography.bodyLarge, color = Tinward.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Tinward.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(amountText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = amountColor)
    }
}
