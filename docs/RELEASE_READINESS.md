# Tinward Android: release readiness (Gate 4)

Date: 2026-11-08. Branch `claude/jovial-wright-e59e8c`. Everything below says what was **verified**, how, and what is **not**. Nothing here is legal advice; Google Play's current policies must be re-read before submission.

## 1. Verdict

**Not ready to publish yet.** The app is feature-complete for Free and for the Plus features listed in section 6, and the engineering checks below pass. The remaining blockers (section 2) are things only Google Play Console, a real subscription test, a signing key and some real-user testing can resolve. None of them can be settled from this repository.

## 2. Blockers (must be done by a person, in this order)

| # | Blocker | Why it blocks | What to do |
|---|---|---|---|
| 1 | **Play subscription does not exist** | Plus cannot be bought; purchase, restore, cancel, refund and expiry code has never run against Google Play | Create subscription `tinward_plus` (monthly and yearly base plans) in Play Console, upload to an internal test track, test with licence testers: buy, restore on a second device, cancel, refund, lapse, offline for more than 7 days |
| 2 | **SMS permission approval** | `READ_SMS` / `RECEIVE_SMS` are restricted; a declaration and review are needed and approval is not assumed | Submit the declaration (section 4). If refused, the app still works through manual entry and CSV import; decide whether to ship without SMS |
| 3 | **Target SDK and Billing library versions** | `targetSdk` is 34 and Play Billing is 7.0.0. Play raises the minimum target API and billing library version over time | Check Play's current minimums and raise both before submitting; re-run the full test suite and the on-device checks after |
| 4 | **Signing and identity** | The release build is unsigned unless the `KOSH_*` keystore variables are set. The package is still `dev.kosh.financetracker` | Decide the final package id (changing it makes a new app, with no in-place update from Kosh, and the existing Kosh install is signed with a different key anyway). Create or locate the upload key. Back up the key |
| 5 | **Privacy policy and store listing** | Play requires a policy URL and accurate Data Safety and store text | Publish a policy matching section 5; use the feature list in section 6 |
| 6 | **Real-user beta** | The spec requires Free and Plus beta testers and accounting validation on real bank messages | Run a closed test; the global SMS parser has only been run against written templates |

## 3. What was checked in this pass

| Check | Result | Evidence |
|---|---|---|
| Unit tests | **338 pass, 0 fail** | `testDebugUnitTest`: ledger invariants, parsers (28 regional fixtures), CSV, planning, timeline, rollover, review, debt, entitlement, security |
| Database upgrade | **Pass** | `DatabaseMigrationTest`: builds genuine version 7 and version 8 databases, upgrades to 14, Room validates the schema, every row survives, new columns default safely, unique index still works, soft delete works, reopen keeps new data |
| Ledger invariants | Pass | `AccountLedgerTest` (17) covers the spec's rules for transfers, cards, cash, refunds, adjustments, currencies |
| Performance with a long history | **Fixed, then pass** | 20,000 transactions: every calculation under 100 ms except ledger projection (0.7 s). The projection and transfer matching were quadratic (11.7 s at 40,000 rows); both are now indexed and a test fails if the curve stops being linear (10k to 40k ratio 3.8) |
| Lint (release) | **0 errors**, 59 warnings | One real error fixed (notification permission re-check). Warnings: outdated dependencies, harmless typos in Google's font certificate data, intentional `commit()` in the lock code (documented), one Compose parameter-order nit |
| Code shrinking (R8) | **Pass** | Release now shrinks. A `releaseCheck` build (same configuration, separate package and the debug key) was installed on the phone: first run, onboarding, PIN setup, encrypted database, account creation, transaction entry, Home, Plan and Activity all worked, no crash. Removed afterwards |
| Network access | **None, by design** | The merged manifest had `INTERNET`, pulled in by a Google telemetry library inside Play Billing. It is removed in the manifest; billing still connects through the Play Store app. Verified on the phone |
| Exported components | Pass | Only the launcher activity and the SMS receiver (requires `BROADCAST_SMS`) are exported by the app; framework services require system permissions |
| Backup | Pass | `allowBackup=false`; the only backup is the user's encrypted file |
| Logging | Pass | No `Log` calls in the app; no analytics or crash reporter; notification text is generic |
| Debug-only features | Pass | Plus switch, test reminder and text-size override are gated on the debuggable flag, which release builds do not set |
| Touch targets | Fixed | Choice chips and review buttons raised from 44 dp to 48 dp |
| Large text (2x) | Partly checked, 3 problems fixed | Home, Plan and Add screens at 2x system text. Fixed: tab labels breaking letter by letter, month selector wrapping, budget amounts colliding with labels, buttons breaking mid-word. Compact controls now cap at 1.3x to 1.5x; body text keeps growing |
| Light and dark | Checked | Plan screen in light mode (contrast fine); dark used throughout testing |
| Lock screen and screenshots | Verified earlier | Secure window flag blanks screenshots and the recents preview while locked |

## 4. Google Play SMS declaration (draft)

**Core functionality:** Tinward is a personal finance tracker. Its core function is to build a person's transaction history from the transactional SMS their banks send them, so they can see where their money went without connecting a bank account or sharing credentials.

**Why SMS and not another method:** banks in many countries send a text for each transaction; reading these on the device is the only way to capture them automatically without an account login or an aggregator. Users who decline can still add transactions by hand or import a CSV.

**How the permission is used:** the app reads incoming and existing messages only to detect bank and card transaction alerts. It ignores one-time passwords, promotions, reminders and failed payments, and does nothing with other messages.

**What happens to the data:** parsed fields and the original message text of recognised transaction alerts are stored only on the device, in a database encrypted with a key unlocked by the user's PIN. Nothing is uploaded. The app has no network permission and no account.

**In-app disclosure (shown before the permission request):** "Tinward reads bank SMS on this phone to build your transaction history. Nothing leaves your device." Also shown in Settings. The permission is requested only after this explanation and the app works without it.

## 5. Data Safety form (draft answers)

| Question | Answer |
|---|---|
| Does the app collect or share user data? | Collects: yes, on the device only. Shares: no |
| Data sent off the device | None. The app has no network access |
| Data types handled on device | Financial info (transactions, balances, budgets); Messages (SMS used to detect bank alerts); Device identifiers: none |
| Data encrypted in transit | Not applicable (nothing is transmitted) |
| Data encrypted at rest | Yes: SQLCipher database and encrypted values, key wrapped by the user's PIN |
| Can users request deletion | Yes: in-app delete, "Recently deleted" purge, and uninstalling erases everything; no server copy exists |
| Purchases | Subscriptions through Google Play Billing (Google handles payment data; Tinward does not see it) |
| Ads, analytics, tracking | None |

Note for the form: Google's billing library may send its own diagnostics through Google. With `INTERNET` removed this should not be possible from the app process, but re-check the declaration wording against the final merged manifest.

## 6. Accurate public feature list (for tinward.app and the store)

**Tinward Free**
- Local-only: no account, no sign-in, no server, no ads, no bank connection. Works offline. The app has no internet permission.
- Mandatory app lock with a 6-digit PIN (optional biometrics), data encrypted on the phone, auto-lock, hidden from the recents screen.
- Accounts and cards you create yourself: bank, cash, wallet, debit and credit cards, per-account currency, tracked balances (estimates, not live bank balances), transfers, card payments, balance corrections.
- Transactions: automatic capture from supported bank SMS where you allow it (tested on India-format messages and on written templates for other countries; English only), manual entry, general CSV import with column mapping and duplicate protection, edit, delete with undo, refunds, split categories, notes and labels, search and filters.
- Planning: monthly income, 50/30/20, one monthly budget plus 3 category budgets, bills and income schedules with reminders, one savings goal, "available to spend" with its working shown.
- Home and Insights: tracked accounts, bills due, 6-month income and spending chart, comparison with last month, where your money stands.
- Your data stays yours: CSV export, encrypted backup and restore, light and dark themes.

**Tinward Plus** (subscription through Google Play; price shown by Google Play for your country)
- Money Timeline: expected balance over 30, 60 or 90 days with every assumption shown, and what-if tests.
- Unlimited category budgets with rollover of unspent money.
- More than one savings goal.
- Financial Review: top merchants, category changes, spending pace, savings rate, share of income committed to bills.
- Debt planner for credit cards: payoff time, interest, highest-interest-first versus smallest-first, limit use by month.
- Custom rules: your own "if merchant contains / amount between / money in or out / this account, then set category and label" rules, in a priority order you can see, applied to new bank messages and imports and, when you choose, to existing transactions, with undo. Rules never change amounts or dates and never move money.
- If Plus ends: everything stays, Plus-only items pause, nothing is deleted, export and backup still work.

**Not included, so do not advertise:** rules that flag for review or mark recurring, rules that run on statement-file imports, custom budget periods, loan tracking, bank sync, web or iOS apps, cloud sync, multi-user, non-English SMS, any claim that SMS works for "all banks".

## 7. Known limitations (carry into the store text where relevant)

- Balances are calculated from your records and are estimates.
- A bare "$" in a message is interpreted by a setting (US, Australian, Canadian, Singapore, NZ or Hong Kong dollars).
- Parsers outside India are written against templates, not real captured messages.
- Forgetting the PIN cannot be recovered without an encrypted backup (there is no server to reset it).
- An SMS arriving after a CSV covering the same day was imported can create a duplicate.
- Reminders use a small date-only cache refreshed when the app is opened, covering the next 45 days.
- Custom rules match plain text only (no regex, no OR/NOT) and only set category and label.
- Statements in `.xls`/`.xlsx` are supported only for the three built-in banks.

## 8. Not yet verified

- Any purchase flow against Google Play.
- TalkBack and screen-reader behaviour (labels exist, nothing was exercised with TalkBack).
- Large text at 2x for every screen (Home, Plan, Add checked), and landscape or tablet layouts.
- Small-screen devices and Android versions other than the one test phone (Android 14 class, OnePlus).
- Battery and background behaviour over days; the daily reminder was exercised through a debug trigger, not a real 9 am run or after a reboot.
- Restoring a backup on a second phone.
- R8 with the legacy `.xls` statement import (Apache POI) and the Google font download path.
- Biometric unlock (needs a person at the sensor) and SMS received while locked.
- Beta with real users.
