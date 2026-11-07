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
class FixedBudgetStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: dev.kosh.financetracker.security.Vault,
) {
    private val incomeKey = stringPreferencesKey("monthly_income")
    private val expensesKey = stringPreferencesKey("fixed_expenses_json")

    val monthlyIncome: Flow<BigDecimal?> = context.fixedBudgetDataStore.data.map { p ->
        decrypt(p[incomeKey])?.toBigDecimalOrNull()
    }

    val expenses: Flow<List<FixedExpenseItem>> = context.fixedBudgetDataStore.data.map { p ->
        parse(decrypt(p[expensesKey]))
    }

    suspend fun setMonthlyIncome(amount: BigDecimal?) {
        context.fixedBudgetDataStore.edit { p ->
            if (amount == null) p.remove(incomeKey) else p[incomeKey] = encrypt(amount.toPlainString())
        }
    }

    suspend fun addExpense(label: String, amount: BigDecimal) {
        context.fixedBudgetDataStore.edit { p ->
            val list = parse(decrypt(p[expensesKey])) + FixedExpenseItem(UUID.randomUUID().toString(), label.trim(), amount)
            p[expensesKey] = encrypt(serialize(list))
        }
    }

    suspend fun removeExpense(id: String) {
        context.fixedBudgetDataStore.edit { p ->
            p[expensesKey] = encrypt(serialize(parse(decrypt(p[expensesKey])).filterNot { it.id == id }))
        }
    }

    /** Re-saves any value written before app lock existed, now encrypted. Safe to call repeatedly. */
    suspend fun encryptLegacyValues() {
        context.fixedBudgetDataStore.edit { p ->
            p[incomeKey]?.takeIf { !it.startsWith(PREFIX) }?.let { p[incomeKey] = encrypt(it) }
            p[expensesKey]?.takeIf { !it.startsWith(PREFIX) }?.let { p[expensesKey] = encrypt(it) }
        }
    }

    private fun encrypt(plain: String): String {
        val key = vault.dataKey() ?: error("Budget data written while locked")
        return PREFIX + java.util.Base64.getEncoder().encodeToString(
            dev.kosh.financetracker.security.Crypto.seal(key, plain.toByteArray(), AAD),
        )
    }

    /** Locked or unreadable values read as empty rather than throwing. Pre-lock plaintext is still readable. */
    private fun decrypt(stored: String?): String? {
        if (stored == null) return null
        if (!stored.startsWith(PREFIX)) return stored
        val key = vault.dataKey() ?: return null
        return runCatching {
            String(dev.kosh.financetracker.security.Crypto.open(key, java.util.Base64.getDecoder().decode(stored.removePrefix(PREFIX)), AAD))
        }.getOrNull()
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

    private companion object {
        const val PREFIX = "enc1:"
        val AAD = "tinward-budget-v1".toByteArray()
    }
}
