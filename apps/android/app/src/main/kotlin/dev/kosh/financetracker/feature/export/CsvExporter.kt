package dev.kosh.financetracker.feature.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.ui.components.labelFor
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Writes the whole ledger as CSV to app cache and hands it to the Android share sheet. */
object CsvExporter {
    private val HEADERS = listOf("Date", "Amount", "Direction", "Type", "Category", "Merchant", "Bank", "Account", "Payment Method", "Source", "Notes")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun toCsv(transactions: List<Transaction>): String = buildString {
        appendLine(HEADERS.joinToString(","))
        transactions.forEach { t ->
            appendLine(
                listOf(
                    t.timestamp.atZone(ZoneId.systemDefault()).format(dateFormatter),
                    t.amount.toPlainString(),
                    t.direction.name,
                    t.type.name,
                    t.category?.let { labelFor(it) } ?: "Uncategorized",
                    t.merchant.orEmpty(),
                    t.bank.orEmpty(),
                    t.accountSuffix.orEmpty(),
                    t.paymentMethod?.name.orEmpty(),
                    t.source.name,
                    t.notes.orEmpty(),
                ).joinToString(",") { escape(it) },
            )
        }
    }

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' }) "\"" + field.replace("\"", "\"\"") + "\"" else field

    fun shareIntent(context: Context, transactions: List<Transaction>): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "tinward-export-${LocalDate.now()}.csv")
        file.writeText(toCsv(transactions))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Export transactions",
        )
    }
}
