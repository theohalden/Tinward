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
| Accounts | V | **Slice 2.** Real `accounts` table (bank, cash, wallet, other). Create, edit, archive/restore, reorder (repository), opening balance, per-account currency, transfers as two linked legs, balance correction as an ADJUSTMENT entry. Existing accounts discovered from messages on first unlock. Verified on device: migration 8→9 on real data, discovery, correct balance, pay card. |
| Cards (debit/credit) | P | **Slice 2.** Credit card: owed amount, limit, available, utilisation %, statement and due day, payments as transfers (not spending). Debit card linked to a bank account, no balance of its own. Verified on device: credit card creation and payment. **Missing:** due-date reminders, SMS-to-card matching beyond last-four, debit-card flow on device. |
| Categories | P | Built-in set and merchant-to-category mapping. **Slice 3 adds custom labels** (the user's own wording on a transaction, searchable, reusable) that sit on top of a built-in category; totals, budgets and rules still use the built-in category. Fully custom categories with their own totals are not built: the category enum is used by charts, colours and the 50/30/20 rules and needs a wider change. |
| Currency | P | **Slice 2.** Currency on every transaction and account; main-currency setting; Home, Insights and Plan total in the main currency only; transfers between currencies refused; no FX. **Missing:** currency step in onboarding, non-India SMS parsing (Slice 5). |
| Recurring bills, reminders, goals | M | Fixed expenses are a static list, not schedules. |
| Search, filter, split | V | **Slice 3.** Search covers merchant, note, label, category and amount. Filters: type, period, account, category (a debit card's spending counts under its bank). Split categories (stored as text, validated to add up, counted by part in totals). Verified on device. |
| App lock (PIN/biometric) | P | **Slice 1.** Mandatory PIN (6+ digits or passphrase, weak PINs rejected), PBKDF2-SHA256 310k + AES-GCM key wrap, escalating throttle (never wipes), lock on cold start and after auto-lock timeout, `FLAG_SECURE` on lock screens. Verified on device: setup, weak-PIN rejection, wrong PIN, unlock, auto-lock at 36s, no lock at 5s. **Biometric unlock is built but unverified** (needs a person to enrol/touch the sensor). |
| Encryption at rest | V | **Slice 1.** SQLCipher under Room (raw 256-bit key); budget values AES-GCM under the same key. Existing plaintext DB converted with row-count verification before the plaintext is shredded; verified on device (file header no longer `SQLite format 3`, same figures after). |
| Backup / restore | V | **Slice 1.** User-initiated `.twbak` (AES-256-GCM, PBKDF2 passphrase separate from the PIN, header authenticated). Restore re-checks the PIN, validates the database before touching anything, rolls back on failure, relaunches the app. Verified on device: create, wrong passphrase rejected, restore replaced a changed value, app relaunched, PIN works afterwards. Cross-device restore is by design but **untested on a second phone**. |
| Plus tier / Play Billing | M | No billing dependency, no entitlement flag. |

## 3. Security and privacy findings

1. ~~No app lock and no encryption at rest.~~ **Addressed in Slice 1** (see section 6).
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

## 6. Slice 1 record: app lock, encryption, backup (2026-11-07)

**Design.** A random 256-bit data key (DEK) encrypts the database (SQLCipher) and budget values (AES-GCM). The DEK is stored only wrapped under a key derived from the PIN (PBKDF2-HMAC-SHA256, 310,000 iterations, random salt); a wrong PIN fails the GCM tag. The PIN is never stored. Biometric unlock wraps the same DEK under an Android Keystore key that requires a strong biometric per use and is invalidated when biometrics change; the PIN stays the root credential.

**Schema/data migration.** No Room schema change (still v8). The plaintext `finance-tracker.db` is copied table by table into a SQLCipher file, row counts are compared, then the plaintext (and `-wal`/`-shm`) is overwritten and deleted. A crash at any step is recovered by the next unlock. (SQLCipher's ATTACH + `sqlcipher_export` was tried first and failed because ATTACH is per-connection and the connection pool does not guarantee one connection.) Budget DataStore values written before the lock existed are re-saved encrypted after first unlock.

**Background SMS while locked.** `SmsReceiver` no longer touches the database when locked. It records only a flag; the message text is never staged. After the next unlock the normal de-duplicating inbox scan runs. Limit: a message deleted from the inbox before the user unlocks is missed.

**Honest limits.** The lock gates the UI and the key; once unlocked the database stays open until the process ends (it is not closed on every re-lock). The throttle and auto-lock use wall-clock time, so changing the phone clock can shorten a wait. A rooted or compromised device, malware or a weak PIN defeats local protection. There is no PIN reset: only an encrypted backup restores data after a forgotten PIN.

**Tests.** `VaultCoreTest` (10) and `BackupCodecTest` (6) plus the existing suite: 130 unit tests pass. Covered: key wrap round trip, wrong PIN, throttle escalation and persistence across process death, PIN change keeps data, weak PIN rejection, tampered key fails closed, backup round trip, plaintext absent from file, wrong passphrase, flipped byte anywhere, truncation, non-backup input.

**Not verified.** Biometric enrol/unlock; SMS arriving while locked; reboot behaviour; restore onto a second device; large databases (tested with 33 transactions); TalkBack on the lock screen; the "hide in recents" switch turned on (lock screens were confirmed hidden from screenshots); the setup crash-recovery paths other than "setup interrupted before conversion".

## 7. Slice 2 record: accounts, cards, currency, ledger (2026-11-08)

**Schema (Room v8 → v9, `MIGRATION_8_9`).** New `accounts` table; `transactions` gains `currency` (NOT NULL DEFAULT 'INR') and `transferGroupId`. Existing rows keep all data. `TransactionType` gains `ADJUSTMENT` (stored by name, so older rows are unaffected). The existing `transactions.accountId` column, unused until now, is the link.

**Discovery.** After unlock, `AccountLinker` attaches rows that have a bank and last-four but no account. One matching account is reused, none creates one, an ambiguous match is left alone. This is a deliberate deviation from "ask the user on ambiguity" for the *create* case, so existing users keep seeing their accounts; ambiguous matches are never guessed.

**Opening balance rule (`AccountBootstrap`).** If a message or statement reported a real balance, tracked balance is anchored to it. If none ever did, the account starts at zero from today and the screen asks the user to set the real balance. This replaced a first version that summed partial history and produced a misleading −₹96,244 on a real account.

**Ledger (`AccountLedger`, pure, 17 tests).** Asset balance = opening + net; card owed = opening − net; debit card moves its linked bank only; transfers, card payments and cash withdrawals are neutral to income and spending; refunds and adjustments reverse or correct without counting as income; foreign-currency rows never enter another currency's balance; archived accounts leave net worth; rows before the opening date are already inside the opening amount.

**Tests.** 152 unit tests pass (17 ledger, 4 bootstrap, the rest earlier).

**Not verified / open.** Debit-card creation and spending on device; editing an account on device; reorder UI (repository only); transfer between two currencies is refused in code and tested but not shown on device; the migration was exercised on one real database (restored v8 backup) and has no automated migration test; large histories; reminders for card due dates (Slice 4); non-India SMS parsing and raw-SMS retention (Slice 5). The Add-transaction account picker compiles but was not exercised on device.

## 8. Slice 3 record: transactions (2026-11-08)

**Schema (v9 → v10, `MIGRATION_9_10`).** Four nullable columns on `transactions`: `deletedAtEpochMillis` (soft delete), `refundOfId`, `splitsJson`, `customLabel`. No data is rewritten.

**Edit.** Entries you typed in: everything editable. Imported (SMS, statement) rows: amount, date and source text are never changed; only category, note, label, split and account. Transfer legs, adjustments and card payments are labelling-only because their legs are linked.

**Delete.** Soft delete with an Undo snackbar and a "Recently deleted" list (restore, or empty for good). Both legs of a transfer go together. Imported rows are kept as tombstones so a rescan cannot resurrect them. Emptying the list lets a rescan re-import a deleted bank SMS (stated in the dialog).

**Refunds.** A refund is a linked `REFUND` entry (`refundOfId`). It reduces the month's spending and its category (not income), cannot exceed what was paid, and scales a split proportionally. Rule applies to every `REFUND` row, including any produced by the SMS classifier.

**Bug fixed.** The automatic income and transfer reclassification also ran over manually added entries (a manual "Received" without a salary-like description was demoted to Unknown). Manual rows are now exempt.

**Bug fixed.** Editing an entry on an archived account silently detached it; the account now stays selectable.

**Tests.** 168 unit tests pass (9 refund/split, 7 filter, earlier suites).

**Not verified / open.** The v9→v10 migration was exercised only on one real database and has no automated test. Full custom categories (see above). Custom labels are not yet a managed list (no rename or delete). Search is a substring match, not fuzzy. A split on an imported row was not exercised on device. The ₹30 refund created before the proportional-split fix still carries the original's full split on the Preview app (totals ignore a mismatched split).
