# Kosh – Navigation Map (baseline)

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
