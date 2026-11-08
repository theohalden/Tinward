package dev.kosh.financetracker.feature.importcsv

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kosh.financetracker.core.csv.ColumnGuesser
import dev.kosh.financetracker.core.csv.ColumnMapping
import dev.kosh.financetracker.core.csv.CsvImportPlanner
import dev.kosh.financetracker.core.csv.CsvReader
import dev.kosh.financetracker.core.csv.CsvTable
import dev.kosh.financetracker.core.csv.DateDetection
import dev.kosh.financetracker.core.csv.DateFormat
import dev.kosh.financetracker.core.csv.DateParsing
import dev.kosh.financetracker.core.csv.ImportPlan
import dev.kosh.financetracker.core.csv.ImportSettings
import dev.kosh.financetracker.core.model.Account
import dev.kosh.financetracker.core.model.AccountType
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.parser.MoneyNumbers
import dev.kosh.financetracker.data.repository.AccountRepository
import dev.kosh.financetracker.data.repository.CsvImportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

private const val MAX_BYTES = 5 * 1024 * 1024

data class ImportCsvUi(
    val fileName: String? = null,
    val table: CsvTable? = null,
    val headerRow: Int = 0,
    val mapping: ColumnMapping = ColumnMapping(),
    val dateFormat: DateFormat? = null,
    val dateDetection: DateDetection? = null,
    val decimal: MoneyNumbers.Decimal = MoneyNumbers.Decimal.DOT,
    val invertSign: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val accountId: Long? = null,
    val plan: ImportPlan? = null,
    val error: String? = null,
    val working: Boolean = false,
    val result: String? = null,
) {
    val header: List<String> get() = table?.rows?.getOrNull(headerRow).orEmpty()
    val account: Account? get() = accounts.firstOrNull { it.id == accountId }
    val canImport: Boolean get() = account != null && (plan?.newRows?.isNotEmpty() == true) && !working && result == null
}

@HiltViewModel
class ImportCsvViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountsRepo: AccountRepository,
    private val importer: CsvImportRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(ImportCsvUi())
    val ui: StateFlow<ImportCsvUi> = _ui.asStateFlow()
    private var existing: List<Transaction> = emptyList()
    private val dayFirst = Locale.getDefault().country !in setOf("US", "PH", "CA")

    init {
        viewModelScope.launch {
            val list = accountsRepo.observeAccounts().first().filter { !it.archived && it.type != AccountType.DEBIT_CARD }
            _ui.update { it.copy(accounts = list, accountId = list.singleOrNull()?.id) }
            refreshExisting()
        }
    }

    fun load(uri: Uri, name: String?) {
        _ui.update { it.copy(working = true, error = null, result = null) }
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)!!.use { s ->
                        val bytes = s.readNBytesCompat(MAX_BYTES + 1)
                        if (bytes.size > MAX_BYTES) null else String(bytes, Charsets.UTF_8)
                    }
                }.getOrNull()
            }
            if (text == null) { _ui.update { it.copy(working = false, error = "That file could not be read, or it is larger than 5 MB.") }; return@launch }
            val table = CsvReader.parse(text)
            if (table.rows.size < 2) { _ui.update { it.copy(working = false, error = "This does not look like a table of transactions.") }; return@launch }
            val header = ColumnGuesser.findHeaderRow(table.rows)
            val mapping = ColumnGuesser.guess(table.rows[header])
            val body = table.rows.drop(header + 1).take(300)
            val dates = mapping.date?.let { c -> body.map { it.getOrNull(c).orEmpty() } }.orEmpty()
            val detection = DateParsing.detect(dates, dayFirst)
            val amountCells = listOfNotNull(mapping.amount, mapping.debit, mapping.credit, mapping.balance).flatMap { c -> body.map { it.getOrNull(c).orEmpty() } }
            _ui.update {
                it.copy(
                    fileName = name, table = table, headerRow = header, mapping = mapping, dateDetection = detection, dateFormat = detection?.best,
                    decimal = MoneyNumbers.detectDecimal(amountCells), invertSign = false, working = false,
                    error = if (detection == null && mapping.date != null) "No date format matches the date column. Pick the date column again." else null,
                )
            }
            replan()
        }
    }

    fun selectAccount(id: Long) { _ui.update { it.copy(accountId = id) }; viewModelScope.launch { refreshExisting(); replan() } }
    fun setMapping(block: ColumnMapping.() -> ColumnMapping) { _ui.update { it.copy(mapping = it.mapping.block()) }; redetectDates(); replan() }
    fun setDateFormat(f: DateFormat) { _ui.update { it.copy(dateFormat = f) }; replan() }
    fun setDecimal(d: MoneyNumbers.Decimal) { _ui.update { it.copy(decimal = d) }; replan() }
    fun setInvert(on: Boolean) { _ui.update { it.copy(invertSign = on) }; replan() }

    private fun redetectDates() {
        val s = _ui.value; val t = s.table ?: return; val c = s.mapping.date ?: return
        val det = DateParsing.detect(t.rows.drop(s.headerRow + 1).take(300).map { it.getOrNull(c).orEmpty() }, dayFirst)
        _ui.update { it.copy(dateDetection = det, dateFormat = det?.best ?: it.dateFormat) }
    }

    private suspend fun refreshExisting() {
        val all = accountsRepo.observeAccounts().first()
        existing = _ui.value.account?.let { importer.existingFor(it, all) }.orEmpty()
    }

    private fun replan() {
        val s = _ui.value; val t = s.table; val f = s.dateFormat
        if (t == null || f == null || !s.mapping.usable) { _ui.update { it.copy(plan = null) }; return }
        val plan = CsvImportPlanner.plan(t, s.headerRow, ImportSettings(s.mapping, f, s.decimal, s.invertSign), existing)
        _ui.update { it.copy(plan = plan) }
    }

    fun import() {
        val s = _ui.value; val account = s.account ?: return; val plan = s.plan ?: return
        _ui.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            val r = runCatching { importer.import(account, plan.rows) }
            _ui.update {
                r.fold(
                    { res -> it.copy(working = false, result = "Added ${res.added} transaction${if (res.added == 1) "" else "s"} to ${account.name}." + if (res.alreadyPresent > 0) " ${res.alreadyPresent} were already there." else "") },
                    { e -> it.copy(working = false, error = "The import did not finish: ${e.message}") },
                )
            }
        }
    }

    private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192); var left = n
        while (left > 0) { val r = read(buf, 0, minOf(buf.size, left)); if (r < 0) break; out.write(buf, 0, r); left -= r }
        return out.toByteArray()
    }
}
