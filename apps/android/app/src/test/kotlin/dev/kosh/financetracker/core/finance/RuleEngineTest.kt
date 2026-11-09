package dev.kosh.financetracker.core.finance

import dev.kosh.financetracker.core.model.PaymentMethod
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionSource
import dev.kosh.financetracker.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class RuleEngineTest {
    private fun d(v: String) = BigDecimal(v)
    private fun tx(id: Long = 1, merchant: String? = "Swiggy Order", amt: String = "400", dir: TransactionDirection = TransactionDirection.DEBIT,
                   type: TransactionType = TransactionType.EXPENSE, cat: TransactionCategory? = TransactionCategory.UNCATEGORIZED, label: String? = null, account: Long? = 1) = Transaction(
        id = id, timestamp = Instant.EPOCH, amount = d(amt), direction = dir, type = type, category = cat, merchant = merchant, rawMerchant = merchant,
        accountId = account, accountSuffix = null, bank = null, paymentMethod = PaymentMethod.UNKNOWN, source = TransactionSource.SMS, sourceMessageId = "s$id",
        rawSourceText = "Rs.$amt debited secret text", confidence = 1.0, notes = null, createdAt = Instant.EPOCH, customLabel = label,
    )
    private fun rule(id: Long = 1, name: String = "r", merchant: String? = "swiggy", cat: TransactionCategory? = TransactionCategory.FOOD, label: String? = null, overwrite: Boolean = false,
                     min: String? = null, max: String? = null, dir: RuleDirection = RuleDirection.ANY, account: Long? = null, enabled: Boolean = true) =
        AutoRule(id, name, enabled, merchant, min?.let(::d), max?.let(::d), dir, account, cat, label, overwrite)

    @Test fun `merchant match is case insensitive and partial`() {
        assertTrue(RuleEngine.matches(rule(merchant = "SWIGGY"), tx()))
        assertFalse(RuleEngine.matches(rule(merchant = "zomato"), tx()))
    }

    @Test fun `the original message text is never searched`() = assertFalse(RuleEngine.matches(rule(merchant = "secret"), tx()))

    @Test fun `amount range is inclusive`() {
        assertTrue(RuleEngine.matches(rule(merchant = null, min = "400", max = "400"), tx()))
        assertFalse(RuleEngine.matches(rule(merchant = null, min = "401"), tx()))
        assertFalse(RuleEngine.matches(rule(merchant = null, max = "399"), tx()))
    }

    @Test fun `direction and account conditions`() {
        assertFalse(RuleEngine.matches(rule(dir = RuleDirection.RECEIVED), tx()))
        assertTrue(RuleEngine.matches(rule(dir = RuleDirection.SPENT), tx()))
        assertFalse(RuleEngine.matches(rule(account = 2), tx(account = 1)))
        assertTrue(RuleEngine.matches(rule(account = 1), tx(account = 1)))
    }

    @Test fun `all conditions must hold together`() = assertFalse(RuleEngine.matches(rule(merchant = "swiggy", min = "500"), tx(amt = "400")))

    @Test fun `a rule with no condition matches nothing`() {
        val everything = rule(merchant = null)
        assertFalse(everything.hasCondition); assertFalse(RuleEngine.matches(everything, tx()))
    }

    @Test fun `transfers, card payments and adjustments are left alone`() {
        listOf(TransactionType.TRANSFER, TransactionType.CREDIT_CARD_PAYMENT, TransactionType.CASH_WITHDRAWAL, TransactionType.ADJUSTMENT, TransactionType.UNKNOWN).forEach {
            assertFalse("$it", RuleEngine.matches(rule(), tx(type = it)))
        }
    }

    @Test fun `by default only blanks are filled`() {
        assertEquals(TransactionCategory.FOOD, RuleEngine.changeFor(listOf(rule()), tx(cat = TransactionCategory.UNCATEGORIZED))!!.category)
        assertEquals(TransactionCategory.FOOD, RuleEngine.changeFor(listOf(rule()), tx(cat = null))!!.category)
        assertNull("the user's own choice is respected", RuleEngine.changeFor(listOf(rule()), tx(cat = TransactionCategory.SHOPPING)))
    }

    @Test fun `Other counts as not decided`() =
        assertEquals(TransactionCategory.FOOD, RuleEngine.changeFor(listOf(rule()), tx(cat = TransactionCategory.OTHER))!!.category)

    @Test fun `overwrite replaces an existing category`() =
        assertEquals(TransactionCategory.FOOD, RuleEngine.changeFor(listOf(rule(overwrite = true)), tx(cat = TransactionCategory.SHOPPING))!!.category)

    @Test fun `labels follow the same fill or overwrite rule`() {
        assertEquals("Eating out", RuleEngine.changeFor(listOf(rule(cat = null, label = " Eating out ")), tx())!!.label)
        assertNull(RuleEngine.changeFor(listOf(rule(cat = null, label = "Eating out")), tx(label = "Mine")))
        assertEquals("Eating out", RuleEngine.changeFor(listOf(rule(cat = null, label = "Eating out", overwrite = true)), tx(label = "Mine"))!!.label)
    }

    @Test fun `a rule that would change nothing yields no change`() = assertNull(RuleEngine.changeFor(listOf(rule(overwrite = true)), tx(cat = TransactionCategory.FOOD)))

    @Test fun `higher priority wins even if it changes nothing`() {
        val high = rule(id = 1, cat = TransactionCategory.SHOPPING)           // matches, but the category is already set, so no change
        val low = rule(id = 2, cat = TransactionCategory.FOOD, overwrite = true)
        assertNull(RuleEngine.changeFor(listOf(high, low), tx(cat = TransactionCategory.SHOPPING)))
        assertEquals(2L, RuleEngine.changeFor(listOf(low, high), tx(cat = TransactionCategory.SHOPPING))!!.ruleId)
    }

    @Test fun `disabled rules and rules without an action are skipped`() {
        assertNull(RuleEngine.changeFor(listOf(rule(enabled = false)), tx()))
        assertNull(RuleEngine.changeFor(listOf(rule(cat = null, label = null)), tx()))
        assertEquals(2L, RuleEngine.changeFor(listOf(rule(id = 1, enabled = false), rule(id = 2)), tx())!!.ruleId)
    }

    @Test fun `changes are produced per transaction and counts ignore priority`() {
        val txs = listOf(tx(1), tx(2, merchant = "Uber"), tx(3, merchant = "Swiggy Instamart"), tx(4, cat = TransactionCategory.GROCERIES))
        assertEquals(listOf(1L, 3L), RuleEngine.changes(listOf(rule()), txs).map { it.transactionId })
        assertEquals(3, RuleEngine.matchCount(rule(), txs))
    }
}
