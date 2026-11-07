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
