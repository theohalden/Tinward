# Tinward existing-app audit (Gate 0, Deliverable A)

Date: 2026-11-07. Evidence: repo at `apps/android` (branch `claude/jovial-wright-e59e8c`, HEAD `941805a`), the Kosh APK installed on the test phone (decompiled with jadx), on-device runs of the Tinward Preview build, and the earlier baseline in `design-audit/kosh-baseline/`. Items not verified are marked **unverified**.

## 1. Identity

| Item | Value | Evidence |
|---|---|---|
| Repo | monorepo `tinward`; Android app in `apps/android` | git |
| Package (repo) | `dev.kosh.financetracker`, v0.2.0 (versionCode 2); preview flavor `.preview` | `app/build.gradle.kts` |
| Package (phone) | `dev.kosh.financetracker`, v0.1.0 (versionCode 1), debug-flagged, DB schema v7 | APK / dexdump |
| Repo DB schema | Room v8 (`MIGRATION_7_8`) | source |
| minSdk / targetSdk | 26 / 34 | gradle |
| Signing | Phone APK signed with a key that is neither this Mac's debug key nor the release key, so a Tinward build cannot update Kosh in place | apksigner comparison |
| Installed alongside | Tinward Preview (`dev.kosh.financetracker.preview`) with its own data | adb |

The phone build and the repo are diverged lines, not old and new of one line. The device had Budget, Add Transaction and Export (UI) on the v7 data layer. The repo had source reconciliation and DB v8 but none of those UI features. **The Budget / Add / Export discrepancy is resolved:** those features were rebuilt in the repo from the decompiled APK (`FixedBudgetStore`, `BudgetRule`, `CsvExporter`, `AddTransactionViewModel`) and run on the phone. The original Kosh source was never found; the decompile is the only reference.

## 2. Feature status (as of HEAD)

Legend: V = verified on device, P = partial, M = missing, B = blocked/needs decision.

| Area | Status | Notes |
|---|---|---|
| Home, Activity, Plan, Accounts, Insights, Review, Detail, Settings, Add, onboarding (3 pages) | V | Tinward theme, light and dark, Appearance setting |
| Home uses Plan income; Coming up from fixed costs | V | commit `941805a` |
| SMS scan + live receiver | P | Works; parsers cover only HDFC, Kotak, Slice and a generic UPI pattern (India). Parser unit tests exist. Not globally applicable. |
| Statement import (HDFC xls, Kotak csv, Slice xlsx) | P | Bank-specific parsers, not a general CSV mapper. No column-mapping UI. |
| CSV export | P | Writes 11-column file and shares it; share flow not exercised on device |
| Budget | P | One monthly income, fixed-expense list, 50/30/20 breakdown. No per-category budgets, no rollover. |
| Accounts | P | Accounts are derived from `bank` + `accountSuffix` on transactions. There is no account entity, no opening balance, no manual create/edit/archive, no transfer legs. |
| Cards (debit/credit) | M | Only a `CREDIT_CARD_PURCHASE` / `CREDIT_CARD_PAYMENT` transaction type. No card record, limit, statement or due dates. |
| Categories | P | Built-in set and merchant-to-category mapping entity. No custom categories. |
| Currency | M | INR is hard-coded in formatting; no currency on transactions. |
| Recurring bills, reminders, goals | M | Fixed expenses are a static list, not schedules. |
| Search, filter, split | P | Verify; split is not supported by the model. |
| App lock (PIN/biometric) | M | None. No `BiometricPrompt`, no `FLAG_SECURE`, no lock on start. |
| Encryption at rest | M | Plain Room database and plain DataStore. |
| Backup / restore | M | `allowBackup="false"` (good: no cloud copy), but no user backup or restore at all. |
| Plus tier / Play Billing | M | No billing dependency, no entitlement flag. |

## 3. Security and privacy findings

1. **No app lock and no encryption at rest.** The financial database is plaintext; anyone with the unlocked phone can open the app.
2. **Raw SMS text is stored.** `Transaction.rawSourceText` keeps the original message in the database. The spec says not to retain raw financial SMS unnecessarily. This needs a decision (drop it, or keep only redacted fields) and a migration.
3. **Exported components.** `MainActivity` is exported (launcher; expected). `SmsReceiver` is exported but guarded by `BROADCAST_SMS` (correct).
4. **Backups.** `allowBackup="false"`, so no Android auto-backup leak. There is also no way for a user to recover data, which matters once a PIN exists.
5. **Permissions.** `READ_SMS` and `RECEIVE_SMS` are declared. Play restricts both; a declaration and review are required and approval is not assumed.
6. **Logging.** Not audited for raw SMS in logs (**unverified**).

## 4. Tests and build

- 24 unit-test files under `app/src/test` (parsers, reconciliation, classifiers, calculators, formatting). No instrumentation tests, no UI tests, no migration test, no ledger-invariant test for the spec's ten rules.
- Debug and preview builds pass; unit tests passed at the last run. Release build not attempted (needs the signing env vars).
- Gradle on this Mac needs `JAVA_HOME` (Homebrew JDK 17) and `local.properties` (`sdk.dir`); both are untracked.

## 5. Ordered gaps (by dependency, not time estimates)

1. Secure foundation: PIN KDF + key wrapping, encrypted DB, biometric unlock, lifecycle lock, recents/screenshot protection, throttling.
2. Backup and restore: encrypted, versioned, with its own passphrase; also needed before any lock ships, since there is no PIN reset.
3. Data model: Account and Card entities with opening balances, currency per transaction, transfer links, adjustments; migration from v8 that preserves all existing rows.
4. Ledger invariant tests for the ten rules before more UI is built on them.
5. Raw-SMS retention decision.
6. Recurring schedules, reminders, per-category budgets (Free limit of 3).
7. Generic CSV import with column mapping.
8. SMS parser coverage beyond India, with fixtures per region.
9. Plus: billing, entitlement flag, timeline, rules, scenarios, goals. Only after Free is complete.
