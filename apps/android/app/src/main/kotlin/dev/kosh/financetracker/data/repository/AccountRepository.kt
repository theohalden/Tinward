package dev.kosh.financetracker.data.repository

import androidx.room.withTransaction
import dev.kosh.financetracker.core.finance.AccountBootstrap
import dev.kosh.financetracker.core.finance.AccountLedger
import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionType
import dev.kosh.financetracker.data.database.AccountDao
import dev.kosh.financetracker.data.database.FinanceDatabase
import dev.kosh.financetracker.data.database.TransactionDao
import dev.kosh.financetracker.data.database.TransactionEntity
import dev.kosh.financetracker.data.database.toDomain
import dev.kosh.financetracker.data.database.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val database: FinanceDatabase,
    private val accounts: AccountDao,
    private val transactions: TransactionDao,
) {
    fun observeAccounts(): Flow<List<Account>> = accounts.observeAll().map { list -> list.map { it.toDomain() } }
    fun observeAccount(id: Long): Flow<Account?> = accounts.observe(id).map { it?.toDomain() }

    suspend fun create(account: Account): Long = accounts.insert(account.copy(id = 0, sortOrder = accounts.count()).toEntity())
    suspend fun update(account: Account) = accounts.update(account.toEntity())
    suspend fun setArchived(id: Long, archived: Boolean) = accounts.setArchived(id, archived)

    suspend fun move(id: Long, up: Boolean) {
        val list = accounts.getAll().filter { !it.archived }
        val i = list.indexOfFirst { it.id == id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) return
        database.withTransaction {
            // Rewrite a dense 0..n order so swaps never collide with stale values.
            val order = list.map { it.id }.toMutableList().also { java.util.Collections.swap(it, i, j) }
            order.forEachIndexed { index, accountId -> accounts.setOrder(accountId, index) }
        }
    }

    /** Both legs are written together or not at all. */
    suspend fun transfer(fromId: Long, toId: Long, amount: BigDecimal, at: Instant, note: String?): Result<Unit> = database.withTransaction {
        val all = accounts.getAll().map { it.toDomain() }.associateBy { it.id }
        val from = all[fromId] ?: return@withTransaction Result.failure(IllegalArgumentException("Account not found."))
        val to = all[toId] ?: return@withTransaction Result.failure(IllegalArgumentException("Account not found."))
        AccountLedger.transfer(from, to, amount, at, note).map { legs -> legs.forEach { transactions.insert(it.toEntity()) } }
    }

    /** Records the difference between what Tinward tracked and the real balance. */
    suspend fun reconcile(account: Account, tracked: BigDecimal, actual: BigDecimal): Boolean {
        val adjustment = AccountLedger.adjustment(account, tracked, actual, Instant.now()) ?: return false
        transactions.insert(adjustment.toEntity())
        return true
    }

    /**
     * Attaches transactions that have a bank + last-four but no account yet. Reuses an existing
     * account when exactly one matches; creates one when none does; leaves the row alone when
     * the match is ambiguous. For a newly created bank/wallet account the opening balance is
     * set so tracked balance equals the latest balance an SMS or statement reported.
     */
    suspend fun linkUnassigned(): Int = database.withTransaction {
        val observations = transactions.getAllObservations()
        val unassigned = observations.filter { it.accountId == null && !it.accountSuffix.isNullOrBlank() }
        if (unassigned.isEmpty()) return@withTransaction 0
        val canonicalIds = transactions.getAll().map { it.id }.toSet()
        val existing = accounts.getAll().map { it.toDomain() }.toMutableList()
        var linked = 0

        unassigned.groupBy { (it.bank?.trim()?.lowercase()) to it.accountSuffix!!.trim() }.forEach { (key, group) ->
            val (bankKey, suffix) = key
            val matches = existing.filter { it.last4 == suffix && (it.institution.isNullOrBlank() || bankKey == null || it.institution.trim().lowercase() == bankKey) }
            val target: Long = when {
                matches.size == 1 -> matches[0].id
                matches.size > 1 -> return@forEach
                else -> {
                    val created = newAccountFor(group, canonicalIds, existing.size)
                    val id = accounts.insert(created.toEntity())
                    existing += created.copy(id = id)
                    id
                }
            }
            accounts.assignTransactions(target, group.map { it.id })
            linked += group.size
        }
        linked
    }

    private fun newAccountFor(group: List<TransactionEntity>, canonicalIds: Set<Long>, order: Int): Account {
        val bank = group.firstNotNullOfOrNull { it.bank?.takeIf(String::isNotBlank) }
        val suffix = group.first().accountSuffix!!.trim()
        val isCard = group.any { it.type == TransactionType.CREDIT_CARD_PURCHASE }
        val opening = AccountBootstrap.openingFor(group.map { it.toDomain() }, canonicalIds, isCard, Instant.now())
        return Account(
            name = "${bank ?: "Account"} \u2022\u2022$suffix",
            type = if (isCard) AccountType.CREDIT_CARD else AccountType.BANK,
            institution = bank, last4 = suffix, currency = "INR",
            openingBalance = opening.amount, openingDate = opening.date, sortOrder = order,
        )
    }
}
