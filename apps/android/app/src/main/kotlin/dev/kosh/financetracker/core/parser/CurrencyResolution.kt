package dev.kosh.financetracker.core.parser

/** Turns the parser's neutral "$" into a real currency using the user's choice. */
object CurrencyResolution {
    val DOLLAR_CURRENCIES = listOf("USD", "AUD", "CAD", "NZD", "SGD", "HKD")

    fun resolve(parsedCurrency: String, dollarMeans: String): String =
        if (parsedCurrency == MessageMoney.BARE_DOLLAR) dollarMeans else parsedCurrency

    /** Default for "$": the user's main currency if it is a dollar, else US dollars. */
    fun defaultDollar(baseCurrency: String): String = if (baseCurrency in DOLLAR_CURRENCIES) baseCurrency else "USD"
}
