package dev.kosh.financetracker.core.model

import java.math.BigDecimal
import java.time.Instant

/**
 * Accounts and cards are containers the user creates by hand; Tinward never logs in to a bank.
 * A debit card has no balance of its own: its transactions move [linkedAccountId]'s balance.
 */
enum class AccountType(val label: String, val isCard: Boolean = false) {
    BANK("Bank account"),
    CASH("Cash"),
    WALLET("Wallet"),
    OTHER("Other"),
    DEBIT_CARD("Debit card", isCard = true),
    CREDIT_CARD("Credit card", isCard = true),
}

data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val institution: String? = null,
    /** Masked reference only (last four). Full numbers, CVV and PINs are never stored. */
    val last4: String? = null,
    val currency: String = "INR",
    /** Asset: money in the account at [openingDate]. Credit card: amount owed at [openingDate]. */
    val openingBalance: BigDecimal = BigDecimal.ZERO,
    val openingDate: Instant = Instant.now(),
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val linkedAccountId: Long? = null,
    val creditLimit: BigDecimal? = null,
    val statementDay: Int? = null,
    val dueDay: Int? = null,
) {
    val isLiability: Boolean get() = type == AccountType.CREDIT_CARD
}
