package dev.kosh.financetracker.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import dev.kosh.financetracker.core.finance.AutoRule
import dev.kosh.financetracker.core.finance.RuleDirection
import dev.kosh.financetracker.core.model.TransactionCategory
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

/** [position] is the priority: 0 is checked first. */
@Entity(tableName = "auto_rules")
data class AutoRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean,
    val position: Int,
    val merchantContains: String?,
    val amountMin: BigDecimal?,
    val amountMax: BigDecimal?,
    val direction: String,
    val accountId: Long?,
    val setCategory: TransactionCategory?,
    val setLabel: String?,
    val overwrite: Boolean,
)

fun AutoRuleEntity.toDomain() = AutoRule(
    id, name, enabled, merchantContains, amountMin, amountMax,
    runCatching { RuleDirection.valueOf(direction) }.getOrDefault(RuleDirection.ANY), accountId, setCategory, setLabel, overwrite,
)

fun AutoRule.toEntity(position: Int) = AutoRuleEntity(
    id, name, enabled, position, merchantContains?.trim()?.ifBlank { null }, amountMin, amountMax, direction.name, accountId, setCategory, setLabel?.trim()?.ifBlank { null }, overwrite,
)

/** What a rule changed, with what was there before, so any batch can be put back exactly. */
@Entity(tableName = "rule_changes")
data class RuleChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val transactionId: Long,
    val ruleId: Long,
    val ruleName: String,
    val oldCategory: TransactionCategory?,
    val oldLabel: String?,
    val newCategory: TransactionCategory?,
    val newLabel: String?,
    val appliedAtEpochMillis: Long,
    val automatic: Boolean,
    val undone: Boolean = false,
)

@Dao
interface RuleDao {
    @Query("SELECT * FROM auto_rules ORDER BY position, id")
    fun observeRules(): Flow<List<AutoRuleEntity>>

    @Query("SELECT * FROM auto_rules ORDER BY position, id")
    suspend fun getRules(): List<AutoRuleEntity>

    @Insert suspend fun insert(rule: AutoRuleEntity): Long
    @Update suspend fun update(rule: AutoRuleEntity)
    @Query("DELETE FROM auto_rules WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE auto_rules SET position = :position WHERE id = :id") suspend fun setPosition(id: Long, position: Int)
    @Query("UPDATE auto_rules SET enabled = :enabled WHERE id = :id") suspend fun setEnabled(id: Long, enabled: Boolean)
    @Query("SELECT COALESCE(MAX(position), -1) FROM auto_rules") suspend fun maxPosition(): Int

    @Insert suspend fun insertChanges(changes: List<RuleChangeEntity>)
    @Query("SELECT COALESCE(MAX(batchId), 0) FROM rule_changes") suspend fun maxBatch(): Long
    @Query("SELECT * FROM rule_changes WHERE batchId = :batchId AND undone = 0") suspend fun changesInBatch(batchId: Long): List<RuleChangeEntity>
    @Query("UPDATE rule_changes SET undone = 1 WHERE batchId = :batchId") suspend fun markUndone(batchId: Long)
    @Query("SELECT * FROM rule_changes WHERE undone = 0 ORDER BY appliedAtEpochMillis DESC, id DESC LIMIT :limit") fun observeRecent(limit: Int): Flow<List<RuleChangeEntity>>
}
