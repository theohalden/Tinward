package dev.kosh.financetracker.data.repository

import androidx.room.withTransaction
import dev.kosh.financetracker.core.categorization.CategoryEngine
import dev.kosh.financetracker.core.classifier.IncomeClassifier
import dev.kosh.financetracker.core.csv.PlannedRow
import dev.kosh.financetracker.core.csv.RowStatus
import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.core.model.PaymentMethod
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionSource
import dev.kosh.financetracker.core.model.TransactionType
import dev.kosh.financetracker.data.database.FinanceDatabase
import dev.kosh.financetracker.data.database.TransactionDao
import dev.kosh.financetracker.data.database.toDomain
import dev.kosh.financetracker.data.database.toEntity
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class CsvImportResult(val added: Int, val alreadyPresent: Int)

@Singleton
class CsvImportRepository @Inject constructor(
    private val database: FinanceDatabase,
    private val dao: TransactionDao,
    private val transactions: TransactionRepository,
    private val rules: RulesRepository,
) {
    /** What the account already holds, for duplicate checks. */
    suspend fun existingFor(account: Account, allAccounts: List<Account>): List<Transaction> {
        // A debit card spends from its linked bank account, so its entries count as that account's.
        val ids = setOf(account.id) + allAccounts.filter { it.type == AccountType.DEBIT_CARD && it.linkedAccountId == account.id }.map { it.id }
        return dao.getAll().filter { it.accountId in ids }.map { it.toDomain() }
    }

    /**
     * Saves the rows the preview marked new. A stable id per row makes re-importing the same file
     * harmless, and a row deleted earlier stays deleted. Nothing here is treated as bank-verified.
     */
    suspend fun import(account: Account, rows: List<PlannedRow>, zone: ZoneId = ZoneId.systemDefault()): CsvImportResult {
        val occurrences = HashMap<String, Int>()
        var added = 0; var present = 0; val newIds = ArrayList<Long>()
        database.withTransaction {
            for (row in rows.filter { it.status == RowStatus.NEW }) {
                val date = row.date ?: continue; val amount = row.amount ?: continue; val direction = row.direction ?: continue
                val key = "csv:${account.id}:$date:${if (direction == TransactionDirection.DEBIT) "-" else "+"}$amount:${row.description.lowercase().hashCode()}"
                val n = occurrences.merge(key, 1, Int::plus)!! - 1
                val merchant = row.description.take(60).ifBlank { null }
                val card = account.type == AccountType.CREDIT_CARD
                val type = when (direction) {
                    TransactionDirection.DEBIT -> if (card) TransactionType.CREDIT_CARD_PURCHASE else TransactionType.EXPENSE
                    // A credit alone does not prove income (it can be a refund or a transfer).
                    else -> if (!card && IncomeClassifier.isLikelySalary(merchant, null)) TransactionType.INCOME else TransactionType.UNKNOWN
                }
                val tx = Transaction(
                    timestamp = date.atTime(LocalTime.NOON).atZone(zone).toInstant(), amount = amount, direction = direction, type = type,
                    category = if (direction == TransactionDirection.DEBIT) CategoryEngine.categorize(merchant) else TransactionCategory.UNCATEGORIZED,
                    merchant = merchant, rawMerchant = merchant, accountId = account.id, accountSuffix = account.last4, bank = account.institution,
                    paymentMethod = PaymentMethod.UNKNOWN, source = TransactionSource.CSV, sourceMessageId = "$key:$n", rawSourceText = null,
                    confidence = 0.9, notes = null, createdAt = Instant.now(), balanceAfter = row.balance, currency = account.currency,
                )
                val newId = dao.insertIfNew(tx.toEntity())
                if (newId == -1L) present++ else { added++; newIds += newId }
            }
        }
        transactions.reconcileTransfers(); transactions.reconcileIncomeClassification(); transactions.reconcileCategories()
        rules.applyToNew(newIds)
        return CsvImportResult(added, present)
    }
}
