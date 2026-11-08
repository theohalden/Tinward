package dev.kosh.financetracker.ui.format

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** The currency Home, Insights and Plan total in. Read by composables, so a change redraws them. */
object BaseCurrency {
    private val state = androidx.compose.runtime.mutableStateOf("INR")
    var code: String
        get() = state.value
        set(v) { state.value = v }
}

/**
 * Single entry point for money on screen. INR keeps Indian digit grouping
 * (12,34,567.89); any other ISO currency uses the device locale's grouping and
 * symbol. `hidden` masks digits for the privacy toggle while keeping the symbol.
 */
fun formatMoney(amount: BigDecimal, hidden: Boolean = false, currencyCode: String = BaseCurrency.code): String {
    if (currencyCode == "INR") return formatRupees(amount, hidden)
    val currency = Currency.getInstance(currencyCode)
    val symbol = currency.getSymbol(Locale.getDefault())
    if (hidden) return "$symbol*****"
    val nf = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        this.currency = currency
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return nf.format(amount.setScale(2, RoundingMode.HALF_UP))
}

/** Signed variant for lists: debits "-", credits "+", neutral (transfers) no sign. */
fun formatSignedMoney(amount: BigDecimal, isDebit: Boolean?, hidden: Boolean = false, currencyCode: String = BaseCurrency.code): String {
    val sign = when {
        hidden || isDebit == null -> ""
        isDebit -> "−"
        else -> "+"
    }
    return sign + formatMoney(amount.abs(), hidden, currencyCode)
}

/**
 * Indian digit grouping: last 3 digits together, then groups of 2
 * (12,34,567.89 not 1,234,567.89). `hidden` masks the digits for the
 * privacy/eye toggle while keeping the ₹ sign and layout stable.
 */
fun formatRupees(amount: BigDecimal, hidden: Boolean = false): String {
    if (hidden) return "₹*****"

    val rounded = amount.setScale(2, RoundingMode.HALF_UP)
    val negative = rounded.signum() < 0
    val plain = rounded.abs().toPlainString()
    val (intPart, fracPart) = plain.split(".").let { it[0] to it.getOrElse(1) { "00" } }

    return buildString {
        if (negative) append('-')
        append('₹')
        append(groupIndianDigits(intPart))
        append('.')
        append(fracPart.padEnd(2, '0').take(2))
    }
}

private fun groupIndianDigits(intDigits: String): String {
    if (intDigits.length <= 3) return intDigits

    val lastThree = intDigits.takeLast(3)
    var remaining = intDigits.dropLast(3)
    val groups = ArrayDeque<String>()
    while (remaining.length > 2) {
        groups.addFirst(remaining.takeLast(2))
        remaining = remaining.dropLast(2)
    }
    if (remaining.isNotEmpty()) groups.addFirst(remaining)
    groups.addLast(lastThree)
    return groups.joinToString(",")
}
