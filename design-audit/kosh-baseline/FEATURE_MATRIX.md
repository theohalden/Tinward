# Kosh – Feature Matrix

Legend: ✅ implemented & working · 🟡 partial/incomplete · ❌ absent · 📱 seen on device · 📄 present in repo source.

| Feature | Device 📱 | Source 📄 | Notes |
|---|---|---|---|
| Home dashboard (month spend, income/spend card) | ✅ | ✅ | Source label "Recorded spending", device "Spent this month" |
| Month selection (sheet) | ✅ | ✅ | Per-screen state |
| Privacy amount masking | ✅ | ✅ | Session-only; masks ₹ only |
| Transaction list / history ("Activity", package `trail`) | ✅ | ✅ | Search + 4 filters, grouped by date |
| Transaction detail ("story") | ✅ | ✅ | 2 tabs; amount unformatted |
| Category edit (single transaction) | ✅ | ✅ | Inline list of 5 (REVIEW_PICKER) |
| Review queue (uncategorised) | ✅ | ✅ | 5 categories only; 12 pending on device |
| Spending insights (trend + categories) | ✅ | ✅ | % vs previous month; no drill-down |
| Income tracking | 🟡 | 🟡 | Home income = declared salary on device (Budget); source derives from classified INCOME txns |
| Expense tracking | ✅ | ✅ | Auto from SMS/statements |
| **Manual add transaction** | ✅ | ❌ | Device sheet; source only has `TransactionSource.MANUAL` enum |
| **Budget (fixed expenses, 50/30/20)** | ✅ | ❌ | Not in repo |
| **Export data** | 📱 button seen, not exercised | ❌ | |
| Statement import (HDFC xls, Kotak csv, Slice xlsx) | ✅ picker | ✅ | Parsers + reconciliation + dialogs in source |
| SMS ingestion (inbox scan + live receiver) | ✅ | ✅ | READ_SMS/RECEIVE_SMS; parsers: HDFC, Kotak, Slice, generic UPI |
| SMS review/confirmation of *detected* transactions | 🟡 | 🟡 | Only uncategorised spending is reviewable; unparsed/unknown-direction messages are counted in a summary line, never shown |
| Auto-categorisation (merchant rules + mappings) | ✅ | ✅ | `CategoryEngine`, `MerchantCategoryMappingEntity`; 19 categories |
| Transfer / card-payment detection | ✅ | ✅ | `TransferDetector`, `CardTransactionClassifier` |
| Source reconciliation (SMS ↔ statement) | – | ✅ | `SourceReconciliation`, `TransactionSourceLink` (DB v8) |
| Accounts overview | 🟡 | 🟡 | Cards show bank + suffix + monthly debit/credit; **no balance**, no account management |
| Balances | ❌ | ❌ | `Bal Rs.` in SMS text is not extracted into UI |
| Categories management (create/rename/colour) | ❌ | ❌ | Fixed enum, fixed colours |
| Search & filters | ✅ | ✅ | Text + type only |
| Multi-currency | ❌ | ❌ | ₹ hard-coded |
| Recurring / subscriptions view | ❌ | ❌ | `SUBSCRIPTIONS` category only; Budget has manual fixed items (device) |
| Goals / savings | ❌ | ❌ | 50/30/20 "Savings" bar on device only |
| Notifications / alerts | ❌ | ❌ | |
| Settings | ❌ | ❌ | |
| Onboarding | ❌ | ❌ | Permission gate only |
| Light theme | ❌ | ❌ | Dark only |
| Backup / cloud sync | ❌ | ❌ | `allowBackup=false` |
| Biometric/app lock | ❌ | ❌ | |
| Localisation (strings.xml) | ❌ | ❌ | Only `app_name`; all UI strings hard-coded in Kotlin |
| Tests | – | ✅ | 24 unit-test files: parsers, classifiers, statements, calculator, reconciliation, formatting |

## Code status: implemented / incomplete / unused
**Implemented & wired:** all 5 routes in `MainActivity` (Overview, Trail, Insights, Review, Detail), `AppShellViewModel` badge, `SmsSyncManager`, `SmsReceiver`, repository/DAO/DB (Room v8, `exportSchema=false`), statement import VM + dialog, month picker, charts.
**Incomplete:** `TransactionSource.MANUAL`/`NOTIFICATION` enum values with no UI/ingest path in source; `PaymentMethod` richness (NEFT/IMPS/RTGS/CASH/NETBANKING) not surfaced; account concept limited to bank+suffix string; Review category list limited to 5; the repository holds no Budget/fixed-expense entities (so the device Budget data model is unknown).
**Unused:** `SegmentedBar` composable (no references); `KoshColors.ChartPalette` (no references); `TransactionDetailViewModel` fine; `ic_visibility*` drawables used via `VisibilityToggle` (ok).
**Naming debt:** package `dev.kosh.financetracker`, route/screen names (`Trail`, `Insights`, `Overview`), `KoshTheme`/`KoshColors`/`KoshCard`/`KoshWordmark`/`ic_kosh_mark`, app label "Kosh", preview flavour suffix `.preview`. (Application ID must **not** change per brief.)

## Persistence & state
- Room (`FinanceDatabase` v8: `TransactionEntity`, `MerchantCategoryMappingEntity`, `TransactionSourceLink`), Hilt-provided (`DatabaseModule`). Flows → `stateIn(WhileSubscribed(5s))` in each ViewModel; UI state is immutable data classes.
- UI-only state (`amountsHidden`, selected tab on Detail, `editingCategory`) in `remember` – lost on rotation/process death (not `rememberSaveable`).
- All ViewModels recompute from the **full** transaction list on every emission (`FinanceCalculator.*` over `observeTransactions()`); fine at current volumes, a scaling risk later.
- Preview flavour: copied ledger, SMS receiver disabled (`src/preview`).
