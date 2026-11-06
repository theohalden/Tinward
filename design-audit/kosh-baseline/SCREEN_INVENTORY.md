# Kosh – Screen Inventory (baseline)

Audit only. Nothing was redesigned, installed, cleared or committed. Evidence: Kosh `dev.kosh.financetracker` v0.1.0 (versionCode 1) on a OnePlus CPH2681, Android 15, 720×1604 @320dpi, plus the Kotlin source in `apps/android` (monorepo commit `7edfd75`).
All screenshots are in `screenshots/` and **redacted** (amounts, merchants, account digits, SMS text, budget items are blocked out). Raw captures and UI-hierarchy dumps contain personal data and were kept outside the repo.

## ⚠️ Critical caveat: installed build ≠ repo source
The phone runs an older/different build (last updated 2026‑10‑09) than the source in this checkout (monorepo import 2026‑11‑05). Differences observed:

| Feature | On device | In `apps/android` source |
|---|---|---|
| **Budget** tab (fixed expenses, 50/30/20 rule) | Yes | **No** (no code, no route, no strings; `git log -S Budget` finds nothing) |
| **Add transaction** (+ button, bottom sheet) | Yes | **No** (`TransactionSource.MANUAL` enum exists, no UI) |
| **Export data** button | Yes | **No** |
| Home hero label | "Spent this month" | "Recorded spending" |
| Transaction detail amount | `₹<amount without grouping or decimals>` | `"₹${amount}"` (same raw format) |
| 5-item bottom nav | Home / Activity / Spending / Budget / Review | 4 items (no Budget) |
| Reconciliation / statement-import notes ("Excluded from totals", provisional-totals copy) | Only "Transfer · Excluded from spending" seen | Present |

Conclusion: the Budget / Add / Export code lives in a source tree that is **not in this repo** (or was removed in the monorepo reconciliation). Before any redesign, the owner must decide which tree is the source of truth. All "device-only" items below are marked **[device only]**; "source-only" items **[source only]**.

## Screens (13 distinct screens/sheets/states reachable on device; 3 more source-only states)

| # | Screen | Purpose | Entry | Evidence |
|---|---|---|---|---|
| 1 | **Home / Overview** | Month spend hero, income vs spending, review prompt, accounts carousel, recent activity | App launch; bottom nav "Home" | `01_home_top`, `02_home_scroll1`, `61_home_amounts_hidden` |
| 2 | **Month picker sheet** | Pick month/year (dots mark months with data) | Month pill on Home/Spending/Budget | `60_month_picker` |
| 3 | **Activity (Money Trail)** | Searchable, filterable, date-grouped transaction list | Bottom nav; Home "See all" | `10_activity`, `11_activity_scroll`, `12_activity_filter_transfers` |
| 4 | **Activity – empty/no-result state** | "No transactions found" | Search with no match | `13_activity_search_empty` |
| 5 | **Spending (Insights)** | Month total, % vs last month, daily trend chart, category breakdown | Bottom nav; Home hero tap | `20_spending`, `21_spending_scroll` |
| 6 | **Budget** [device only] | Fixed monthly expenses list (delete ×, "+ Add fixed expense"), 50/30/20 rule bars | Bottom nav | `30_budget`, `31_budget_scroll1` |
| 7 | **Review** | One-at-a-time categorisation queue with source SMS, 5 category choices, Confirm | Bottom nav (red dot badge); Home review chip | `40_review`, `41_review_scroll` |
| 8 | **Transaction detail ("Transaction story")** – tab 1 "How Kosh understood this" | Timeline: SMS captured → type → category | Tap any row (Home/Activity) | `80_detail_story` |
| 9 | **Transaction detail** – tab 2 "Source & details" + Edit category | Field list, inline category edit | Tab on detail | `81_detail_details_tab` |
| 10 | **Add transaction sheet** [device only] | Spent/Received toggle, amount, note, 19-category list, Save | "+" on Home | `50_add_transaction`, `51_add_transaction_scroll`, `52_add_transaction_scroll2` |
| 11 | **Hidden-amounts state** | Privacy eye masks amounts as `₹*****` | Eye icon on Home | `61_home_amounts_hidden` |
| 12 | **Import statement** | System file picker (Android DocumentsUI), not Kosh UI; then confirm/result dialogs [source only, not exercised to avoid writing data] | Import icon on Home | (picker was Android system UI; not captured) |
| 13 | **Export data** [device only] | Not opened (would write/share user data) | Export icon on Home | – |
| S1 | **SMS permission gate** [source only, not shown: permission already granted] | "Kosh needs SMS access" + Grant button | First launch without permission | code: `OverviewScreen.PermissionGate` |
| S2 | **Loading state** [source only] | Spinner + "Reading your bank SMS…" | First sync, no data | `OverviewScreen.LoadingState` |
| S3 | **Empty home** [source only] | "No transactions yet" + Scan SMS history | No data | `OverviewScreen.EmptyHomeState` |

Not present anywhere (device or source): splash/onboarding, Settings screen, standalone Accounts/Balances screen, standalone Categories screen, error screens (only a dialog/summary line), global-currency settings, light theme.

## Per-screen audit

Priority: H/M/L. Recommendation: Keep / Refine / Redesign / Remove.

### 1. Home / Overview – **Redesign (H)**
- **Existing UI:** orange→black radial "warm hero" gradient behind the status bar; wordmark; month pill; three round icon buttons (+, import, export); eye toggle; 52sp amount; sparkline; Income/Spending card with progress bars; review chip; account gradient cards; recent activity card with "See all"; 5-tab bottom bar.
- **UX issues:** headline answers "how much did I spend", but the screen has **no balance, no net/left-to-spend, no budget progress** (those live on another tab). Income and Spending repeated twice (hero amount and card). Three unlabelled icon buttons in a row under the wordmark (+ / ↑ / ↓: upload vs download glyph for export vs import is ambiguous; labelled only for TalkBack). Review chip wraps to two lines. Accounts show only monthly debit/credit sums – **no balances** – so cards look like bank cards but carry no balance. Accounts carousel clips the 2nd card (peek is fine) but amounts on it are cut mid-number (`₹17,05…`), looks like truncation.
- **Visual issues:** gradient hero is the loudest thing in the app and conflicts with the editorial palette; account card gradients (blue/indigo/teal/pink/gold) are off-palette; scrolling content passes under the status bar with no scrim (title "Accounts" collides with clock in `02_home_scroll1`); mixed corner radii (20/14/36 px circles); hero amount tracks wide (letter-spacing from `tnum`).
- **Functional issues:** `hasPermission` is not re-checked on resume; hero `displayLarge` has no font scaling guard (52sp ×1.3 would overflow); the eye toggle state is not persisted and doesn't hide counts/merchant names; the sparkline only appears with ≥2 days of data.
- **Source note:** source Home has no Budget/+ button; see caveat.

### 2. Month picker sheet – **Keep / Refine (L)**
Clear 3-col grid with data dots, selected month in gold. Issues: year heading "2026" then older years require scroll; no "this month" shortcut; dots on every month (data indicator unclear). `60_month_picker`.

### 3. Activity – **Refine (M)**
- **Existing UI:** wordmark + large "Activity" + subtitle; rounded search field; filter chips (All / Spending / Income / Transfers); date headers ("6 NOV FRI"); rows with category tile, merchant, `Bank ••suffix · SMS`, amount, time.
- **UX issues:** header consumes ~330px before the first row; search placeholder wraps to two lines; subtitle "All transactions from your bank SMS" is wrong once manual/statement entries exist; "Unknown" merchant (and a ○ placeholder icon) appears for unparsed rows; rows with source text like "Kotak Bank A/c X9996 to …" expose raw parser strings and can wrap to 3 lines; "Transfer · Excluded from spending" is jargon; no month filter, no category/account filter, no sort, no swipe actions; list is unbounded without sticky date headers.
- **Visual issues:** "<Bank> • •<last4> · SMS" has a doubled bullet (`••` rendered with spaced bullet); amounts right-aligned tabular – good; expense is salmon (#F1A292) / income is pale green – low-key but readable.
- **Functional:** amounts are formatted with `formatRupees` here but **not** on the detail screen (see Screen 8); no pagination (all rows in memory); search matches merchant/account suffix/amount only.

### 5. Spending – **Refine (M)**
- Strengths: big total, % chip vs previous month (`▼ 23%` in green = less spend, correct semantic), line+area trend, category rows with identity-coloured tiles and bars.
- Issues: trend chart has **no axes, no labels, no tooltips**, so it cannot be read quantitatively; large dead gap between amount and chart (`20_spending` y≈460–600); category bars show amounts but **no percentages** and bar fills are fractions of the top category, not of total; "Other" and "Uncategorized" dominate (roughly nine-tenths of the month) which makes insight low-value; tapping a category does nothing (no drill-down); month selector but no range (3M/6M/12M) or comparison view; `Spending` heading collides with wordmark spacing.
- Functional: `percentChangeVsPreviousMonth` null state shows nothing; Home sparkline and this chart both only span days with data.

### 6. Budget [device only] – **Redesign (H)**
- **Existing UI:** "Fixed Monthly Expenses" card (salary, 8 named items with × delete, total, left-after, "+ Add fixed expense"), "50/30/20 Rule" card (Needs/Wants/Savings bars with target ticks).
- **UX issues:** × delete buttons sit next to amounts with no confirmation visible; income is a hand-entered "declared monthly income (salary)" rather than computed from data; subtitle `(₹49800)` is **unformatted** (no grouping) while list amounts are formatted – inconsistent; Wants shows ₹0 (0%) because nothing is mapped to Wants, so the rule looks broken; "Needs 74%" bar shows red with target tick but no explanation of what counts as a need; no per-category spending budgets or progress against actual spend, so the tab does not connect to Spending.
- **Visual:** card-in-card on near-black, headings 28sp-ish, targets set in small light type ("target 50%") with weak contrast (#B9B6B1 on #191B1D is OK, but light weight at 13sp).
- **Functional:** no edit of an item (only delete + add), no recurring/due dates.

### 7. Review – **Refine (H)**
- **Existing UI:** "1 of 12" with prev/next arrows, big amount, date, explanatory copy, source SMS card, five radio-style categories (Food, Bills, Shopping, Transport, Other), disabled "Confirm type" button.
- **UX issues:** reviewing 12 items is a 3-tap flow each (pick, confirm, advance); the five choices don't cover most real merchants (e.g. an AI subscription ends up in "Other"/"Bills"), while the Add sheet offers 19 – mismatch; tab badge is a bare red dot (no count); "Confirm type" is far from the choices and is dimmed until a selection; the screen scrolls so Confirm may be below the fold on small devices; copy says "type" but asks for a category; the source SMS includes full card number suffix and phone numbers (privacy-sensitive, shown uncropped); no "skip", no "not spending / transfer" option, no merchant memory feedback ("always categorise X as…").
- **Visual:** good identity-coloured tiles; large display amount competes with headline.

### 8/9. Transaction detail ("Transaction story") – **Refine (H)**
- **Strength:** the "How Kosh understood this" timeline is a genuine trust feature (shows the SMS, the classification and categorisation reasoning).
- **Issues:** hero amount is **unformatted** `₹<amount without grouping or decimals>` (no `.00`, no grouping – `"₹${current.amount}"` in `TransactionDetailScreen.kt`) while every other screen uses `formatRupees`; amount has no sign; "Transaction type: EXPENSE" shows the raw enum; "Source: Bank SMS (Successfully paid Rs…)" truncates the body; account shown as `Card •••• <last4>` although the source SMS is a bank-account debit; tab titles wrap to two lines on one tab; the bottom nav disappears (route hides it) and there is no sticky action – Edit category is an outlined button, then reveals an unstyled list inline (no confirmation); no notes/tags/split/delete/attachments.
- **Visual:** default Material `TopAppBar` + `TabRow` + `Card`s (themed minimally) – a different visual language than the custom `KoshCard` screens.

### 10. Add transaction sheet [device only] – **Refine (H)**
- Good: it exists and is reachable from Home; has Spent/Received segmented toggle, amount with ₹ prefix, optional note.
- Issues: opens at ~50% height with a keyboard-less preview; fields are borderless/low-contrast boxes; **no date/time field, no account selector, no currency selector**; category list is a long vertical stack of 19 radio rows (scroll 2+ screens) instead of a grid/chips; Save is at the very bottom and disabled-looking; no recents/templates; entry is slow compared with premium trackers (amount → category → save should be ≤3 taps).

### 11. Hidden-amounts state – **Keep / Refine (L)**
Masks all ₹ values on Home (consistent `₹*****`), but not counts, merchant names, account digits or the sparkline; mask is per-session; the eye icon is small (≈48px target is OK).

### 12/13. Import / Export – **Refine (M)**
Import uses system picker (fine); the Kosh confirmation/result dialogs are plain `AlertDialog`s with long technical copy ("source records linked", "unresolved overlaps") – not user-friendly. Export unexplained (format, destination, redaction) – not exercised.

### S1–S3. First-use / empty / loading [source only] – **Redesign (H)**
No onboarding; first-use is a permission gate with one sentence, a wordmark and a stock button, then a spinner, then an empty message with a text-button ("Scan SMS history"). `EmptyState` component is text-only (no illustration/CTA). No explanation of privacy model beyond one line; no alternative path (manual add / import) offered on the empty state.

## Cross-screen findings (global)
- **No unified header:** the wordmark appears on every top-level screen (taking ~50px) *and* a 28sp title (Activity/Spending/Budget/Review) – redundant chrome; Home has wordmark only.
- **Status bar handling:** `enableEdgeToEdge` + transparent bars, but no scrim: content scrolls under the clock (seen in `02_home_scroll1`, `31_budget_scroll1`).
- **Single dark theme only.** No light theme; `statusBarStyle = dark` hard-coded.
- **Currency/locale hard-coded to ₹ and Indian grouping** (`formatRupees`); month labels use `Locale.getDefault()`; dates mix `MMM d, yyyy` (Home) with `6 NOV FRI` (Activity) and `6 Nov 2026 · 10:36 pm` (Review/Detail); time uses lowercase "pm".
