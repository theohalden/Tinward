package dev.kosh.financetracker.core.csv

import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.parser.MoneyNumbers
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import kotlin.math.abs

data class CsvTable(val delimiter: Char, val rows: List<List<String>>)

/** Reads delimited text: quoted fields, doubled quotes, line breaks inside quotes, CRLF, a BOM. */
object CsvReader {
    private val DELIMITERS = listOf(',', ';', '\t', '|')

    fun parse(text: String): CsvTable {
        val clean = text.removePrefix("\uFEFF")
        val delimiter = detectDelimiter(clean)
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>(); val cell = StringBuilder(); var quoted = false; var i = 0
        fun endCell() { row.add(cell.toString().trim()); cell.setLength(0) }
        fun endRow() { endCell(); if (row.any { it.isNotEmpty() }) rows.add(row); row = ArrayList() }
        while (i < clean.length) {
            val c = clean[i]
            when {
                quoted && c == '"' && i + 1 < clean.length && clean[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == delimiter -> endCell()
                !quoted && c == '\r' -> { if (i + 1 < clean.length && clean[i + 1] == '\n') i++; endRow() }
                !quoted && c == '\n' -> endRow()
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return CsvTable(delimiter, rows)
    }

    private fun detectDelimiter(text: String): Char {
        val lines = text.lineSequence().filter { it.isNotBlank() }.take(10).toList()
        return DELIMITERS.maxByOrNull { d -> lines.count { l -> countOutsideQuotes(l, d) >= 1 } * 100 + (lines.minOfOrNull { countOutsideQuotes(it, d) } ?: 0) }
            ?.takeIf { d -> lines.any { countOutsideQuotes(it, d) >= 1 } } ?: ','
    }

    private fun countOutsideQuotes(line: String, d: Char): Int { var q = false; var n = 0; for (c in line) { if (c == '"') q = !q else if (c == d && !q) n++ }; return n }
}

/** Which column holds what. Null means the file has no such column. */
data class ColumnMapping(
    val date: Int? = null, val description: Int? = null, val amount: Int? = null,
    val debit: Int? = null, val credit: Int? = null, val balance: Int? = null,
) {
    val split: Boolean get() = debit != null || credit != null
    val usable: Boolean get() = date != null && (amount != null || split)
}

object ColumnGuesser {
    // English first, then the headers banks commonly use in German, French, Spanish, Italian, Portuguese and Dutch.
    private val date = listOf("transaction date", "txn date", "posting date", "booking date", "value date", "posted", "date",
        "datum", "buchungstag", "buchungsdatum", "fecha", "data", "fecha operacion", "data operazione", "date operation", "date de comptabilisation")
    private val description = listOf("description", "narration", "particulars", "details", "payee", "merchant", "memo", "reference", "remarks", "transaction details",
        "beschreibung", "verwendungszweck", "buchungstext", "libelle", "libelles", "concepto", "descripcion", "descrizione", "causale", "descricao", "historico", "omschrijving", "naam")
    private val debit = listOf("debit", "withdrawal", "paid out", "money out", "outflow", "dr", "soll", "debito", "debe", "cargo", "dare", "debito", "af", "uitgaven")
    private val credit = listOf("credit", "deposit", "paid in", "money in", "inflow", "cr", "haben", "credito", "haber", "abono", "avere", "bij", "inkomsten")
    private val amount = listOf("amount", "amt", "value", "transaction amount", "betrag", "montant", "importe", "importo", "valor", "bedrag")
    private val balance = listOf("balance", "running balance", "closing balance", "saldo", "solde", "saldo contable")

    /** Lower case, accents removed, anything else turned into spaces. */
    private fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("""\p{Mn}+"""), "").replace(Regex("""[^a-z0-9 ]"""), " ").replace(Regex("""\s+"""), " ").trim()

    fun headerScore(row: List<String>): Int {
        val groups = listOf(date, description, debit, credit, amount, balance)
        return row.count { c -> val n = norm(c); n.isNotEmpty() && groups.any { g -> g.any { w -> n == w || n.contains(w) } } }
    }

    /** The header is the first row that looks like column names; banks often put account details above it. */
    fun findHeaderRow(rows: List<List<String>>): Int {
        val best = rows.take(30).withIndex().maxByOrNull { (_, r) -> headerScore(r) } ?: return 0
        return if (headerScore(best.value) >= 2) best.index else 0
    }

    fun guess(header: List<String>): ColumnMapping {
        val used = HashSet<Int>()
        fun pick(words: List<String>): Int? {
            for (w in words) header.forEachIndexed { i, h -> if (i !in used) { val n = norm(h); if (n == w || n.split(' ').contains(w) || (w.length > 3 && n.contains(w))) { used += i; return i } } }
            return null
        }
        val d = pick(date); val desc = pick(description); val deb = pick(debit); val cred = pick(credit)
        val amt = if (deb == null && cred == null) pick(amount) else null
        return ColumnMapping(d, desc, amt, deb, cred, pick(balance))
    }
}

data class DateFormat(val pattern: String, val example: String) {
    /** Strict, so 31/02/2026 is rejected instead of quietly becoming 28 February. */
    val formatter: DateTimeFormatter by lazy {
        DateTimeFormatter.ofPattern(pattern.replace("yyyy", "uuuu").replace("yy", "uu"), Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
    }
}

data class DateDetection(val best: DateFormat, val alternatives: List<DateFormat>, val ambiguous: Boolean)

object DateParsing {
    val FORMATS = listOf(
        DateFormat("yyyy-M-d", "2026-11-05"), DateFormat("yyyy/M/d", "2026/11/05"), DateFormat("d/M/yyyy", "05/11/2026"), DateFormat("M/d/yyyy", "11/05/2026"),
        DateFormat("d-M-yyyy", "05-11-2026"), DateFormat("M-d-yyyy", "11-05-2026"), DateFormat("d.M.yyyy", "05.11.2026"), DateFormat("d/M/yy", "05/11/26"),
        DateFormat("M/d/yy", "11/05/26"), DateFormat("d MMM yyyy", "05 Nov 2026"), DateFormat("d-MMM-yyyy", "05-Nov-2026"), DateFormat("d-MMM-yy", "05-Nov-26"),
        DateFormat("MMM d, yyyy", "Nov 5, 2026"), DateFormat("MMMM d, yyyy", "November 5, 2026"), DateFormat("d MMMM yyyy", "5 November 2026"),
    )
    private val TIME_SUFFIX = Regex("""[ T]\d{1,2}:\d{2}(:\d{2})?(\.\d+)?(\s?[AaPp][Mm])?(\s?(Z|[+-]\d{2}:?\d{2}))?$""")

    fun parse(raw: String, format: DateFormat): LocalDate? {
        val text = raw.trim().replace(TIME_SUFFIX, "").trim()
        if (text.isEmpty()) return null
        return runCatching { LocalDate.parse(text, format.formatter) }.getOrNull()
            ?.takeIf { it.year in 1990..2100 }
    }

    /**
     * Picks the format that reads the most sample dates. When day-first and month-first both read
     * every sample (05/11/2026), the file is ambiguous: the user's habit decides, and the screen
     * shows the result so a wrong guess is visible before anything is saved.
     */
    fun detect(samples: List<String>, dayFirstDefault: Boolean): DateDetection? {
        val values = samples.filter { it.isNotBlank() }
        if (values.isEmpty()) return null
        val scored = FORMATS.map { f -> f to values.count { parse(it, f) != null } }
        val top = scored.maxOf { it.second }
        if (top == 0) return null
        val good = scored.filter { it.second >= maxOf(1, (top * 0.95).toInt()) && it.second == top }.map { it.first }
        val ambiguous = good.size > 1 && good.map { it.pattern.replace("d", "x").replace("M", "x") }.distinct().size < good.size + 0 &&
            good.any { it.pattern.startsWith("d/") || it.pattern.startsWith("d-") || it.pattern.startsWith("d.") } && good.any { it.pattern.startsWith("M/") || it.pattern.startsWith("M-") }
        val best = if (ambiguous) good.first { dayFirstDefault == (it.pattern.startsWith("d")) } else good.first()
        return DateDetection(best, good, ambiguous)
    }
}

enum class RowStatus { NEW, DUPLICATE, ERROR }

data class PlannedRow(
    val line: Int, val date: LocalDate?, val description: String, val amount: BigDecimal?, val direction: TransactionDirection?,
    val balance: BigDecimal?, val status: RowStatus, val problem: String? = null,
)

data class ImportSettings(
    val mapping: ColumnMapping, val dateFormat: DateFormat, val decimal: MoneyNumbers.Decimal,
    /** Credit-card exports often show charges as positive amounts. */
    val invertSign: Boolean = false,
)

data class ImportPlan(val rows: List<PlannedRow>) {
    val newRows get() = rows.filter { it.status == RowStatus.NEW }
    val duplicates get() = rows.count { it.status == RowStatus.DUPLICATE }
    val errors get() = rows.count { it.status == RowStatus.ERROR }
    val moneyIn: BigDecimal get() = newRows.filter { it.direction == TransactionDirection.CREDIT }.fold(BigDecimal.ZERO) { a, r -> a + (r.amount ?: BigDecimal.ZERO) }
    val moneyOut: BigDecimal get() = newRows.filter { it.direction == TransactionDirection.DEBIT }.fold(BigDecimal.ZERO) { a, r -> a + (r.amount ?: BigDecimal.ZERO) }
    val firstDate: LocalDate? get() = newRows.mapNotNull { it.date }.minOrNull()
    val lastDate: LocalDate? get() = newRows.mapNotNull { it.date }.maxOrNull()
}

object CsvImportPlanner {
    /**
     * Turns file rows into a preview. A row that is already in the account's history (same
     * direction and amount within a day) is marked a duplicate; each existing entry can absorb only
     * one file row, so two real purchases of the same amount on one day are not both dropped.
     * [existing] must be the target account's current transactions.
     */
    fun plan(table: CsvTable, headerRow: Int, settings: ImportSettings, existing: List<Transaction>, zone: ZoneId = ZoneId.systemDefault()): ImportPlan {
        val m = settings.mapping
        val used = BooleanArray(existing.size)
        val out = ArrayList<PlannedRow>()
        for (index in (headerRow + 1) until table.rows.size) {
            val cells = table.rows[index]
            fun cell(i: Int?) = i?.let { cells.getOrNull(it) }.orEmpty().trim()
            val line = index + 1
            val dateText = cell(m.date); val description = cell(m.description)
            val balance = cell(m.balance).takeIf { it.isNotEmpty() }?.let { MoneyNumbers.parse(it, settings.decimal)?.value }
            val hasAmountText = cell(m.amount).isNotEmpty() || cell(m.debit).isNotEmpty() || cell(m.credit).isNotEmpty()
            if (!hasAmountText && description.isEmpty()) continue   // blank, section or opening-balance line
            val date = DateParsing.parse(dateText, settings.dateFormat)
                ?: run { out += error(line, description, if (dateText.isEmpty()) "No date" else "Date \"$dateText\" does not match the chosen format"); null } ?: continue

            val (amount, direction, problem) = amountAndDirection(cells, m, settings)
            if (amount == null || direction == null) { out += error(line, description, problem ?: "No amount", date); continue }

            val hit = existing.indices.firstOrNull { k ->
                !used[k] && existing[k].direction == direction && existing[k].amount.compareTo(amount) == 0 &&
                    abs(java.time.temporal.ChronoUnit.DAYS.between(existing[k].timestamp.atZone(zone).toLocalDate(), date)) <= 1
            }
            if (hit != null) used[hit] = true
            out += PlannedRow(line, date, description, amount, direction, balance, if (hit != null) RowStatus.DUPLICATE else RowStatus.NEW, if (hit != null) "Already in your history" else null)
        }
        return ImportPlan(out)
    }

    private fun error(line: Int, desc: String, why: String, date: LocalDate? = null) =
        PlannedRow(line, date, desc, null, null, null, RowStatus.ERROR, why)

    private data class Result3(val amount: BigDecimal?, val dir: TransactionDirection?, val problem: String?)
    private operator fun Result3.component1() = amount
    private operator fun Result3.component2() = dir
    private operator fun Result3.component3() = problem

    private fun amountAndDirection(cells: List<String>, m: ColumnMapping, s: ImportSettings): Result3 {
        fun at(i: Int?) = i?.let { cells.getOrNull(it) }.orEmpty().trim()
        if (m.split) {
            val d = at(m.debit).takeIf { it.isNotEmpty() }?.let { MoneyNumbers.parse(it, s.decimal) }
            val c = at(m.credit).takeIf { it.isNotEmpty() }?.let { MoneyNumbers.parse(it, s.decimal) }
            val dv = d?.value?.abs()?.takeIf { it.signum() > 0 }; val cv = c?.value?.abs()?.takeIf { it.signum() > 0 }
            return when {
                dv != null && cv != null -> Result3(null, null, "Both a debit and a credit are filled in")
                dv != null -> Result3(dv, TransactionDirection.DEBIT, null)
                cv != null -> Result3(cv, TransactionDirection.CREDIT, null)
                else -> Result3(null, null, "No amount")
            }
        }
        val text = at(m.amount)
        val p = MoneyNumbers.parse(text, s.decimal) ?: return Result3(null, null, if (text.isEmpty()) "No amount" else "Amount \"$text\" is not a number")
        if (p.value.signum() == 0) return Result3(null, null, "Amount is zero")
        var out = when (p.marker) { MoneyNumbers.Marker.DEBIT -> true; MoneyNumbers.Marker.CREDIT -> false; null -> p.negative }
        if (s.invertSign) out = !out
        return Result3(p.value.abs(), if (out) TransactionDirection.DEBIT else TransactionDirection.CREDIT, null)
    }
}
