# Kosh – Current Design System (as implemented)

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
