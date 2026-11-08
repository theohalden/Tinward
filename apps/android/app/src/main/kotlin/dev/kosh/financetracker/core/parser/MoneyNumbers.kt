package dev.kosh.financetracker.core.parser

import java.math.BigDecimal

/**
 * Reads amounts the way people write them in different countries: 1,234.56 (US/UK/India),
 * 1.234,56 (much of Europe), 1 234,56 (spaces), (123.45) or -123.45 for negatives, and
 * trailing Dr/Cr markers. Shared by SMS parsing and CSV import.
 */
object MoneyNumbers {
    enum class Decimal(val char: Char, val label: String) { DOT('.', "1,234.56"), COMMA(',', "1.234,56") }

    data class Parsed(val value: BigDecimal, val negative: Boolean, val marker: Marker?)
    enum class Marker { DEBIT, CREDIT }

    private val MARKER = Regex("""(?i)\s*\b(dr|db|debit|cr|credit)\b\.?\s*$""")

    /** Which decimal separator a set of sample values uses. Votes across samples; ties go to [fallback]. */
    fun detectDecimal(samples: List<String>, fallback: Decimal = Decimal.DOT): Decimal {
        var dot = 0; var comma = 0
        for (raw in samples) {
            val s = clean(raw)
            val lastDot = s.lastIndexOf('.'); val lastComma = s.lastIndexOf(',')
            when {
                lastDot >= 0 && lastComma >= 0 -> if (lastDot > lastComma) dot++ else comma++
                lastComma >= 0 && s.length - lastComma - 1 in 1..2 -> comma++
                lastDot >= 0 && s.length - lastDot - 1 in 1..2 -> dot++
                // a lone separator followed by exactly 3 digits reads as a thousands mark: no vote
            }
        }
        return when { comma > dot -> Decimal.COMMA; dot > comma -> Decimal.DOT; else -> fallback }
    }

    private fun clean(raw: String) = raw.trim().replace('\u00A0', ' ').replace('\u2212', '-').replace(Regex("""[^\d.,()\-+ ]"""), "").trim()

    /** Parses [raw] with a known decimal separator. Null when it is not a number. */
    fun parse(raw: String, decimal: Decimal = Decimal.DOT): Parsed? {
        var text = raw.trim().replace('\u00A0', ' ').replace('\u2212', '-')
        var marker: Marker? = null
        MARKER.find(text)?.let { m ->
            marker = if (m.groupValues[1].lowercase().startsWith("d")) Marker.DEBIT else Marker.CREDIT
            text = text.removeRange(m.range)
        }
        var negative = false
        if (text.startsWith("(") && text.endsWith(")")) { negative = true; text = text.substring(1, text.length - 1) }
        text = text.replace(Regex("""[^\d.,\-+ ]"""), "").trim()   // currency symbols, codes, stray text
        if (text.endsWith("-")) { negative = true; text = text.dropLast(1) }
        if (text.startsWith("-")) { negative = true; text = text.drop(1) }
        if (text.startsWith("+")) text = text.drop(1)
        text = text.replace(" ", "")
        if (text.isEmpty() || !text.any(Char::isDigit)) return null
        val (thousands, dec) = if (decimal == Decimal.DOT) ',' to '.' else '.' to ','
        text = text.replace(thousands.toString(), "")
        if (text.count { it == dec } > 1) return null
        text = text.replace(dec, '.')
        val value = text.toBigDecimalOrNull() ?: return null
        return Parsed(value, negative, marker)
    }

    /** For free text (SMS) where the format is not known in advance. */
    fun parseAuto(raw: String): BigDecimal? = parse(raw, detectDecimal(listOf(raw)))?.value
}
