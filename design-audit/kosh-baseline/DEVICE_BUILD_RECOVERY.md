# Device build vs repo source: recovery findings

Read-only investigation. No source files were changed, nothing was installed on the phone, nothing was committed.

## What the installed build is
- Package `dev.kosh.financetracker`, v0.1.0 (versionCode 1), last updated 2026‑10‑09.
- An earlier backup of this exact APK exists locally (`kosh-original-backup/original-installed.apk` in the Codex visualizations folder). SHA‑256 of that backup **equals** the APK currently on the phone (`391537986f19…ee69836`), so the backup is the audited build.
- The same backup folder holds `original-data.tar` (the app's database and DataStore files). It contains personal financial data. It was **not opened or extracted**, and should stay out of any shared material.

## What exists only in the device build (from the dex class list, 669 app classes)
| Package in APK | Classes | In repo? |
|---|---|---|
| `feature/budget` | 48 | No |
| `feature/addtransaction` (sheet, ViewModel, state) | 28 | No |
| `feature/export` (`CsvExporter`, `ExportViewModel`) | 12 | No |
| `data/budget` (`FixedBudgetStore`, `FixedExpenseItem`; Preferences DataStore `fixed_budget`) | 13 | No |
| `data/settings` (settings DataStore) | 7 | No |
| `core/finance/BudgetRuleBreakdown` (50/30/20 maths) | 1 | No |

Budget data lives in a DataStore (`fixed_budget.preferences_pb`), not in Room, so the Room schema (v8 in repo) is not the full persisted state.

## What exists only in the repo
The repo (monorepo import, 2026‑11‑05) has the newer data layer that the device build lacks: source reconciliation (`SourceReconciliation`, `TransactionSourceLink`, DB v8), confirmed classification, transfer/income reconcilers and their copy ("Excluded from totals", provisional-totals notes). So the two trees are **diverged lines**, not old and new of one line:
- Device = UI features (Budget, Add, Export, settings store) on an older data layer.
- Repo = reconciliation/data-integrity work without those UI features.

Searches found no Budget source anywhere in git (all branches, remotes, `-S` history search) and no source directory under `/Users/echo/Apps`. Dangling git trees exist but were not unpacked. Temp folders `/private/tmp/kosh-*` also hold no Budget source.

## Options to get readable source back
1. **Decompile the APK** with jadx (not installed; needs a download). Output is approximate Java (Compose lambdas are noisy), good for logic, strings, structure and `BudgetRuleBreakdown` maths, and a poor base to paste into the repo.
2. **Rebuild the three features from the audit + decompiled reference** on top of the repo's newer data layer (recommended path if the repo is the source of truth). Screenshots `30_`, `31_`, `50_`–`52_` and the class list define the behaviour.
3. **Check other machines or backups** for the original Kosh repository that built the device APK (the `.codex` folder name suggests an earlier tool built it).
4. Drop Budget/Add/Export from the Tinward scope.

## Constraints that still apply
Putting recovered code into `apps/android` would be a production source edit, which this audit excludes. Recovered output should stay outside the repo (for example in the shared notes folder) until you approve.
