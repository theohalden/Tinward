# CHATGPT BRIEF: Kosh to Tinward, Android baseline audit

How to use: paste this whole file into ChatGPT and attach the PNGs from `screenshots/` (they are redacted). Suggested prompt at the end of the file.

Context: Kosh is an existing Android personal-finance app (Kotlin, Jetpack Compose, on-device SMS parsing, India banks). It is being rebranded to Tinward, a premium, globally accessible tracker. Brand is fixed and must not be changed: ink T with an ochre square mark; paper #f7f5f0, ink #121412, ochre #b8801f; Instrument Serif headings, Manrope body. Mobile direction: Editorial x Utility. This audit is baseline only; nothing has been redesigned.

Privacy: all amounts, merchants, account digits, SMS text and budget items are removed from the text and blocked out in the images.

Two things matter first:
1. The installed app (v0.1.0) is a different build from the repo source. It has Budget, Add transaction, Export; the repo does not. The repo has newer reconciliation work the phone lacks. Source-of-truth decision pending.
2. Device-only features were recovered by decompiling the APK; specs are in section 0.

## 0. Recovered specs for the device-only features

Source: jadx decompile of the installed APK (identical to a local backup APK). Logic only. No user data, no DataStore or database contents were read.

## Add transaction (`feature/addtransaction`)
- Bottom sheet from the "+" on Home. State: direction (Spent = DEBIT, Received = CREDIT), amount text, optional label, selected category.
- Save is rejected unless amount parses as a decimal greater than 0. A category must be chosen (Save looks inactive until then).
- Saved as `TransactionSource.MANUAL`, timestamp = **now** (no date/time picker), type EXPENSE for Spent and INCOME for Received, payment method UNKNOWN, confidence 1.0, merchant = the optional label (blank becomes null).
- Category list = all 19 categories, vertical radio rows.
- Design implication: no date, account or currency choice exists; back-dating a transaction is impossible.

## Export (`feature/export`)
- Export icon on Home writes `kosh-export-<yyyy-MM-dd>.csv` and opens the Android share sheet (`ACTION_SEND`, `text/csv`).
- Columns: Date (`yyyy-MM-dd HH:mm:ss`), Amount, Direction, Type, Category (display label, "Uncategorized" if none), Merchant, Bank, Account, Payment Method, Source, Notes. RFC-style quoting.
- It exports every transaction, with no month scope, no format choice and no warning that it contains personal data.

## Budget (`feature/budget`, `data/budget`, `core/finance`)
- Data: Preferences DataStore with two keys, `monthly_income` (a string) and `fixed_expenses_json` (a JSON array of `{id, label, amount}`). Nothing seeded in code. The income is declared by the user, not derived from transactions.
- Operations: set income, add expense, remove expense (no edit, no confirmation in the logic).
- Screen: "Fixed Monthly Expenses" card (income, items with delete, total fixed, left after fixed, "+ Add fixed expense") and a "50/30/20 Rule" card.
- Rule maths (`FinanceCalculator.budgetRuleForMonth`): spending by category is split into
  - Needs: Groceries, Bills, Rent, Fuel, Health, Education, Travel
  - Wants: Food, Shopping, Entertainment, Subscriptions, Personal Care, Technology, Home, Fees
  - Unclassified: everything else (Other, Uncategorized, Family, Business)
  and compared with declared income; targets 50/30/20.
- Observation (unverified): on the device the Needs bar value equalled the fixed-expense total while Wants showed 0, so the displayed Needs may come from the fixed list rather than from `budgetRuleForMonth`. Not confirmed in the decompiled UI code.
- Design implication: the user's mental model (fixed costs, what is left, 50/30/20) is the strongest "utility" feature in the app, but it is disconnected from the Spending and Activity data.

---

## Kosh – Screen Inventory (baseline)

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

---

## Kosh – Navigation Map (baseline)

## Architecture
- Single `MainActivity` (Compose, edge-to-edge) → `Scaffold` + `NavHost` (Navigation-Compose) → **flat 4-route graph** (source) / **5 tabs on the installed device build**.
- Start destination: `overview`. Bottom bar is custom (`KoshBottomBar`, rounded top corners), hidden only on the `detail/{transactionId}` route.
- Tab switching uses `popUpTo(startDestination){saveState}`, `launchSingleTop`, `restoreState` (standard state-preserving pattern).
- Sheets (month picker, add transaction) and dialogs (statement import) are in-screen overlays, not routes.
- No deep links, no nested graphs, no argument types beyond `transactionId: Long`.

## Routes (source: `navigation/KoshDestinations.kt`)
| Route | Screen | Bottom-nav? |
|---|---|---|
| `overview` | Home | Yes (Home) |
| `trail` | Activity | Yes (Activity) |
| `insights` | Spending | Yes (Spending) |
| `review` | Review | Yes (Review, red-dot badge from `AppShellViewModel.needsReviewCount`) |
| `detail/{transactionId}` | Transaction story | No (bar hidden) |
| *(device only)* Budget | Budget | Yes (4th of 5) |

## Flow diagram
```
                    ┌────────────── Bottom bar ──────────────┐
                    │ Home │ Activity │ Spending │ Budget* │ Review │
                    └──┬──────┬──────────┬──────────┬────────┬─────┘
                       │      │          │          │        │
 Home ── hero/spark tap ─────────────────► Spending │        │
 Home ── "See all" ────────────► Activity            │        │
 Home ── review chip ─────────────────────────────────────────► Review
 Home ── row tap ─┐     Activity ── row tap ─┐
                  └──────► Transaction story ◄┘   (back arrow → previous)
 Home ── month pill ──► Month sheet   (also on Spending, Budget*)
 Home ── "+" * ───────► Add transaction sheet
 Home ── import ──────► System file picker ──► Import dialog(s) [source]
 Home ── export * ────► (not exercised)
 Home ── eye ─────────► toggles amount masking (state in composable, not persisted)
 Detail ── "Edit category" ──► inline category list (no confirm)
 * = device-only (not in repo source)
```

## Entry points
- **Launcher icon** (gold "K" mark on near-black) → Home. Notification/deep-link entry: none. SMS receiver (`SmsReceiver`) writes transactions in the background; no notification surfaces them.
- First run without SMS permission and without data → Permission gate on Home (source).

## Observations
1. **Back behaviour:** pressing system Back at any tab's root exits the app (no "back to Home" convention). Observed during the audit: Back from Home → launcher.
2. **Cross-tab shortcuts are text links** ("See all", gold chip) rather than affordances; tapping the hero amount silently jumps to Spending (undiscoverable).
3. **Review is a top-level tab** even though it's a task queue; its badge is an unlabelled dot. On Home it is duplicated as a chip.
4. **Month state is per-screen** (separate `selectedMonth` in Overview, Insights, [Budget]); changing it on Home does not carry to Spending.
5. **Add/Import/Export live as three ungrouped icon buttons on Home** – should be reachable from every tab (e.g. a persistent add action).
6. **No Settings / profile / About destination**; there is nowhere for currency, privacy, SMS permissions, categories, export or app info.
7. Detail hides the bottom bar and offers only a back arrow (no next/previous transaction, no share).
8. Tab labels: Home / Activity / Spending / Budget / Review (the code names are Overview / Trail / Insights – internal naming differs from UI naming).

---

## Kosh – UX Audit (cross-cutting)

Per-screen detail (purpose, entry, UI, issues, recommendation, priority, screenshot) is in `SCREEN_INVENTORY.md`. This file ranks the cross-cutting issues against the evaluation criteria. Evidence tags: **[D]** seen on device, **[S]** seen in source, **[D+S]** both. Findings are observations; no redesign is proposed here.

## Summary scorecard
| Criterion | Rating | Why |
|---|---|---|
| Financial information hierarchy | ★★☆☆☆ | One huge number (spend) but no balance/net/left-to-spend on Home; income/spend repeated; accounts show flow, not balance |
| Readability & number formatting | ★★★☆☆ | Tabular figures + Indian grouping on most screens; unformatted on Detail (`₹<amount without grouping or decimals>`) and Budget subtitle (`₹49800`) |
| Transaction-entry speed | ★★☆☆☆ | Add exists only on device build; no date/account/currency; 19 radios; Review = 3 taps/item |
| Navigation clarity | ★★★☆☆ | Clear 5-tab bar; hidden shortcuts, per-screen month state, no Settings |
| Trust & perceived quality | ★★★☆☆ | "Transaction story" and "Excluded from spending" explain the maths (good); jargon, raw enums, provisional-totals copy, glow gradient reduce polish |
| Accessibility | ★★☆☆☆ | See below |
| Global currencies & localisation | ★☆☆☆☆ | ₹ and lakh/crore grouping hard-coded; English strings hard-coded |
| Empty / first-use | ★☆☆☆☆ | No onboarding; text-only empty states |
| Visual consistency | ★★½☆☆ | Custom KoshCard screens vs default Material detail screen; off-palette gradients; mixed date formats |

## High priority
1. **Source/device divergence** [D+S]. The installed app has Budget, Add transaction and Export which do not exist in the repo. The redesign base must be chosen first (see `FEATURE_MATRIX.md`).
2. **Unformatted money on Transaction detail and Budget** [D+S]: `"₹${current.amount}"` in `TransactionDetailScreen.kt`; Budget subtitle `(₹49800)` [D]. All other money goes through `formatRupees`. Trust issue for a finance app.
3. **Home hierarchy** [D]: the answer to "am I okay this month?" needs Budget tab + Home math. No balance anywhere in the app. Income card shows "No salary credit found this month" copy logic [S] but actual income on device is the declared salary [D, Budget].
4. **First-use experience** [S]: gate → spinner → empty. No onboarding, no privacy explanation beyond one sentence, no manual-start path.
5. **Transaction entry** [D]: missing date/account/currency, long category list, low-contrast fields; Review only offers 5 categories [D+S] while 19 exist.
6. **Single-currency/locale lock** [S]: `formatRupees` hard-codes `₹` and Indian grouping; `ParserFields` and parsers are India-bank specific (HDFC, Kotak, Slice, generic UPI). A "globally accessible" Tinward cannot rely on this.
7. **Accessibility** [D+S]:
   - Touch targets under 48dp: month pill ≈35dp tall, filter chips ≈33dp, eye toggle ≈34dp tall, Review prev/next 36dp, Budget × buttons visually ≈20dp.
   - `Icon(contentDescription = null)` used for decorative items; Home hero is a clickable `Column` with no role/label for "opens Spending"; sparkline has a description only on device ("Spending trend over the last 6 days").
   - Colour is the only expense/income signal in some places (salmon vs green; Review badge dot) though a +/− sign exists on Activity; Home recent-activity hides the sign when amounts are hidden.
   - Typography uses `sp` (scales) but fixed container heights exist (Home hero `height(300.dp)`, account cards `156.dp`, chip padding) → clipping risk at large font scale. **Not verified on device** (changing system font size is a settings change; I did not alter it).
   - No light theme / no system contrast option; dark only.
   - Not verified: TalkBack traversal order, focus order in sheets.

## Medium priority
8. **Chrome redundancy** [D]: wordmark + screen title on every tab; the Home triple-icon row; extra 50–110px before content on Activity/Spending/Budget/Review.
9. **Status-bar collision** [D]: content scrolls under the clock with no scrim (`02_home_scroll1`, `31_budget_scroll1`).
10. **Spending chart not quantitative** [D]: no axes/labels/tooltips; dead space between total and chart; category rows lack % share; categories not drillable.
11. **Jargon & raw strings** [D+S]: "Transfer · Excluded from spending", "Unmatched record · Excluded from totals", "Purpose unconfirmed", enum values (`EXPENSE`, `CARD`, `SMS`), parser text as merchant ("Kotak Bank A/c X9996 to …"), "Unknown" merchant everywhere.
12. **Month state per screen** [D+S] (Overview/Insights each own a `selectedMonth`).
13. **Budget logic legibility** [D]: Wants always ₹0; needs 74% bar red without explanation; × delete unconfirmed.
14. **Dialog copy** for statement import/reconciliation [S] is long and technical.
15. **Back behaviour** [D]: Back on any tab exits the app.
16. **Review privacy** [D]: SMS body (card suffix, phone numbers) displayed in full.

## Low priority
17. Doubled bullets in `<Bank> • •<last4>` [D]; lower-case "pm"; three date styles; `Locale.getDefault()` only partly used.
18. Month picker lacks "Today"; dots on every month are ambiguous [D].
19. Home hero label differs between builds (Spent this month / Recorded spending) – wording not consistent with Spending tab "Spent this month".
20. Eye toggle masks amounts only, per session [D+S].

## Strengths worth keeping
- **Explainability:** Transaction story timeline; "Excluded from spending" labelling; reconciliation notes [D+S].
- **Privacy-first copy and on-device SMS parsing** ("Nothing leaves your phone") [S] + masking toggle [D].
- **Identity-coloured category tiles** reused consistently across Home/Activity/Review/Spending/Add [D].
- **Tabular figures** (`fontFeatureSettings = "tnum"`) on amounts [S].
- Sensible spacing scale (`Spacing.kt`) and reusable components (see `DESIGN_SYSTEM_CURRENT.md`).
- Month selector with data indicators; privacy eye; clear empty-search message [D].

## Verification limits (stated honestly)
- Not exercised to avoid altering/exporting real data: saving an add-transaction, Budget add/delete, Export, statement import flow, "Edit category", Review confirm, Scan SMS.
- Not exercised due to settings rules: font scale, display size, light mode, language/locale, TalkBack.
- The installed app already had permission and data; permission-gate, loading and empty-Home states come from source only.
- Only one device size/density (720×1604, 320dpi); tablet/foldable behaviour is source-inferred (no `WindowSizeClass`, fixed widths: account card 260dp; Home hero 300dp).

---

## Kosh – Feature Matrix

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

---

## Kosh – Current Design System (as implemented)

Source: `apps/android/app/src/main/kotlin/dev/kosh/financetracker/ui/**`. Documented as-is; no changes proposed here. The Tinward brand (ink T + ochre square, paper #f7f5f0 / ink #121412 / ochre #b8801f, Instrument Serif + Manrope) is **approved and untouched**.

## Theme
- `KoshTheme` wraps `MaterialTheme(darkColorScheme, KoshTypography)` plus `KoshExtendedColors` (income, expense, accentText, comparison, warmHighlight, burntAmber, raisedSurface) via a `CompositionLocal`.
- **Dark only.** No light scheme, no dynamic colour, no day/night resources; manifest theme is `Theme.Material.Light.NoActionBar` (legacy; Compose overrides) and status/nav bars are forced to dark style (`MainActivity`).
- Material 3 BOM 2024.05, compileSdk 34, minSdk 26.

## Colour (`Color.kt`, "Kosh warm palette – 29 Sep 2026")
| Token | Hex | Used for |
|---|---|---|
| BaseBlack | #0B0D0F | app background |
| Graphite | #191B1D | `surface`, cards, bottom bar |
| RaisedGraphite | #252526 | raised cards, chips, inputs |
| PrimaryText | #F7F5F0 | primary text (**same as web "paper"**) |
| SecondaryText | #B9B6B1 | secondary text |
| Gold | #D5A64F | primary/accent, selected states, links |
| WarmHighlight | #FFBF5B | hero glow, spending bar, Food |
| BurntAmber | #7C330D | hero gradient, Fuel/Home |
| Divider | #3B3936 | outlines, tracks |
| Positive | #B7D5AE | income / decrease |
| Expense | #F1A292 | expenses |
| Comparison | #C8A7DB | transfers, income bar, Family |
| Category extras | Blue #7FAEDB, Teal #6FBFA3, Red #E08A7D, Pink #D98BB0, Indigo #9A93D9 | category tiles |
| ChartPalette | list of 5 | **unused** |

Relationship to the website: paper (#F7F5F0) already matches; Android ink (#0B0D0F) vs web ink (#121412), Android gold #D5A64F vs web ochre #B8801F (lighter, tuned for dark). Purple/pink/blue/teal/indigo category colours and the account-card gradients have **no equivalent** in the web palette.

Contrast spot-checks (WCAG formula, computed): PrimaryText on BaseBlack ≈ 17.9:1; SecondaryText on Graphite 8.5:1; Gold on Graphite 7.7:1; Expense 8.5:1 and Positive 10.8:1 on Graphite. Palette itself is accessible; issues are size/weight and targets, not colour.

## Typography (`Type.kt`)
- Family: **Manrope** (Google downloadable font via Play Services; weights 400/500/600/700; falls back to system sans if unavailable — a first-launch/offline risk). No serif / display family in the app.
- Scale: displayLarge 52/58 SemiBold (−0.5 tracking) · displaySmall 40/46 · titleLarge 28/34 · titleMedium 18/24 · bodyLarge 16/22 · bodyMedium 14/20 · labelMedium 13/18 · labelSmall 12/16. Other Material slots (headline*, bodySmall, labelLarge, title small) fall back to Material defaults (e.g. `bodySmall` is used by Home disclaimer text).
- Numbers: `fontFeatureSettings = "tnum"` applied ad hoc per Text (not in the theme).
- Handoff note in code: Home amount 48–56sp, screen titles 27–32sp, sections 17–19sp, merchant 15–16sp, support 12–14sp.

## Spacing, shape, elevation
- `Spacing`: xs 4, sm 8, ms 12, md 16, lg 24, xl 32, xxl 48 dp ("never use a raw dp value outside this list" – **not followed**: many raw dp values for sizes, radii, bars).
- Radii in use: 2 (bars), 12 (badge), 14 (chips/buttons/review rows), 16 (filter chips/review card), 20 (KoshCard, account card, search, month pill), 24 (bottom bar top). Seven values, no token.
- `KoshCard`: 4dp shadow (black 40%), vertical gradient (+5% white at top), 1dp border `Divider@50%`.
- `AccountCard`: 260×156dp, per-bank 3-stop gradient (hash of bank name), coloured glow shadow 12dp, gloss + vignette overlays.
- Bottom bar: Graphite surface, 24dp top radius, 2dp shadow, no ripple indicator pill, gold for selected.
- Icons: mix of Material Icons (Home, List, CheckCircle, ArrowBack, Search) and custom vector/Canvas icons (`InsightsIcon`, `CalendarIcon`, `ImportIcon`, `ChevronDownIcon`, `ic_visibility*`, category icons). Budget/Add/Export icons on device have no source.

## Reusable components (all in `ui/components`)
| Component | Role | Used by |
|---|---|---|
| `KoshCard` | Elevated surface shell | Home, Insights, Review |
| `KoshBottomBar` | 4/5-item nav with badge | MainActivity |
| `KoshWordmark` | Icon + "Kosh" text | every top-level screen (hard-coded text & drawable) |
| `MonthSelectorPill` + `MonthPickerSheet` | Month selection | Home, Insights (+ Budget device) |
| `CategoryIconTile` + `CategoryVisuals` (`colorFor`, `labelFor`, `REVIEW_PICKER_CATEGORIES`) | Category identity | Home, Activity, Review, Insights, Detail |
| `AccountCard` | Per-account gradient card | Home |
| `SpendSparkline` / `SpendTrendChart` | Mini/full trend (Canvas) | Home / Insights |
| `EmptyState` | Title + description | Activity, Insights, Review |
| `VisibilityToggle` | Eye icon | Home |
| `SegmentedBar` | Stacked bar | **unused** |
| `formatRupees()` | Indian grouping, ₹ | all except Detail |

**Not componentised (re-implemented per screen):** filter chip, progress bar (Home `ProgressBar`, Insights category bar – duplicated), transaction row (`RecentActivityRow` vs `TrailRow` – near duplicates), screen header (wordmark + title), primary button styling, radio row (`CategoryOptionRow`, Detail inline list), section title, dialog styling, date header, `NavArrow`.

## Hard-coded values (representative)
- Strings: every UI string is a Kotlin literal; `strings.xml` holds only `app_name`.
- Colours: Detail and Import dialog use `MaterialTheme.colorScheme` while others use `KoshColors.*` directly; `Color.White/Black/Transparent` in cards.
- Sizes: Home hero height 300dp, account card 260×156dp, icon tile 36/32/28 dp, buttons 36dp circles.
- Currency/locale: `₹`, Indian grouping, "SMS", "Bank SMS" copy.

## Responsive behaviour
- Phone-portrait only assumption; no `WindowSizeClass`, no landscape/tablet layouts; Home hero fixed height; account cards fixed width (fine in a horizontal list); `Scaffold` insets set to zero with per-screen `statusBars` handling. No orientation lock declared in manifest. Tablet/foldable and large-font behaviour **not tested** (see `UX_AUDIT.md`).

## Existing brand assets in the Android app
- Launcher: gold "K" mark on near-black (`ic_launcher_*`, monochrome layer present for themed icons). In-app mark: `ic_kosh_mark` tinted gold. These are **Kosh** assets, not the approved Tinward ink-T/ochre-square mark.

---

## Tinward – Design Opportunities (Editorial × Utility)

**These are observations and proposals for discussion, not designs.** Everything labelled *Proposal* is a new capability or change to be approved. The Tinward identity (mark, palette, Instrument Serif + Manrope) is taken as given and is not modified here. Evidence references point to `screenshots/`.

## Direction in one line
*Editorial* = confident typographic hierarchy, calm surfaces, restrained accent; *Utility* = fast capture, dense-but-legible numbers, honest data. Kosh already has the Utility bones (SMS ingestion, reconciliation, explainability); what it lacks is editorial restraint and some utility basics.

## Prerequisite decisions (before any design work)
1. **Pick the source-of-truth Android tree.** The phone has Budget/Add/Export that the repo lacks (see `SCREEN_INVENTORY.md`). Recover that code or consciously drop those features.
2. **Dark-only or dual theme?** Web is a light "paper" palette; the app is dark-only. Both appear viable under the brand, but the decision shapes every concept. (*Proposal*)
3. **Scope of "globally accessible":** currency + locale + non-India SMS sources, or just currency/locale formatting first?

## Opportunities by theme

### 1. Typography as the editorial layer
- Today: Manrope everywhere, ad-hoc `tnum`. *Opportunity:* serif display (Instrument Serif) for screen titles and the hero figure's context line, Manrope for data; make tabular figures a theme-level default. Reduces the "dashboard template" feel of `20_spending` / `30_budget`.
- Establish a short number hierarchy (hero / row / caption) and format **all** money through one function (fixes `₹1164` on `80_detail_story` and `(₹49800)` on `31_budget_scroll1`).

### 2. Replace the glow with paper-and-ink restraint
- The radial amber hero (`01_home_top`) and multi-colour account gradients are the least brand-aligned elements; the editorial palette suggests flat surfaces, hairline rules and a single ochre accent. Category colours could be reduced to a tonal set (the web already limits itself to paper/ink/ochre).
- Keep identity-coloured category tiles' *recognisability* (strength) but evaluate a calmer tonal treatment. (*Proposal*)

### 3. Home: answer "where do I stand?" first
- Combine what is currently split across Home, Spending and Budget: *spent so far · left to spend · what's pending review*, with one editorial sentence ("You've spent X of Y; 4 transactions are waiting") using real data.
- Account cards could show **balance** if the parser extracts `Bal Rs.` from SMS (present in the Review sample SMS) — new data capability (*Proposal*).
- Group Add / Import / Export into one clear affordance; move Review into a Home "inbox" row (already partially there as the gold chip).

### 4. Utility: fast capture
- Persistent add action on every tab; amount-first entry with a recent/frequent category row instead of 19 radios (`50_…`–`52_…`); date, account and currency fields defaulted but editable.
- Review: show all categories compactly, add *Skip* and *Not spending* options, and remember choices ("always for this merchant") — `MerchantCategoryMappingEntity` already exists. (*Proposal* for UI; data model exists.)

### 5. Honest, readable insights
- Quantitative chart: axes, tap-to-read value, month comparison; category share % and drill-down to Activity; collapse "Other/Uncategorized" prompts into a call-to-action (about 9 in 10 rupees of the sampled month sit in those two buckets).
- Keep "How Kosh understood this" – rename and promote as a core trust pattern ("Why this is here"), the strongest differentiator versus generic trackers.

### 6. Trust, tone and copy
- Replace parser/engine jargon ("unmatched overlap", "source records linked", "Purpose unconfirmed") with plain language; never display raw enums (`EXPENSE`, `CARD`).
- Redact sensitive digits in the Review SMS preview by default, with tap-to-reveal. (*Proposal*)
- Make privacy a visible, quiet feature: masking toggle persisted, an app-lock option (*Proposal*), "on-device only" statement in onboarding.

### 7. First-use and empty states
- Onboarding of 3 short steps (what Tinward reads, what stays on device, choose month/currency) followed by the permission request in context. Empty states with a single clear action and an alternative path (add manually / import statement). (*Proposal*)

### 8. Global readiness
- Currency + locale driven number/date formatting (replace `formatRupees` with a formatter parameterised by currency and locale); externalise strings to `strings.xml` for translation; avoid `₹`-only copy.
- Parser strategy for non-Indian banks is a separate research track (notifications, statements/CSV import) — out of design scope.

### 9. Navigation & structure
- Reduce chrome: drop repeated wordmark + large title on every tab; use a compact top bar with the Tinward mark only on Home.
- Add Settings (currency, language, SMS access, categories, export, about) — currently nonexistent.
- Share month state across tabs; consider 4 tabs (Home · Activity · Insights · Budget) with Review as an inbox on Home. (*Proposal*)
- Fix system-Back to return to Home before exiting; add status-bar scrim.

### 10. Accessibility baseline
- Min 48dp targets for chips, month pill, eye toggle, Review arrows, Budget ×; give composite rows semantic labels (merchant, amount, direction, date); test at 200% font scale (fixed-height hero & cards are risks); add light/contrast variants; confirm destructive Budget × with undo.

## Reuse vs rebuild (high level)
| Keep | Refine | Rebuild |
|---|---|---|
| SMS/statement pipeline, classifiers, reconciliation, Room, VMs, tests; Transaction-story concept; month picker concept; category tile concept; `Spacing` scale | Activity list & filters; Spending; Review; Detail; Add sheet; Month pill; EmptyState; Bottom bar | Home hero & account cards; Budget; onboarding/first-use; header system; theme/typography/token layer |

## Not in scope here
Moodboard, Home concepts, any new colours/fonts/logo, any code. Next steps (pending approval): Tinward mobile moodboard, then three distinct Home dashboard concepts that preserve real functionality (spend, income, review inbox, accounts, recent activity, month selection, privacy mask, add/import/export).

---

## Screenshot index (attach these files)
01_home_top, 02_home_scroll1, 61_home_amounts_hidden (Home); 60_month_picker; 10_activity, 11_activity_scroll, 12_activity_filter_transfers, 13_activity_search_empty; 20_spending, 21_spending_scroll; 30_budget, 31_budget_scroll1; 40_review, 41_review_scroll; 50_add_transaction, 51_add_transaction_scroll, 52_add_transaction_scroll2; 80_detail_story, 81_detail_details_tab.

## Suggested prompt
"You are a senior mobile product designer. Using the audit and screenshots, (1) write a Tinward mobile moodboard brief in the Editorial x Utility direction using only the fixed brand tokens, (2) propose three distinct Home dashboard concepts that keep real functionality (month spend, income, review inbox, accounts, recent activity, month selector, privacy mask, add/import/export, budget status), (3) list for each concept which existing components are reused and which new ones are proposals. Do not alter the logo, palette or fonts."
