package dev.kosh.financetracker.feature.detail

import java.math.BigDecimal

/** Refund relationships for the transaction on screen. */
data class DetailExtras(
    val refunded: BigDecimal = BigDecimal.ZERO,
    /** Name of the purchase this entry is a refund of, if it is one. */
    val refundOfName: String? = null,
    /** Name of the account or card this is recorded on. */
    val accountName: String? = null,
)
