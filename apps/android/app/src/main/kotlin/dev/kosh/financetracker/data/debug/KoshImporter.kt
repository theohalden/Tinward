package dev.kosh.financetracker.data.debug

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kosh.financetracker.core.finance.Frequency
import dev.kosh.financetracker.core.finance.RecurringItem
import dev.kosh.financetracker.core.model.PaymentMethod
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionSource
import dev.kosh.financetracker.core.model.TransactionType
import dev.kosh.financetracker.data.budget.FixedBudgetStore
import dev.kosh.financetracker.data.database.FinanceDatabase
import dev.kosh.financetracker.data.database.MerchantCategoryMappingDao
import dev.kosh.financetracker.data.database.MerchantCategoryMappingEntity
import dev.kosh.financetracker.data.database.TransactionDao
import dev.kosh.financetracker.data.database.TransactionEntity
import dev.kosh.financetracker.data.database.toDomain
import dev.kosh.financetracker.data.repository.PlanningRepository
import dev.kosh.financetracker.data.repository.TransactionRepository
import dev.kosh.financetracker.security.DebugOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Development-only: merges a copy of the old Kosh app's data into Tinward. Kosh's database is plain
 * SQLite at version 7, which this app's own migrations were built from, so rows map one to one.
 *
 * It exists only in debuggable builds (the screen row and [available] check the flag), reads a copy placed in the app's
 * own private files folder, never touches the Kosh app, ignores rows already present (the unique source id), and runs
 * once. It should be deleted before a public release.
 */
@Singleton
class KoshImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: FinanceDatabase,
    private val transactions: TransactionDao,
    private val mappings: MerchantCategoryMappingDao,
    private val planning: PlanningRepository,
    private val budget: FixedBudgetStore,
    private val repository: TransactionRepository,
    private val debug: DebugOptions,
    private val accounts: dev.kosh.financetracker.data.repository.AccountRepository,
) {
    private val dir get() = File(context.filesDir, "kosh-import")
    private val dbFile get() = File(dir, "finance-tracker.db")
    private val marker get() = File(dir, "imported.done")

    fun available(): Boolean = debug.enabled && dbFile.exists() && !marker.exists()

    suspend fun import(): String = withContext(Dispatchers.IO) {
        if (!available()) return@withContext "Nothing to import."
        var inserted = 0; var alreadyThere = 0; var skipped = 0; var mapped = 0; var bills = 0; var linked = 0
        val src = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val rows = ArrayList<TransactionEntity>()
            // Messages Tinward's own scan already recorded: same bank, card, amount and direction within two minutes.
            val existingSms = transactions.getAllObservations().filter { it.source == TransactionSource.SMS }
                .groupBy { listOf(it.bank?.lowercase(), it.accountSuffix, it.amount.stripTrailingZeros().toPlainString(), it.direction) }
            src.rawQuery(
                "SELECT timestampEpochMillis, amount, direction, type, category, merchant, rawMerchant, accountSuffix, bank, paymentMethod, source, sourceMessageId, rawSourceText, confidence, notes, createdAtEpochMillis, balanceAfter FROM transactions ORDER BY id",
                null,
            ).use { c ->
                while (c.moveToNext()) {
                    val e = runCatching {
                        TransactionEntity(
                            timestampEpochMillis = c.getLong(0), amount = BigDecimal(c.getString(1)),
                            direction = TransactionDirection.valueOf(c.getString(2)), type = TransactionType.valueOf(c.getString(3)),
                            category = c.getString(4)?.let { TransactionCategory.valueOf(it) },
                            merchant = c.getString(5), rawMerchant = c.getString(6), accountId = null,
                            accountSuffix = c.getString(7), bank = c.getString(8),
                            paymentMethod = c.getString(9)?.let { PaymentMethod.valueOf(it) },
                            source = TransactionSource.valueOf(c.getString(10)), sourceMessageId = c.getString(11),
                            rawSourceText = c.getString(12), confidence = c.getDouble(13), notes = c.getString(14),
                            createdAtEpochMillis = c.getLong(15), balanceAfter = c.getString(16)?.let { BigDecimal(it) },
                        )
                    }.getOrNull()
                    if (e == null) { skipped++; continue }
                    if (e.source == TransactionSource.SMS) {
                        val k = listOf(e.bank?.lowercase(), e.accountSuffix, e.amount.stripTrailingZeros().toPlainString(), e.direction)
                        if (existingSms[k].orEmpty().any { kotlin.math.abs(it.timestampEpochMillis - e.timestampEpochMillis) <= 120_000 }) { alreadyThere++; continue }
                    }
                    rows += e.withReference()
                }
            }
            database.withTransaction {
                rows.forEach { if (transactions.insertIfNew(it) == -1L) alreadyThere++ else inserted++ }
                src.rawQuery("SELECT merchant, category, updatedAtEpochMillis FROM merchant_category_mappings", null).use { c ->
                    while (c.moveToNext()) {
                        runCatching { mappings.upsert(MerchantCategoryMappingEntity(c.getString(0), TransactionCategory.valueOf(c.getString(1)), c.getLong(2))) }.onSuccess { mapped++ }
                    }
                }
            }
        } finally { src.close() }

        // Income and fixed monthly expenses come from a small JSON file made from Kosh's settings.
        File(dir, "fixed_budget.json").takeIf { it.exists() }?.let { f ->
            val j = JSONObject(f.readText())
            j.optString("income").toBigDecimalOrNull()?.let { budget.setMonthlyIncome(it) }
            val existing = planning.observeRecurring().first().map { it.name.lowercase() }.toSet()
            val today = LocalDate.now(); val due = today.withDayOfMonth(today.lengthOfMonth())
            val arr = j.optJSONArray("expenses")
            for (i in 0 until (arr?.length() ?: 0)) {
                val o = arr!!.getJSONObject(i)
                val name = o.getString("label").trim().replace(Regex("\\s+"), " ")
                if (name.lowercase() in existing) continue
                planning.saveRecurring(RecurringItem(name = name, amount = BigDecimal(o.getString("amount")), frequency = Frequency.MONTHLY, anchor = due)); bills++
            }
        }
        linked = repository.reconcileSourceLinks()
        repository.reconcileTransfers(); repository.reconcileIncomeClassification(); repository.reconcileCategories()
        // Accounts found from SMS alone start at zero; statement rows carry real running balances, so set each
        // bank account's opening from the latest balance a statement reported (Tinward's normal rule), once rows are linked.
        var corrected = 0
        for (attempt in 0 until 20) {
            if (transactions.getAll().none { it.accountId == null && !it.accountSuffix.isNullOrBlank() }) break
            kotlinx.coroutines.delay(500)
        }
        val all = transactions.getAll().map { it.toDomain() }
        accounts.observeAccounts().first().filter { !it.archived && it.type == dev.kosh.financetracker.core.model.AccountType.BANK }.forEach { a ->
            val group = all.filter { it.accountId == a.id }
            if (group.isEmpty()) return@forEach
            val o = dev.kosh.financetracker.core.finance.AccountBootstrap.openingFor(group, group.map { it.id }.toSet(), false, java.time.Instant.now())
            if (o.fromReportedBalance) { accounts.update(a.copy(openingBalance = o.amount, openingDate = o.date)); corrected++ }
        }
        marker.writeText("done")
        "Imported $inserted transactions ($alreadyThere were already here, $skipped could not be read), $mapped remembered merchants, $bills monthly bills, $linked SMS rows matched to statement rows, $corrected account balances set from statements."
    }

    /** Kosh kept the bank's reference number inside the row id; Tinward matches SMS to statements by it. */
    private fun TransactionEntity.withReference(): TransactionEntity {
        val id = sourceMessageId ?: return this
        val ref = Regex(":ref:(\\w+)$").find(id)?.groupValues?.get(1)
            ?: if (id.startsWith("statement:")) id.substringAfterLast(':').takeIf { it.all(Char::isDigit) && it.length >= 8 } else null
        return if (ref != null && transactionRef == null) copy(transactionRef = ref) else this
    }
}
