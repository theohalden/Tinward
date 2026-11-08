package dev.kosh.financetracker.core.classifier

/**
 * Cheap keyword-based filter that decides whether an SMS is worth feeding into the
 * transaction pipeline at all. Deliberately over-inclusive: false positives get
 * discarded later by the parser/classifier, false negatives silently drop real
 * transactions, which is the worse failure mode.
 */
object FinancialMessageDetector {

    private val KEYWORDS = listOf(
        "debited",
        "credited",
        "spent",
        "txn",
        "transaction",
        "upi",
        "atm",
        "card",
        "a/c",
        "account",
        "inr",
        "rs.",
        "₹",
        "available balance",
        "emi",
        "neft",
        "imps",
        "rtgs",
        // Other countries and currencies: the parser decides what is real.
        "purchase", "withdrawal", "withdrawn", "deposit", "charged", "payment", "refund", "paid",
        "$", "£", "€", "¥", "usd", "eur", "gbp", "aud", "cad", "sgd", "aed", "nzd", "chf", "zar", "jpy",
    )

    fun isFinancialMessage(body: String): Boolean {
        if (body.isBlank()) return false
        val lower = body.lowercase()
        return KEYWORDS.any { lower.contains(it) }
    }
}
