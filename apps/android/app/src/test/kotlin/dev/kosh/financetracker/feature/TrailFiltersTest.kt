package dev.kosh.financetracker.feature

import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.core.model.CategorySplit
import dev.kosh.financetracker.core.model.PaymentMethod
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionSource
import dev.kosh.financetracker.core.model.TransactionType
import dev.kosh.financetracker.feature.trail.Period
import dev.kosh.financetracker.feature.trail.TrailFilter
import dev.kosh.financetracker.feature.trail.TrailFilters
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TrailFiltersTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 8)
    private fun tx(id: Long, date: String, type: TransactionType = TransactionType.EXPENSE, cat: TransactionCategory = TransactionCategory.FOOD,
                   merchant: String = "Cafe", account: Long? = null, note: String? = null, label: String? = null,
                   splits: List<CategorySplit> = emptyList(), amt: String = "100") = Transaction(
        id = id, timestamp = Instant.parse("${date}T10:00:00Z"), amount = BigDecimal(amt), direction = TransactionDirection.DEBIT, type = type,
        category = cat, merchant = merchant, rawMerchant = merchant, accountId = account, accountSuffix = null, bank = null,
        paymentMethod = PaymentMethod.UNKNOWN, source = TransactionSource.MANUAL, sourceMessageId = null, rawSourceText = null,
        confidence = 1.0, notes = note, createdAt = Instant.EPOCH, splits = splits, customLabel = label,
    )
    private fun ids(f: TrailFilters, list: List<Transaction>, accounts: List<Account> = emptyList()) = f.apply(list, accounts, today, zone).map { it.id }

    private val bank = Account(1, "HDFC", AccountType.BANK)
    private val debit = Account(2, "Debit", AccountType.DEBIT_CARD, linkedAccountId = 1)

    @Test fun `period filters`() {
        val list = listOf(tx(1, "2026-11-05"), tx(2, "2026-10-20"), tx(3, "2026-02-01"), tx(4, "2025-12-31"))
        assertEquals(listOf(1L), ids(TrailFilters(period = Period.THIS_MONTH), list))
        assertEquals(listOf(1L, 2L), ids(TrailFilters(period = Period.LAST_30), list))
        assertEquals(listOf(1L, 2L, 3L), ids(TrailFilters(period = Period.THIS_YEAR), list))
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(TrailFilters(), list))
    }

    @Test fun `account filter includes a linked debit card's spending`() {
        val list = listOf(tx(1, "2026-11-05", account = 1), tx(2, "2026-11-05", account = 2), tx(3, "2026-11-05", account = 9))
        assertEquals(listOf(1L, 2L), ids(TrailFilters(accountId = 1), list, listOf(bank, debit)))
        assertEquals(listOf(2L), ids(TrailFilters(accountId = 2), list, listOf(bank, debit)))
    }

    @Test fun `category filter also finds a split part`() {
        val split = listOf(CategorySplit(TransactionCategory.GROCERIES, BigDecimal("70")), CategorySplit(TransactionCategory.HOME, BigDecimal("30")))
        val list = listOf(tx(1, "2026-11-05", cat = TransactionCategory.GROCERIES, splits = split), tx(2, "2026-11-05", cat = TransactionCategory.FOOD))
        assertEquals(listOf(1L), ids(TrailFilters(category = TransactionCategory.HOME), list))
        assertEquals(listOf(2L), ids(TrailFilters(category = TransactionCategory.FOOD), list))
    }

    @Test fun `search covers merchant note label and category name`() {
        val list = listOf(
            tx(1, "2026-11-05", merchant = "Blue Tokai"), tx(2, "2026-11-05", note = "birthday gift"),
            tx(3, "2026-11-05", label = "Pets"), tx(4, "2026-11-05", cat = TransactionCategory.FUEL, merchant = "Pump"),
        )
        assertEquals(listOf(1L), ids(TrailFilters(query = "tokai"), list))
        assertEquals(listOf(2L), ids(TrailFilters(query = "BIRTHDAY"), list))
        assertEquals(listOf(3L), ids(TrailFilters(query = "pets"), list))
        assertEquals(listOf(4L), ids(TrailFilters(query = "fuel"), list))
    }

    @Test fun `filters combine`() {
        val list = listOf(tx(1, "2026-11-05", cat = TransactionCategory.FOOD), tx(2, "2026-10-05", cat = TransactionCategory.FOOD), tx(3, "2026-11-05", cat = TransactionCategory.FUEL))
        assertEquals(listOf(1L), ids(TrailFilters(period = Period.THIS_MONTH, category = TransactionCategory.FOOD, type = TrailFilter.EXPENSES), list))
    }

    @Test fun `transfers filter includes card payments and cash withdrawals`() {
        val list = listOf(tx(1, "2026-11-05", TransactionType.TRANSFER), tx(2, "2026-11-05", TransactionType.CREDIT_CARD_PAYMENT), tx(3, "2026-11-05", TransactionType.CASH_WITHDRAWAL), tx(4, "2026-11-05"))
        assertEquals(listOf(1L, 2L, 3L), ids(TrailFilters(type = TrailFilter.TRANSFERS), list))
    }

    @Test fun `active extras counts only period account and category`() {
        assertEquals(2, TrailFilters(query = "x", period = Period.THIS_YEAR, category = TransactionCategory.FOOD).activeExtras)
    }
}
