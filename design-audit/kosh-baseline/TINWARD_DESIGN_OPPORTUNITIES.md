# Tinward – Design Opportunities (Editorial × Utility)

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
