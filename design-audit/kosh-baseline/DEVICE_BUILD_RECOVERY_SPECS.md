# Recovered specs for the device-only features

Source: jadx decompile of the installed APK (identical to a local backup APK). Logic only. No user data, no DataStore or database contents were read.

## Add transaction (`feature/addtransaction`)
- Bottom sheet from the "+" on Home. State: direction (Spent = DEBIT, Received = CREDIT), amount text, optional label, selected category.
- Save is rejected unless amount parses as a decimal greater than 0. A category must be chosen (Save looks inactive until then).
- Saved as `TransactionSource.MANUAL`, timestamp = **now** (no date/time picker), type EXPENSE for Spent and INCOME for Received, payment method UNKNOWN, confidence 1.0, merchant = the optional label (blank becomes null).
- Category list = all 19 categories, vertical radio rows.
- Design implication: no date, account or currency choice exists; back-dating a transaction is impossible.

## Export (`feature/export`)
- Export icon on Home writes `kosh-export-<yyyy-MM-dd>.csv` and opens the Android share sheet (`ACTION_SEND`, `text/csv`).
- Columns: Date (`yyyy-MM-dd HH:mm:ss`), Amount, Direction, Type, Category (display label, "Uncategorized" if none), Merchant, Bank, Account, Payment Method, Source, Notes. RFC-style quoting.
- It exports every transaction, with no month scope, no format choice and no warning that it contains personal data.

## Budget (`feature/budget`, `data/budget`, `core/finance`)
- Data: Preferences DataStore with two keys, `monthly_income` (a string) and `fixed_expenses_json` (a JSON array of `{id, label, amount}`). Nothing seeded in code. The income is declared by the user, not derived from transactions.
- Operations: set income, add expense, remove expense (no edit, no confirmation in the logic).
- Screen: "Fixed Monthly Expenses" card (income, items with delete, total fixed, left after fixed, "+ Add fixed expense") and a "50/30/20 Rule" card.
- Rule maths (`FinanceCalculator.budgetRuleForMonth`): spending by category is split into
  - Needs: Groceries, Bills, Rent, Fuel, Health, Education, Travel
  - Wants: Food, Shopping, Entertainment, Subscriptions, Personal Care, Technology, Home, Fees
  - Unclassified: everything else (Other, Uncategorized, Family, Business)
  and compared with declared income; targets 50/30/20.
- Observation (unverified): on the device the Needs bar value equalled the fixed-expense total while Wants showed 0, so the displayed Needs may come from the fixed list rather than from `budgetRuleForMonth`. Not confirmed in the decompiled UI code.
- Design implication: the user's mental model (fixed costs, what is left, 50/30/20) is the strongest "utility" feature in the app, but it is disconnected from the Spending and Activity data.
