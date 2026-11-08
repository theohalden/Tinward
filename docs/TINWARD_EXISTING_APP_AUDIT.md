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
| Home, Activity, Plan, Accounts, Insights, Review, Detail, Settings, Add, onboarding (3 pages) | V | Tinward theme, light and dark, Appearance setting. **Slice 6:** Home shows real tracked accounts and cards (not message-derived summaries), bills and card payments due in two weeks, available to spend; Insights adds a 6-month income/spend chart, comparison with the previous month and a tracked net-worth card. |
| Home uses Plan income; Coming up from fixed costs | V | commit `941805a` |
| SMS scan + live receiver | P | **Slice 5.** India parsers (HDFC, Kotak, Slice, UPI) unchanged, plus a generic English-language parser for other countries and currencies (28 representative templates across US, Canada, UK, euro area, Singapore, UAE, Australia, NZ, Japan, India). Not captured from real banks, so this shows pattern coverage, not support for any named bank. Non-English messages are not parsed. OTP, offers, scheduled, failed and balance-only messages are left unread. Scan report (counts only) in Settings. |
| Statement import (HDFC xls, Kotak csv, Slice xlsx) | V | Bank-specific importers unchanged. **Slice 5 adds a general CSV import:** any bank's CSV, preamble skipped, columns guessed (English plus German, French, Spanish, Italian, Portuguese, Dutch headers) and adjustable, one amount column or debit/credit columns, sign convention, date format and decimal separator detected and adjustable, target account, a preview with new / already-there / unreadable counts, duplicate protection. Verified on device with a US-style and a German-style file. Excel files (.xls/.xlsx) other than the three supported banks are not read. |
| CSV export | P | Writes 11-column file and shares it; share flow not exercised on device |
| Budget | V | **Slice 4.** Monthly income and 50/30/20 (kept), plus one overall monthly budget and up to 3 category budgets (free-plan limit enforced in the repository and the UI), with remaining, near-limit and over-budget states. Verified on device. Rollover and custom periods are Plus. |
| Accounts | V | **Slice 2.** Real `accounts` table (bank, cash, wallet, other). Create, edit, archive/restore, reorder (repository), opening balance, per-account currency, transfers as two linked legs, balance correction as an ADJUSTMENT entry. Existing accounts discovered from messages on first unlock. Verified on device: migration 8→9 on real data, discovery, correct balance, pay card. |
| Cards (debit/credit) | P | **Slice 2.** Credit card: owed amount, limit, available, utilisation %, statement and due day, payments as transfers (not spending). Debit card linked to a bank account, no balance of its own. Verified on device: credit card creation and payment. **Missing:** due-date reminders, SMS-to-card matching beyond last-four, debit-card flow on device. |
| Categories | P | Built-in set and merchant-to-category mapping. **Slice 3 adds custom labels** (the user's own wording on a transaction, searchable, reusable) that sit on top of a built-in category; totals, budgets and rules still use the built-in category. Fully custom categories with their own totals are not built: the category enum is used by charts, colours and the 50/30/20 rules and needs a wider change. |
| Currency | P | **Slice 2.** Currency on every transaction and account; main-currency setting; Home, Insights and Plan total in the main currency only; transfers between currencies refused; no FX. **Missing:** currency step in onboarding, non-India SMS parsing (Slice 5). |
| Recurring bills, reminders, goals | V | **Slice 4.** Bill and income schedules (weekly, monthly, yearly; month-end clamping), mark paid with or without recording, automatic settling when a matching payment appears, daily reminders, one savings goal, and an "available to spend" estimate with its working shown. Verified on device, including a real notification. Reminders need the notification permission (asked in context). |
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

## 9. Slice 4 record: bills, budgets, goal, available to spend (2026-11-08)

**Schema (v10 → v11, `MIGRATION_10_11`).** New tables `recurring_items`, `budgets` (category null = overall) and `savings_goals`. The old fixed-expenses list (encrypted DataStore) was carried over once as monthly bills due on the last day of the month, because it had no due date; they stay inside "available to spend" until the user sets the real date. The old DataStore values are left untouched.

**Schedules are expectations, not transactions.** A schedule never posts a transaction by itself. The user confirms ("record it" adds a ledger entry; "just move on" only advances the schedule), or a real payment within five days, in the same direction and currency, within 5% of the amount, and either named like the bill or exactly equal, settles that occurrence automatically.

**Available to spend** = tracked balances of asset accounts, minus credit-card debt (a card in credit adds), minus unpaid bills due by month end, minus money set aside for the goal. Income that has not arrived is not counted. Only the main currency is used. Accounts that show zero are flagged because the estimate depends on them.

**Reminders and the lock.** The daily worker cannot open the encrypted database, so while the app is unlocked it writes only due dates and lead times (no names, amounts or accounts) to a small cache; the notification text is generic ("A bill is coming due") and private on the lock screen. Limitation: the cache is refreshed only when the app has been opened, so a bill added or settled on another day is not reflected until then (it covers 45 days ahead).

**Tests.** 190 unit tests pass (18 planning logic, 4 reminder timing, earlier suites). Verified on device: v11 upgrade, available-to-spend matches a hand calculation, budgets and the 3-category limit, bill carry-over, mark paid (record) moving the schedule on, goal reducing available by the amount set aside, and a real reminder notification.

**Bugs found and fixed while testing.** The card line in the working showed "− ₹1,500" for a card in credit (now shows its true effect); the category dialog was unusable with the keyboard up (now scrolls); mark-paid now settles the occurrence shown, and only the next occurrence per schedule has a button.

**Not verified / open.** The 10→11 migration has no automated test. The reminder worker was exercised through a debug "send test" button, not through a real 9 am daily run or after a reboot. Income schedules are tracked but the planned-income figure on Home still comes from the single "monthly income" setting. Editing a schedule's account does not yet move past transactions. No multi-goal, rollover or custom budget periods (Plus). Weekly schedules are tested in logic only.

## 10. Slice 5 record: import and SMS (2026-11-08)

**Raw SMS retention decision.** Kept, deliberately. The full message text of each recorded SMS stays in the encrypted database. It is used by the "why it is here" screen and, more importantly, by integrity rules that read it (ignoring declined/failed/scheduled messages, spotting payments to a financial account, matching SMS to statements). Erasing it would silently change totals: failed or declined transactions could start counting as spending. There is no logging of message text anywhere (no `Log` calls in the app), the text is never sent off the phone, it is protected by the PIN-derived key, and it is included only in the encrypted user backup. Settings says this in plain words. A future option could store only the fields those rules need.

**Global SMS parsing.** New `GenericBankParser` (last in the registry) with `MessageMoney` (symbols, ISO codes, per-country number formats) and `MessageNoise` (OTP, promotional, and failure wording take priority over amounts). It records only when it finds an amount, a clear direction and a completed-transaction cue. A bare "$" is ambiguous (US, Australian, Canadian, Singapore, NZ, HK dollars), so it is held neutral and resolved by a setting ("Dollar sign on messages", defaulting to the main currency if that is a dollar, else USD); explicit forms (S$, A$, NZ$, USD) are always honoured. New accounts discovered from messages take the messages' currency. Limits: English only; the same last four digits at two banks with no bank name merge into one account; a message with two real amounts takes the first non-balance amount.

**CSV import.** Pure logic in `core/csv` (reader, header and column guessing, strict date parsing that rejects 31/02, locale-aware numbers, duplicate planning) with `CsvImportRepository` writing rows as a new source type `CSV` (never treated as bank-verified, never superseding SMS). A row is a duplicate if the account already has an entry with the same direction and amount within a day, one-to-one, including a linked debit card's spending. Row ids are stable, so re-importing the same file adds nothing and a deleted row stays deleted. Credits are not counted as income unless the description reads like salary (now also in German, French, Spanish, Italian, Portuguese and Dutch). Limits: an SMS that arrives after a CSV covering the same day was imported can duplicate it; CSV only (no .xls/.xlsx for unknown banks); 5 MB cap; UTF-8 text.

**Tests.** 240 unit tests pass (28 SMS fixtures, 21 CSV, earlier suites). Verified on device: preamble and column auto-detection, the day-first/month-first ambiguity warning with a visibly wrong preview until corrected, duplicate detection against a linked debit card, bad rows reported with line numbers, re-import finding everything, comma-decimal and semicolon file understood automatically, imported entry shown as "Imported file".

**Not verified / open.** No real bank SMS from outside India was available, so the global parser is unproven against real wording. The live SMS path (receiver) with the dollar setting was compiled but not exercised. The 11→11 schema is unchanged (no migration in this slice). Matching an SMS against a CSV row is not attempted.

## 11. Slice 6 record: Home and Insights on real data (2026-11-08)

**Home.** The accounts strip now comes from real accounts and cards (tracked balance, card owed and available), tapping opens the account, with "tracked from your messages and entries, not live bank balances" beneath. "Coming up" merges bills and credit card payments due in the next two weeks. The card due day also feeds the daily reminder cache (three days' notice, generic text). The old message-derived account cards were removed.

**"Left to spend" fix.** Found on the phone: a single imported salary replaced the user's planned income as the basis, turning a healthy month into "over by ₹41,468". The basis is now the planned income, unless actual income is higher (`IncomeBasisRule`, tested); with no plan, recorded income is used. The caption says how much has been received so far.

**Insights.** Six months ending at the selected month (income and spending bars, empty months show nothing), the average over active months, this month against last (a percentage only when the earlier month has something to compare with), and where tracked money stands (accounts, card debt or credit, net) with the usual "estimate, not a bank balance" wording. Everything is in the main currency only. Deliberately not built, because the product spec reserves them for Plus: merchant comparisons, spending velocity, category acceleration, savings rate.

**Tests.** 251 unit tests pass (6 trend/comparison/card-due, 5 income basis, earlier suites). Verified on device: Home accounts strip, Insights chart, comparison and standing cards, the hero fix.

**Not verified / open.** Card payment due rows on Home and the reminder entry for a card were verified by unit test only (the test card has no due day and is in credit). Dark mode and large-font layouts of the new cards were not checked. The chart has no tap-to-inspect and no accessibility description beyond text. Insights has no per-account filter.
