package dev.kosh.financetracker.data.repository

import androidx.room.withTransaction
import dev.kosh.financetracker.core.finance.AutoRule
import dev.kosh.financetracker.core.finance.RuleEngine
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.data.database.FinanceDatabase
import dev.kosh.financetracker.data.database.RuleChangeEntity
import dev.kosh.financetracker.data.database.RuleDao
import dev.kosh.financetracker.data.database.TransactionDao
import dev.kosh.financetracker.data.database.toDomain
import dev.kosh.financetracker.data.database.toEntity
import dev.kosh.financetracker.feature.plus.Entitlements
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** A batch of changes that can be undone as one. [batchId] 0 means nothing changed. */
data class RuleRun(val batchId: Long, val changed: Int)

@Singleton
class RulesRepository @Inject constructor(
    private val database: FinanceDatabase,
    private val rules: RuleDao,
    private val transactions: TransactionDao,
    private val entitlements: Entitlements,
) {
    val rulesFlow: Flow<List<AutoRule>> = rules.observeRules().map { l -> l.map { it.toDomain() } }
    val recentChanges: Flow<List<RuleChangeEntity>> = rules.observeRecent(30)

    suspend fun save(rule: AutoRule) = database.withTransaction {
        if (rule.id == 0L) rules.insert(rule.toEntity(rules.maxPosition() + 1))
        else rules.getRules().firstOrNull { it.id == rule.id }?.let { rules.update(rule.toEntity(it.position)) }
    }

    suspend fun delete(id: Long) = rules.delete(id)
    suspend fun setEnabled(id: Long, enabled: Boolean) = rules.setEnabled(id, enabled)

    /** Moves a rule one place up (-1) or down (+1) in priority. */
    suspend fun move(id: Long, by: Int) = database.withTransaction {
        val order = rules.getRules().map { it.id }.toMutableList()
        val from = order.indexOf(id).takeIf { it >= 0 } ?: return@withTransaction
        val to = (from + by).coerceIn(0, order.lastIndex)
        order.add(to, order.removeAt(from))
        order.forEachIndexed { i, rid -> rules.setPosition(rid, i) }
    }

    suspend fun previewChanges(rule: AutoRule? = null): List<dev.kosh.financetracker.core.finance.RuleChange> {
        val list = activeRules(rule)
        return RuleEngine.changes(list, transactions.getAll().map { it.toDomain() })
    }

    suspend fun matchCount(rule: AutoRule): Int = RuleEngine.matchCount(rule, transactions.getAll().map { it.toDomain() })

    /** Applies the rules to everything already recorded. Plus only; undoable with the returned batch id. */
    suspend fun applyToExisting(): RuleRun = apply(transactions.getAll().map { it.toDomain() }, automatic = false)

    /** Called for rows that just arrived by SMS or CSV. Does nothing without Plus. */
    suspend fun applyToNew(ids: Collection<Long>): RuleRun {
        if (ids.isEmpty() || !entitlements.isPlus) return RuleRun(0, 0)
        return apply(ids.mapNotNull { transactions.getByIdOnce(it) }.filter { it.deletedAtEpochMillis == null }.map { it.toDomain() }, automatic = true)
    }

    private suspend fun activeRules(extra: AutoRule? = null): List<AutoRule> {
        val stored = rules.getRules().map { it.toDomain() }
        return if (extra == null) stored else stored.map { if (it.id == extra.id && extra.id != 0L) extra else it }.let { if (extra.id == 0L) it + extra else it }
    }

    private suspend fun apply(candidates: List<Transaction>, automatic: Boolean): RuleRun {
        if (!entitlements.isPlus) return RuleRun(0, 0)
        val active = rules.getRules().map { it.toDomain() }
        val changes = RuleEngine.changes(active, candidates)
        if (changes.isEmpty()) return RuleRun(0, 0)
        val byId = candidates.associateBy { it.id }
        val names = active.associate { it.id to it.name }
        return database.withTransaction {
            val batch = rules.maxBatch() + 1
            val now = System.currentTimeMillis()
            val log = changes.map { c ->
                val before = byId.getValue(c.transactionId)
                c.category?.let { transactions.updateCategory(c.transactionId, it) }
                c.label?.let { transactions.updateCustomLabel(c.transactionId, it) }
                RuleChangeEntity(
                    batchId = batch, transactionId = c.transactionId, ruleId = c.ruleId, ruleName = names[c.ruleId].orEmpty(),
                    oldCategory = before.category, oldLabel = before.customLabel, newCategory = c.category, newLabel = c.label,
                    appliedAtEpochMillis = now, automatic = automatic,
                )
            }
            rules.insertChanges(log)
            RuleRun(batch, log.size)
        }
    }

    /** Puts every transaction in the batch back as it was, unless the user has since changed it again. Returns how many transactions were restored. */
    suspend fun undo(batchId: Long): Int = database.withTransaction {
        val restored = HashSet<Long>()
        for (c in rules.changesInBatch(batchId)) {
            val now = transactions.getByIdOnce(c.transactionId)?.toDomain() ?: continue
            if (c.newCategory != null && now.category == c.newCategory) { transactions.updateCategory(c.transactionId, c.oldCategory ?: dev.kosh.financetracker.core.model.TransactionCategory.UNCATEGORIZED); restored += c.transactionId }
            if (c.newLabel != null && now.customLabel == c.newLabel) { transactions.updateCustomLabel(c.transactionId, c.oldLabel); restored += c.transactionId }
        }
        rules.markUndone(batchId)
        restored.size
    }
}
