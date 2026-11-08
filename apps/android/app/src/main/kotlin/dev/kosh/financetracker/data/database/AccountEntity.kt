package dev.kosh.financetracker.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.AccountType
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val institution: String?,
    val last4: String?,
    val currency: String,
    val openingBalance: BigDecimal,
    val openingDateEpochMillis: Long,
    val archived: Boolean,
    val sortOrder: Int,
    val linkedAccountId: Long?,
    val creditLimit: BigDecimal?,
    val statementDay: Int?,
    val dueDay: Int?,
    val createdAtEpochMillis: Long,
    val apr: BigDecimal? = null,
)

fun AccountEntity.toDomain() = Account(
    id, name, type, institution, last4, currency, openingBalance, Instant.ofEpochMilli(openingDateEpochMillis),
    archived, sortOrder, linkedAccountId, creditLimit, statementDay, dueDay, apr,
)

fun Account.toEntity() = AccountEntity(
    id, name, type, institution, last4, currency, openingBalance, openingDate.toEpochMilli(),
    archived, sortOrder, linkedAccountId, creditLimit, statementDay, dueDay, System.currentTimeMillis(), apr,
)

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY archived, sortOrder, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY archived, sortOrder, id")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observe(id: Long): Flow<AccountEntity?>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Insert suspend fun insert(account: AccountEntity): Long
    @Update suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE accounts SET sortOrder = :order WHERE id = :id")
    suspend fun setOrder(id: Long, order: Int)

    @Query("UPDATE transactions SET accountId = :accountId WHERE id IN (:ids)")
    suspend fun assignTransactions(accountId: Long, ids: List<Long>)
}
