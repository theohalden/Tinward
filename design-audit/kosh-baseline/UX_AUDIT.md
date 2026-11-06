# Kosh – UX Audit (cross-cutting)

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
