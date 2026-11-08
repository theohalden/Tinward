package dev.kosh.financetracker.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import dev.kosh.financetracker.core.finance.Frequency
import dev.kosh.financetracker.core.finance.RecurringItem
import dev.kosh.financetracker.core.finance.RecurringKind
import dev.kosh.financetracker.core.finance.SavingsGoal
import dev.kosh.financetracker.core.model.TransactionCategory
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

@Entity(tableName = "recurring_items")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: BigDecimal,
    val currency: String,
    val kind: RecurringKind,
    val frequency: Frequency,
    val anchorEpochDay: Long,
    val accountId: Long?,
    val category: TransactionCategory?,
    val remindDaysBefore: Int,
    val active: Boolean,
    val paidThroughEpochDay: Long?,
)

fun RecurringEntity.toDomain() = RecurringItem(
    id, name, amount, currency, kind, frequency, LocalDate.ofEpochDay(anchorEpochDay), accountId, category,
    remindDaysBefore, active, paidThroughEpochDay?.let(LocalDate::ofEpochDay),
)

fun RecurringItem.toEntity() = RecurringEntity(
    id, name, amount, currency, kind, frequency, anchor.toEpochDay(), accountId, category, remindDaysBefore, active, paidThrough?.toEpochDay(),
)

/** [category] null is the overall monthly budget; otherwise a budget for that category. */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: TransactionCategory?,
    val amount: BigDecimal,
    val currency: String,
)

@Entity(tableName = "savings_goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val target: BigDecimal,
    val saved: BigDecimal,
    val currency: String,
    val targetDateEpochDay: Long?,
    val createdAtEpochMillis: Long,
    val archived: Boolean,
)

fun GoalEntity.toDomain() = SavingsGoal(id, name, target, saved, currency, targetDateEpochDay?.let(LocalDate::ofEpochDay), archived)
fun SavingsGoal.toEntity() = GoalEntity(id, name, target, saved, currency, targetDate?.toEpochDay(), System.currentTimeMillis(), archived)

@Dao
interface PlanningDao {
    @Query("SELECT * FROM recurring_items ORDER BY active DESC, anchorEpochDay, id")
    fun observeRecurring(): Flow<List<RecurringEntity>>
    @Query("SELECT * FROM recurring_items WHERE id = :id") suspend fun recurring(id: Long): RecurringEntity?
    @Insert suspend fun insert(item: RecurringEntity): Long
    @Update suspend fun update(item: RecurringEntity)
    @Query("DELETE FROM recurring_items WHERE id = :id") suspend fun deleteRecurring(id: Long)
    @Query("UPDATE recurring_items SET paidThroughEpochDay = :day WHERE id = :id") suspend fun setPaidThrough(id: Long, day: Long?)
    @Query("SELECT COUNT(*) FROM recurring_items") suspend fun recurringCount(): Int

    @Query("SELECT * FROM budgets ORDER BY category IS NOT NULL, id") fun observeBudgets(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets") suspend fun budgets(): List<BudgetEntity>
    @Insert suspend fun insertBudget(b: BudgetEntity): Long
    @Update suspend fun updateBudget(b: BudgetEntity)
    @Query("DELETE FROM budgets WHERE id = :id") suspend fun deleteBudget(id: Long)

    @Query("SELECT * FROM savings_goals ORDER BY archived, id") fun observeGoals(): Flow<List<GoalEntity>>
    @Query("SELECT * FROM savings_goals WHERE archived = 0") suspend fun activeGoals(): List<GoalEntity>
    @Query("SELECT * FROM savings_goals WHERE id = :id") suspend fun goal(id: Long): GoalEntity?
    @Insert suspend fun insertGoal(g: GoalEntity): Long
    @Update suspend fun updateGoal(g: GoalEntity)
}
