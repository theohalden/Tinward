package dev.kosh.financetracker.core.finance

import dev.kosh.financetracker.core.classifier.CardTransactionClassifier
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionType
import dev.kosh.financetracker.core.model.TransactionSource
import dev.kosh.financetracker.core.parser.TransactionMessageStatus

/** Interpret sources without rewriting historical classifications or erasing evidence. */
object LedgerProjection {
    fun project(rows: List<Transaction>): List<Transaction> {
        val completed = rows.filterNot {
            it.source == TransactionSource.SMS && TransactionMessageStatus.isNonCompleted(it.rawSourceText.orEmpty())
        }
        val classified = CardTransactionClassifier.projectAll(completed).map { row ->
            ConfirmedClassification.type(row)?.let { row.copy(type = it) } ?: row
        }
        val statements = classified.filter { it.source == TransactionSource.STATEMENT }
        val zone = java.time.ZoneId.systemDefault()
        // Everything below used to be recomputed per row by scanning every statement row, which made
        // this quadratic. The date span of each statement group and the set of (amount, direction,
        // day) triples are now worked out once; the decisions are exactly the same.
        data class Span(val first: java.time.LocalDate, val last: java.time.LocalDate)
        fun span(rows: List<Transaction>): Span? {
            if (rows.isEmpty()) return null
            val days = rows.map { it.timestamp.atZone(zone).toLocalDate() }
            return Span(days.min(), days.max())
        }
        val spanByAccount = statements.groupBy { it.bank to it.accountSuffix }.mapValues { span(it.value) }
        val spanByBank = statements.groupBy { it.bank }.mapValues { span(it.value) }
        val statementKeys = statements.mapTo(HashSet()) { Triple(it.amount.stripTrailingZeros(), it.direction, it.timestamp.atZone(zone).toLocalDate()) }
        val reconciled = classified.map { row ->
            val accountSpan = if (row.accountSuffix == null || row.accountSuffix.length != 4) spanByBank[row.bank] else spanByAccount[row.bank to row.accountSuffix]
            val date = row.timestamp.atZone(zone).toLocalDate()
            val withinCoverage = accountSpan != null &&
                row.type != TransactionType.CREDIT_CARD_PURCHASE && row.type != TransactionType.CREDIT_CARD_PAYMENT &&
                date >= accountSpan.first && date <= accountSpan.last
            val manualOverlap = row.source == TransactionSource.MANUAL && Triple(row.amount.stripTrailingZeros(), row.direction, date) in statementKeys
            if ((row.source == TransactionSource.SMS && withinCoverage) || manualOverlap) {
                row.copy(reconciliationNote = "Unmatched source record: excluded from totals until reconciled. Original data preserved.", isUnmatchedOverlap = true)
            } else row
        }
        val transfers = TransferDetector.findTransferPairs(reconciled.filter { it.reconciliationNote == null })
        return reconciled.map { transaction ->
            when {
                ConfirmedClassification.type(transaction) != null -> transaction
                transaction.id in transfers -> transaction.copy(type = TransactionType.TRANSFER)
                transaction.type == TransactionType.TRANSFER && transaction.source != TransactionSource.MANUAL ->
                    transaction.copy(type = TransactionType.UNKNOWN)
                isAmbiguousBankPayment(transaction) -> transaction.copy(
                    type = TransactionType.UNKNOWN,
                    reconciliationNote = "Payment to a financial account: purpose unconfirmed. It may be a self-transfer or repayment; excluded from spending and income until resolved.",
                )
                else -> transaction
            }
        }
    }

    /** A recipient's bank name does not establish a purchase or account ownership. */
    private fun isAmbiguousBankPayment(row: Transaction): Boolean {
        if (row.source != TransactionSource.SMS || row.direction != dev.kosh.financetracker.core.model.TransactionDirection.DEBIT ||
            row.type !in setOf(TransactionType.EXPENSE, TransactionType.UNKNOWN)) return false
        val recipient = Regex("(?im)^To\\s+([^\\r\\n]+)").find(row.rawSourceText.orEmpty())
            ?.groupValues?.get(1)?.trim()?.lowercase()?.replace(Regex("\\s+"), " ") ?: return false
        return recipient in setOf("kotak", "kotak bank", "kotak mahindra bank", "kotak mahindra bank ltd",
            "hdfc", "hdfc bank", "hdfc bank ltd", "slice", "slice bank")
    }

}
