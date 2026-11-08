package dev.kosh.financetracker.core.finance

import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.core.model.PaymentMethod
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionSource
import dev.kosh.financetracker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** One test per accounting rule in the product spec (section 5, "Ledger invariants"). */
class AccountLedgerTest {
    private val t0 = Instant.parse("2026-01-01T00:00:00Z")
    private val t1 = Instant.parse("2026-02-10T10:00:00Z")
    private fun d(v: String) = BigDecimal(v)

    private val bank = Account(1, "HDFC", AccountType.BANK, openingBalance = d("1000"), openingDate = t0)
    private val cash = Account(2, "Cash", AccountType.CASH, openingBalance = d("100"), openingDate = t0)
    private val card = Account(3, "Visa", AccountType.CREDIT_CARD, openingBalance = d("200"), openingDate = t0, creditLimit = d("5000"))
    private val debit = Account(4, "Debit", AccountType.DEBIT_CARD, openingDate = t0, linkedAccountId = 1)
    private val all = listOf(bank, cash, card, debit)

    private fun tx(account: Account?, dir: TransactionDirection, amt: String, type: TransactionType, cur: String = "INR", at: Instant = t1, group: String? = null) = Transaction(
        timestamp = at, amount = d(amt), direction = dir, type = type, category = null, merchant = "m", rawMerchant = "m",
        accountId = account?.id, accountSuffix = null, bank = null, paymentMethod = PaymentMethod.UNKNOWN,
        source = TransactionSource.MANUAL, sourceMessageId = null, rawSourceText = null, confidence = 1.0, notes = null,
        createdAt = at, currency = cur, transferGroupId = group,
    )

    private fun amountOf(list: List<Transaction>, a: Account) = AccountLedger.positions(all, list).first { it.account.id == a.id }.amount

    @Test fun `opening balance only`() = assertEquals(d("1000"), amountOf(emptyList(), bank))

    @Test fun `1 internal transfer moves both accounts and is neither income nor expense`() {
        val legs = AccountLedger.transfer(bank, cash, d("300"), t1).getOrThrow()
        assertEquals(d("700"), amountOf(legs, bank))
        assertEquals(d("400"), amountOf(legs, cash))
        val summary = FinanceCalculator.summaryForMonth(legs, YearMonth.of(2026, 2), ZoneId.of("UTC"))
        assertEquals(BigDecimal.ZERO, summary.income); assertEquals(BigDecimal.ZERO, summary.expense)
        assertEquals(1, legs.map { it.transferGroupId }.distinct().size)
    }

    @Test fun `2 debit card purchase reduces only the linked bank, no card balance`() {
        val list = listOf(tx(debit, TransactionDirection.DEBIT, "250", TransactionType.EXPENSE))
        assertEquals(d("750"), amountOf(list, bank))
        assertEquals(d("750"), amountOf(list, debit))   // shows the linked balance, not its own
        assertEquals(d("100"), amountOf(list, cash))
    }

    @Test fun `unlinked debit card moves nothing`() {
        val orphan = debit.copy(linkedAccountId = null)
        val pos = AccountLedger.positions(listOf(bank, orphan), listOf(tx(orphan, TransactionDirection.DEBIT, "50", TransactionType.EXPENSE)))
        assertEquals(d("1000"), pos.first { it.account.id == 1L }.amount)
        assertNull(pos.first { it.account.id == 4L }.amount)
    }

    @Test fun `3 credit card purchase raises what is owed and leaves the bank alone`() {
        val list = listOf(tx(card, TransactionDirection.DEBIT, "500", TransactionType.CREDIT_CARD_PURCHASE))
        assertEquals(d("700"), amountOf(list, card))
        assertEquals(d("1000"), amountOf(list, bank))
        val pos = AccountLedger.positions(all, list).first { it.account.id == 3L }
        assertEquals(d("4300"), pos.available)
        assertEquals(14, pos.utilizationPercent)
    }

    @Test fun `4 card payment lowers bank and card debt and is not an expense`() {
        val legs = AccountLedger.transfer(bank, card, d("150"), t1).getOrThrow()
        assertEquals(d("850"), amountOf(legs, bank))
        assertEquals(d("50"), amountOf(legs, card))
        assertTrue(legs.all { it.type == TransactionType.CREDIT_CARD_PAYMENT })
        assertEquals(BigDecimal.ZERO, FinanceCalculator.summaryForMonth(legs, YearMonth.of(2026, 2), ZoneId.of("UTC")).expense)
    }

    @Test fun `5 cash withdrawal moves bank to cash, not spending`() {
        val legs = AccountLedger.transfer(bank, cash, d("200"), t1).getOrThrow()
        assertTrue(legs.all { it.type == TransactionType.CASH_WITHDRAWAL })
        assertEquals(d("800"), amountOf(legs, bank)); assertEquals(d("300"), amountOf(legs, cash))
        assertEquals(BigDecimal.ZERO, FinanceCalculator.summaryForMonth(legs, YearMonth.of(2026, 2), ZoneId.of("UTC")).expense)
    }

    @Test fun `6 refund on a card lowers what is owed`() {
        val list = listOf(
            tx(card, TransactionDirection.DEBIT, "500", TransactionType.CREDIT_CARD_PURCHASE),
            tx(card, TransactionDirection.CREDIT, "500", TransactionType.REFUND),
        )
        assertEquals(d("200"), amountOf(list, card))
    }

    @Test fun `8 reconciliation adjustment brings tracked to actual and is not income or expense`() {
        val adj = AccountLedger.adjustment(bank, tracked = d("1000"), actual = d("1250"), at = t1)!!
        assertEquals(TransactionType.ADJUSTMENT, adj.type)
        assertEquals(d("1250"), amountOf(listOf(adj), bank))
        val s = FinanceCalculator.summaryForMonth(listOf(adj), YearMonth.of(2026, 2), ZoneId.of("UTC"))
        assertEquals(BigDecimal.ZERO, s.income); assertEquals(BigDecimal.ZERO, s.expense)
    }

    @Test fun `adjustment on a card means amount owed`() {
        val adj = AccountLedger.adjustment(card, tracked = d("200"), actual = d("350"), at = t1)!!
        assertEquals(d("350"), amountOf(listOf(adj), card))
        val down = AccountLedger.adjustment(card, tracked = d("200"), actual = d("50"), at = t1)!!
        assertEquals(d("50"), amountOf(listOf(down), card))
    }

    @Test fun `no adjustment when they already agree`() = assertNull(AccountLedger.adjustment(bank, d("5"), d("5"), t1))

    @Test fun `10 different currencies are refused for transfers and never mixed in balances or net worth`() {
        val usd = Account(5, "Wise", AccountType.BANK, currency = "USD", openingBalance = d("10"), openingDate = t0)
        assertTrue(AccountLedger.transfer(bank, usd, d("5"), t1).isFailure)
        val foreign = tx(bank, TransactionDirection.DEBIT, "99", TransactionType.EXPENSE, cur = "USD")
        assertEquals(d("1000"), AccountLedger.balanceOf(bank, listOf(foreign)))
        val net = AccountLedger.netByCurrency(AccountLedger.positions(listOf(bank, usd, card), emptyList()))
        assertEquals(d("800"), net["INR"])   // 1000 asset - 200 owed
        assertEquals(d("10"), net["USD"])
    }

    @Test fun `transfer validation`() {
        assertTrue(AccountLedger.transfer(bank, bank, d("5"), t1).isFailure)
        assertTrue(AccountLedger.transfer(bank, cash, d("0"), t1).isFailure)
    }

    @Test fun `transactions before the opening date are already inside the opening balance`() {
        val old = tx(bank, TransactionDirection.DEBIT, "400", TransactionType.EXPENSE, at = Instant.parse("2025-12-01T00:00:00Z"))
        assertEquals(d("1000"), amountOf(listOf(old), bank))
    }

    @Test fun `archived accounts are left out of net worth`() {
        val net = AccountLedger.netByCurrency(AccountLedger.positions(listOf(bank.copy(archived = true), cash), emptyList()))
        assertEquals(d("100"), net["INR"])
    }

    @Test fun `unmatched overlap rows do not move balances`() {
        val t = tx(bank, TransactionDirection.DEBIT, "70", TransactionType.EXPENSE).copy(isUnmatchedOverlap = true)
        assertEquals(d("1000"), amountOf(listOf(t), bank))
    }

    @Test fun `adjustment builder rejects nothing silently`() = assertNotNull(AccountLedger.adjustment(bank, d("1"), d("2"), t1))
}
