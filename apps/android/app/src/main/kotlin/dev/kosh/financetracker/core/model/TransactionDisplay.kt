package dev.kosh.financetracker.core.model

/**
 * The name to show for a transaction. Transfers between the user's own accounts have no merchant,
 * so they are named by what they are instead of being shown as "Unknown".
 */
fun Transaction.displayMerchant(): String = merchant?.takeIf { it.isNotBlank() } ?: when (type) {
    TransactionType.TRANSFER -> when (direction) {
        TransactionDirection.DEBIT -> "Self transfer out"
        TransactionDirection.CREDIT -> "Self transfer in"
        else -> "Self transfer"
    }
    TransactionType.CREDIT_CARD_PAYMENT -> "Credit card payment"
    TransactionType.CASH_WITHDRAWAL -> "Cash withdrawal"
    TransactionType.ADJUSTMENT -> "Balance correction"
    else -> "Unknown"
}
