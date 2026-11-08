package dev.kosh.financetracker.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class Appearance { SYSTEM, LIGHT, DARK }

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val appearanceKey = stringPreferencesKey("appearance")
    private val onboardedKey = booleanPreferencesKey("onboarded")
    private val currencyKey = stringPreferencesKey("base_currency")
    private val fixedImportedKey = booleanPreferencesKey("fixed_expenses_imported")

    val appearance: Flow<Appearance> = context.settingsDataStore.data.map {
        runCatching { Appearance.valueOf(it[appearanceKey] ?: "SYSTEM") }.getOrDefault(Appearance.SYSTEM)
    }

    /** null until the first read completes, so the UI never flashes onboarding. */
    val onboarded: Flow<Boolean> = context.settingsDataStore.data.map { it[onboardedKey] ?: false }

    /** The currency Home, Insights and Plan total in. INR until changed, because all earlier data is INR. */
    val baseCurrency: Flow<String> = context.settingsDataStore.data.map { it[currencyKey] ?: "INR" }

    val fixedExpensesImported: Flow<Boolean> = context.settingsDataStore.data.map { it[fixedImportedKey] ?: false }
    suspend fun markFixedExpensesImported() { context.settingsDataStore.edit { it[fixedImportedKey] = true } }

    suspend fun setBaseCurrency(code: String) { context.settingsDataStore.edit { it[currencyKey] = code } }


    suspend fun setAppearance(value: Appearance) {
        context.settingsDataStore.edit { it[appearanceKey] = value.name }
    }

    suspend fun setOnboarded() {
        context.settingsDataStore.edit { it[onboardedKey] = true }
    }
}
