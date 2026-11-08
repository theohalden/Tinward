package dev.kosh.financetracker.core.parser

import dev.kosh.financetracker.core.model.EventSource
import dev.kosh.financetracker.core.model.RawFinancialEvent
import dev.kosh.financetracker.core.model.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Representative English-language alert templates from several countries. They are written to
 * match how banks commonly word these messages; they are NOT captured from real accounts, so a
 * pass here shows the parser handles the pattern, not that any particular bank is supported.
 * Messages that are not completed transactions must be left unread.
 */
class GlobalSmsFixturesTest {
    private val registry = TransactionParserRegistry()
    private fun parse(body: String, sender: String? = null) =
        registry.parse(RawFinancialEvent("1", EventSource.SMS, sender, body, Instant.parse("2026-11-05T10:00:00Z"), "1"))

    private class Expect(val amount: String, val currency: String, val dir: TransactionDirection, val suffix: String? = null, val merchant: String? = null)

    private fun check(name: String, body: String, e: Expect, sender: String? = null) {
        val p = parse(body, sender)
        assertNotNull("$name: not parsed", p)
        assertEquals("$name amount", 0, BigDecimal(e.amount).compareTo(p!!.amount))
        assertEquals("$name currency", e.currency, p.currency)
        assertEquals("$name direction", e.dir, p.direction)
        if (e.suffix != null) assertEquals("$name suffix", e.suffix, p.accountSuffix)
        if (e.merchant != null) assertEquals("$name merchant", e.merchant, p.merchantRaw)
    }

    private val D = TransactionDirection.DEBIT
    private val C = TransactionDirection.CREDIT
    private val DOLLAR = MessageMoney.BARE_DOLLAR

    // ---- United States / Canada ----
    @Test fun `US card spend`() = check("chase", "Chase: You spent $45.20 at STARBUCKS on card ending in 1234.", Expect("45.20", DOLLAR, D, "1234", "STARBUCKS"))
    @Test fun `US debit purchase with balance`() {
        val p = parse("BofA Alert: Debit card purchase of $12.99 at NETFLIX.COM on card ending 5678. Available balance: $1,203.45")!!
        assertEquals(0, BigDecimal("12.99").compareTo(p.amount)); assertEquals(0, BigDecimal("1203.45").compareTo(p.balanceAfter)); assertEquals("5678", p.accountSuffix)
    }
    @Test fun `US deposit`() = check("deposit", "Wells Fargo: A $2,500.00 deposit was credited to account ending in 9012.", Expect("2500.00", DOLLAR, C, "9012"))
    @Test fun `US ATM`() { val p = parse("ATM withdrawal of $200.00 at 5TH AVE ATM using card ending 9911.")!!; assertEquals(D, p.direction); assertEquals("9911", p.accountSuffix) }
    @Test fun `US refund`() = check("refund", "Refund of $15.00 from AMAZON has been credited to your card ending 2020.", Expect("15.00", DOLLAR, C, "2020", "AMAZON"))
    @Test fun `Canada purchase`() = check("rbc", "RBC: A purchase of $32.45 was made at TIM HORTONS with your card ending 6655.", Expect("32.45", DOLLAR, D, "6655", "TIM HORTONS"))
    @Test fun `explicit code beats the bare dollar`() = check("usd", "Card ending 1111 charged USD 60.00 at UBER TRIP.", Expect("60.00", "USD", D, "1111", "UBER TRIP"))

    // ---- United Kingdom / Ireland ----
    @Test fun `UK card used`() = check("barclays", "Barclays: Card ending 4321 used for £23.50 at TESCO STORES on 05/11.", Expect("23.50", "GBP", D, "4321", "TESCO STORES"))
    @Test fun `UK app payment with balance`() {
        val p = parse("Monzo: You paid £9.99 to Spotify. Balance £101.20")!!
        assertEquals("GBP", p.currency); assertEquals(D, p.direction); assertEquals("Spotify", p.merchantRaw); assertEquals(0, BigDecimal("101.20").compareTo(p.balanceAfter))
    }
    @Test fun `UK salary`() = check("salary", "Your salary of £2,450.00 has been credited to account ending 1122.", Expect("2450.00", "GBP", C, "1122"))

    // ---- Euro area (comma decimals) ----
    @Test fun `EU card payment comma decimal`() = check("revolut", "Revolut: Card payment of €18,50 at LIDL BERLIN. Ref 8841", Expect("18.50", "EUR", D, null, "LIDL BERLIN"))
    @Test fun `EU received with dot thousands`() = check("n26", "N26: You received €1.250,00 from ACME GMBH.", Expect("1250.00", "EUR", C, null, "ACME GMBH"))

    // ---- Asia-Pacific / Gulf ----
    @Test fun `Singapore dollars`() = check("dbs", "DBS Card Alert: S$12.50 was spent at GRAB* ON 05 NOV with DBS Card ending 3344", Expect("12.50", "SGD", D, "3344", "GRAB*"))
    @Test fun `UAE dirhams`() = check("enbd", "Emirates NBD: AED 150.00 spent at CARREFOUR using card ending 7788 on 05-Nov", Expect("150.00", "AED", D, "7788", "CARREFOUR"))
    @Test fun `Australia`() = check("cba", "CommBank: $64.10 purchase at WOOLWORTHS 1234 using card x2468", Expect("64.10", DOLLAR, D, "2468"))
    @Test fun `NZ dollars with prefix`() = check("nz", "ANZ: NZ$40.00 debited from account ending 3030 at COUNTDOWN.", Expect("40.00", "NZD", D, "3030", "COUNTDOWN"))
    @Test fun `Japan yen`() = check("jp", "Card ending 5050 charged ¥3,200 at FAMILYMART.", Expect("3200", "JPY", D, "5050", "FAMILYMART"))

    // ---- India keeps working and stays rupee ----
    @Test fun `India rupee message is still INR`() {
        val p = parse("Rs.450.00 debited from A/c XX5590 on 05-Nov-26 to VPA zomato@upi UPI Ref No 123456789012.")!!
        assertEquals("INR", p.currency); assertEquals(0, BigDecimal("450.00").compareTo(p.amount)); assertEquals(D, p.direction)
    }

    // ---- not transactions: must be left unread ----
    @Test fun `OTP is ignored`() = assertNull(parse("Your OTP is 482913 to authorize payment of $50.00 at AMAZON. Do not share it."))
    @Test fun `promo is ignored`() = assertNull(parse("Congratulations! You are pre-approved for a loan offer of $10,000. Apply now."))
    @Test fun `scheduled payment is ignored`() = assertNull(parse("Reminder: your payment of $120.00 will be debited on 10/11."))
    @Test fun `failed payment is ignored`() = assertNull(parse("Your payment of $120.00 failed. Please retry."))
    @Test fun `balance only is ignored`() = assertNull(parse("Balance alert: your available balance is $1,203.45."))
    @Test fun `bill due is ignored`() = assertNull(parse("Statement ready: your minimum payment due is $35.00 by 12/11."))
    @Test fun `no amount is ignored`() = assertNull(parse("Your parcel from Amazon has shipped and will arrive tomorrow."))
    @Test fun `discount offer is ignored`() = assertNull(parse("New offer: 20% off your next purchase at Target. Click here."))

    @Test fun `bare dollar resolution`() {
        assertEquals("AUD", CurrencyResolution.resolve(DOLLAR, "AUD")); assertEquals("USD", CurrencyResolution.resolve("USD", "AUD"))
        assertEquals("CAD", CurrencyResolution.defaultDollar("CAD")); assertEquals("USD", CurrencyResolution.defaultDollar("INR"))
    }

    @Test fun `amount formats`() {
        assertEquals(0, BigDecimal("1234.56").compareTo(MoneyNumbers.parseAuto("1,234.56"))); assertEquals(0, BigDecimal("1234.56").compareTo(MoneyNumbers.parseAuto("1.234,56")))
        assertEquals(0, BigDecimal("1234.56").compareTo(MoneyNumbers.parseAuto("1 234,56"))); assertEquals(0, BigDecimal("1234").compareTo(MoneyNumbers.parseAuto("1,234")))
        assertEquals(0, BigDecimal("18.50").compareTo(MoneyNumbers.parseAuto("18,50")))
    }
}
