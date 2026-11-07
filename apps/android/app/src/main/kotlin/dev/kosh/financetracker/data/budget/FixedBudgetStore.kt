package dev.kosh.financetracker.data.budget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class FixedExpenseItem(val id: String, val label: String, val amount: BigDecimal)

private val Context.fixedBudgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "fixed_budget")

/**
 * The user's declared monthly income and recurring fixed costs. Stored on-device in a
 * Preferences DataStore (never uploaded); deliberately separate from transactions so
 * it never alters the ledger.
 */
@Singleton
class FixedBudgetStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val incomeKey = stringPreferencesKey("monthly_income")
    private val expensesKey = stringPreferencesKey("fixed_expenses_json")

    val monthlyIncome: Flow<BigDecimal?> = context.fixedBudgetDataStore.data.map { p ->
        p[incomeKey]?.toBigDecimalOrNull()
    }

    val expenses: Flow<List<FixedExpenseItem>> = context.fixedBudgetDataStore.data.map { p ->
        parse(p[expensesKey])
    }

    suspend fun setMonthlyIncome(amount: BigDecimal?) {
        context.fixedBudgetDataStore.edit { p ->
            if (amount == null) p.remove(incomeKey) else p[incomeKey] = amount.toPlainString()
        }
    }

    suspend fun addExpense(label: String, amount: BigDecimal) {
        context.fixedBudgetDataStore.edit { p ->
            val list = parse(p[expensesKey]) + FixedExpenseItem(UUID.randomUUID().toString(), label.trim(), amount)
            p[expensesKey] = serialize(list)
        }
    }

    suspend fun removeExpense(id: String) {
        context.fixedBudgetDataStore.edit { p ->
            p[expensesKey] = serialize(parse(p[expensesKey]).filterNot { it.id == id })
        }
    }

    private fun parse(json: String?): List<FixedExpenseItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                FixedExpenseItem(o.getString("id"), o.getString("label"), BigDecimal(o.getString("amount")))
            }
        }.getOrDefault(emptyList())
    }

    private fun serialize(items: List<FixedExpenseItem>): String = JSONArray().also { arr ->
        items.forEach { arr.put(JSONObject().put("id", it.id).put("label", it.label).put("amount", it.amount.toPlainString())) }
    }.toString()
}
