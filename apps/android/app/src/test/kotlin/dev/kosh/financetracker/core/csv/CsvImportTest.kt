package dev.kosh.financetracker.core.csv

import dev.kosh.financetracker.core.model.PaymentMethod
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionSource
import dev.kosh.financetracker.core.model.TransactionType
import dev.kosh.financetracker.core.parser.MoneyNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CsvImportTest {
    private val zone = ZoneId.of("UTC")
    private fun d(v: String) = BigDecimal(v)
    private fun fmt(p: String) = DateParsing.FORMATS.first { it.pattern == p }

    // ---- reading ----
    @Test fun `quotes commas and line breaks inside quotes`() {
        val t = CsvReader.parse("Date,Description,Amount\n05/11/2026,\"Smith, John \"\"JJ\"\"\",-12.50\n06/11/2026,\"Two\nlines\",3\n")
        assertEquals(',', t.delimiter); assertEquals(3, t.rows.size)
        assertEquals("Smith, John \"JJ\"", t.rows[1][1]); assertEquals("Two\nlines", t.rows[2][1])
    }

    @Test fun `semicolon and tab delimiters, BOM and CRLF`() {
        assertEquals(';', CsvReader.parse("\uFEFFDatum;Betrag\r\n05.11.2026;-12,50\r\n").delimiter)
        assertEquals('\t', CsvReader.parse("Date\tAmount\n2026-11-05\t5\n").delimiter)
        assertEquals(2, CsvReader.parse("\uFEFFDatum;Betrag\r\n05.11.2026;-12,50\r\n").rows.size)
    }

    @Test fun `blank lines are skipped`() = assertEquals(2, CsvReader.parse("a,b\n\n1,2\n\n").rows.size)

    // ---- header and columns ----
    @Test fun `header is found under a preamble`() {
        val rows = CsvReader.parse("Account Statement\nAccount: 1234\n\nDate,Narration,Withdrawal,Deposit,Balance\n05/11/2026,UPI,100,,900\n").rows
        assertEquals(2, ColumnGuesser.findHeaderRow(rows))   // blank lines are dropped, so the header is the third kept row
    }

    @Test fun `columns are guessed for common layouts`() {
        val a = ColumnGuesser.guess(listOf("Transaction Date", "Description", "Debit", "Credit", "Balance"))
        assertEquals(ColumnMapping(0, 1, null, 2, 3, 4), a)
        val b = ColumnGuesser.guess(listOf("Date", "Payee", "Amount"))
        assertEquals(ColumnMapping(0, 1, 2, null, null, null), b)
        val c = ColumnGuesser.guess(listOf("Posting Date", "Details", "Money out", "Money in", "Running balance"))
        assertEquals(ColumnMapping(0, 1, null, 2, 3, 4), c)
        assertTrue(a.usable); assertFalse(ColumnMapping(null, 1, 2).usable)
    }

    @Test fun `headers in other languages are recognised`() {
        assertEquals(ColumnMapping(0, 1, 2, null, null, null), ColumnGuesser.guess(listOf("Datum", "Beschreibung", "Betrag")))
        assertEquals(ColumnMapping(0, 1, null, 2, 3, 4), ColumnGuesser.guess(listOf("Date", "Libell\u00e9", "D\u00e9bit", "Cr\u00e9dit", "Solde")))
        assertEquals(ColumnMapping(0, 1, 2, null, null, 3), ColumnGuesser.guess(listOf("Fecha", "Concepto", "Importe", "Saldo")))
        assertEquals(ColumnMapping(0, 1, null, 2, 3, 4), ColumnGuesser.guess(listOf("Buchungstag", "Verwendungszweck", "Soll", "Haben", "Saldo")))
    }

    @Test fun `a value date column does not steal the transaction date`() {
        val m = ColumnGuesser.guess(listOf("Txn Date", "Value Date", "Narration", "Amount"))
        assertEquals(0, m.date); assertEquals(2, m.description); assertEquals(3, m.amount)
    }

    // ---- dates ----
    @Test fun `unambiguous day first and month first`() {
        assertEquals("d/M/yyyy", DateParsing.detect(listOf("25/11/2026", "05/11/2026"), dayFirstDefault = false)!!.best.pattern)
        assertEquals("M/d/yyyy", DateParsing.detect(listOf("11/25/2026", "11/05/2026"), dayFirstDefault = true)!!.best.pattern)
    }

    @Test fun `fully ambiguous dates follow the default and say so`() {
        val dayFirst = DateParsing.detect(listOf("05/11/2026", "03/12/2026"), dayFirstDefault = true)!!
        assertTrue(dayFirst.ambiguous); assertEquals("d/M/yyyy", dayFirst.best.pattern)
        assertEquals("M/d/yyyy", DateParsing.detect(listOf("05/11/2026", "03/12/2026"), dayFirstDefault = false)!!.best.pattern)
    }

    @Test fun `other date styles and trailing times`() {
        assertEquals(LocalDate.of(2026, 11, 5), DateParsing.parse("2026-11-05 14:22:10", fmt("yyyy-M-d")))
        assertEquals(LocalDate.of(2026, 11, 5), DateParsing.parse("05-Nov-2026", fmt("d-MMM-yyyy")))
        assertEquals(LocalDate.of(2026, 11, 5), DateParsing.parse("5 November 2026", fmt("d MMMM yyyy")))
        assertEquals(LocalDate.of(2026, 11, 5), DateParsing.parse("05.11.2026", fmt("d.M.yyyy")))
        assertEquals(LocalDate.of(2026, 11, 5), DateParsing.parse("11/05/2026 3:04 PM", fmt("M/d/yyyy")))
        assertNull(DateParsing.parse("31/02/2026", fmt("d/M/yyyy")))
        assertNull(DateParsing.parse("not a date", fmt("d/M/yyyy")))
    }

    @Test fun `no format reads garbage`() = assertNull(DateParsing.detect(listOf("hello", "world"), true))

    // ---- numbers ----
    @Test fun `number styles`() {
        fun v(s: String, dec: MoneyNumbers.Decimal = MoneyNumbers.Decimal.DOT) = MoneyNumbers.parse(s, dec)
        assertEquals(0, d("1234.56").compareTo(v("(1,234.56)")!!.value))
        assertTrue(v("(1,234.56)")!!.negative); assertTrue(v("-12.5")!!.negative); assertTrue(v("12.5-")!!.negative); assertTrue(v("\u221212.5")!!.negative)
        assertEquals(0, d("1234.56").compareTo(v("1.234,56", MoneyNumbers.Decimal.COMMA)!!.value))
        assertEquals(0, d("1234.56").compareTo(v("1 234,56", MoneyNumbers.Decimal.COMMA)!!.value))
        assertEquals(0, d("1234.56").compareTo(v("\$1,234.56")!!.value))
        assertEquals(MoneyNumbers.Marker.DEBIT, v("250.00 Dr")!!.marker); assertEquals(MoneyNumbers.Marker.CREDIT, v("250.00 CR")!!.marker)
        assertNull(v("abc")); assertNull(v("")); assertNull(v("1.2.3"))
    }

    @Test fun `decimal separator is detected from samples`() {
        assertEquals(MoneyNumbers.Decimal.COMMA, MoneyNumbers.detectDecimal(listOf("1.234,56", "12,50", "7,00")))
        assertEquals(MoneyNumbers.Decimal.DOT, MoneyNumbers.detectDecimal(listOf("1,234.56", "12.50")))
        assertEquals(MoneyNumbers.Decimal.DOT, MoneyNumbers.detectDecimal(listOf("1,234", "2,500")))   // lone thousands marks: no vote, falls back
    }

    // ---- planning ----
    private val table = CsvReader.parse(
        "Statement for account 1234\nDate,Description,Debit,Credit,Balance\n" +
            "05/11/2026,COFFEE,4.50,,95.50\n06/11/2026,SALARY,,2000.00,2095.50\n06/11/2026,COFFEE,4.50,,2091.00\n07/11/2026,,,,\nbad,TEST,1,,\n08/11/2026,NOAMOUNT,,,\n09/11/2026,BOTH,1,2,\n",
    )
    private val settings = ImportSettings(ColumnMapping(0, 1, null, 2, 3, 4), fmt("d/M/yyyy"), MoneyNumbers.Decimal.DOT)

    private fun existing(date: String, amt: String, dir: TransactionDirection) = Transaction(
        id = 9, timestamp = Instant.parse("${date}T10:00:00Z"), amount = d(amt), direction = dir, type = TransactionType.EXPENSE, category = null,
        merchant = "m", rawMerchant = "m", accountId = 1, accountSuffix = null, bank = null, paymentMethod = PaymentMethod.UNKNOWN,
        source = TransactionSource.SMS, sourceMessageId = "x", rawSourceText = null, confidence = 1.0, notes = null, createdAt = Instant.EPOCH,
    )

    @Test fun `rows are read, blanks skipped, problems reported`() {
        val plan = CsvImportPlanner.plan(table, 1, settings, emptyList(), zone)
        assertEquals(3, plan.newRows.size); assertEquals(3, plan.errors); assertEquals(0, plan.duplicates)
        assertEquals(0, d("9.00").compareTo(plan.moneyOut)); assertEquals(0, d("2000").compareTo(plan.moneyIn))
        assertEquals(LocalDate.of(2026, 11, 5), plan.firstDate); assertEquals(LocalDate.of(2026, 11, 6), plan.lastDate)
        assertTrue(plan.rows.first { it.problem?.startsWith("Date") == true }.status == RowStatus.ERROR)
        assertTrue(plan.rows.any { it.problem == "Both a debit and a credit are filled in" })
    }

    @Test fun `rows already in history are duplicates, one for one`() {
        // one existing 4.50 coffee on the 5th, none on the 6th: the 5th matches, the 6th stays new
        val plan = CsvImportPlanner.plan(table, 1, settings, listOf(existing("2026-11-05", "4.50", TransactionDirection.DEBIT)), zone)
        assertEquals(1, plan.duplicates)
        assertEquals(RowStatus.DUPLICATE, plan.rows.first { it.date == LocalDate.of(2026, 11, 5) }.status)
        assertEquals(2, plan.newRows.size)
    }

    @Test fun `a one day date difference still matches`() {
        val plan = CsvImportPlanner.plan(table, 1, settings, listOf(existing("2026-11-04", "4.50", TransactionDirection.DEBIT)), zone)
        assertEquals(1, plan.duplicates)
    }

    @Test fun `same amount in the other direction is not a duplicate`() {
        assertEquals(0, CsvImportPlanner.plan(table, 1, settings, listOf(existing("2026-11-05", "4.50", TransactionDirection.CREDIT)), zone).duplicates)
    }

    @Test fun `importing the same file twice finds everything`() {
        val first = CsvImportPlanner.plan(table, 1, settings, emptyList(), zone)
        val saved = first.newRows.map { existing(it.date.toString(), it.amount.toString(), it.direction!!) }
        val again = CsvImportPlanner.plan(table, 1, settings, saved, zone)
        assertEquals(3, again.duplicates); assertEquals(0, again.newRows.size)
    }

    @Test fun `single amount column negative is money out and the sign can be inverted`() {
        val t = CsvReader.parse("Date;Text;Amount\n05.11.2026;Cafe;-12,50\n06.11.2026;Pay;2.000,00\n")
        val s = ImportSettings(ColumnMapping(0, 1, 2), fmt("d.M.yyyy"), MoneyNumbers.Decimal.COMMA)
        val plan = CsvImportPlanner.plan(t, 0, s, emptyList(), zone)
        assertEquals(TransactionDirection.DEBIT, plan.rows[0].direction); assertEquals(0, d("12.50").compareTo(plan.rows[0].amount))
        assertEquals(TransactionDirection.CREDIT, plan.rows[1].direction); assertEquals(0, d("2000").compareTo(plan.rows[1].amount))
        val flipped = CsvImportPlanner.plan(t, 0, s.copy(invertSign = true), emptyList(), zone)
        assertEquals(TransactionDirection.CREDIT, flipped.rows[0].direction)
    }

    @Test fun `Dr Cr markers decide direction`() {
        val t = CsvReader.parse("Date,Details,Amount\n05/11/2026,A,100.00 Dr\n06/11/2026,B,50.00 Cr\n")
        val plan = CsvImportPlanner.plan(t, 0, ImportSettings(ColumnMapping(0, 1, 2), fmt("d/M/yyyy"), MoneyNumbers.Decimal.DOT), emptyList(), zone)
        assertEquals(listOf(TransactionDirection.DEBIT, TransactionDirection.CREDIT), plan.rows.map { it.direction })
    }

    @Test fun `a zero amount is an error`() {
        val t = CsvReader.parse("Date,Details,Amount\n05/11/2026,A,0.00\n")
        assertEquals(1, CsvImportPlanner.plan(t, 0, ImportSettings(ColumnMapping(0, 1, 2), fmt("d/M/yyyy"), MoneyNumbers.Decimal.DOT), emptyList(), zone).errors)
        assertNotNull(t)
    }
}
