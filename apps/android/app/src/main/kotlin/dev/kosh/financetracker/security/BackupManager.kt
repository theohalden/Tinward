package dev.kosh.financetracker.security

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kosh.financetracker.data.database.FinanceDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

sealed interface RestoreResult {
    /** Process is about to restart; the UI should show a short "restarting" note. */
    object Restarting : RestoreResult
    class Failed(val message: String) : RestoreResult
}

/**
 * Encrypted, user-initiated backup and restore. See [BackupCodec] for the file format.
 *
 * The payload is a zip of: the SQLCipher database exactly as stored (already encrypted under the
 * data key), the data key itself, and the encrypted budget values. The whole zip is then encrypted
 * under the backup passphrase, so a backup restores on a new phone with only that passphrase.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: Vault,
    private val database: Provider<FinanceDatabase>,
) {
    private val dbFile get() = context.getDatabasePath(FinanceDatabase.DB_NAME)
    private val budgetFile get() = File(context.filesDir, "datastore/fixed_budget.preferences_pb")

    fun create(passphrase: CharArray, out: OutputStream) {
        val key = vault.dataKey() ?: error("Unlock first")
        val db = database.get().openHelper.writableDatabase
        // Holding a write transaction keeps other writers out while the file is copied.
        db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        db.beginTransaction()
        try {
            BackupCodec.encryptWith(passphrase, out) { enc ->
                ZipOutputStream(enc).use { zip ->
                    zip.entry("manifest.json") {
                        write(JSONObject().put("format", 1).put("dbVersion", db.version).put("createdAt", System.currentTimeMillis()).toString().toByteArray())
                    }
                    zip.entry("dek") { write(key) }
                    zip.entry("finance.db") { dbFile.inputStream().use { it.copyTo(this) } }
                    if (budgetFile.exists()) zip.entry("fixed_budget.preferences_pb") { budgetFile.inputStream().use { it.copyTo(this) } }
                }
            }
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Replaces this phone's data with the backup's. [currentSecret] is re-checked first (restore
     * overwrites everything) and becomes the PIN protecting the restored data.
     */
    suspend fun restore(backupPassphrase: CharArray, currentSecret: CharArray, input: InputStream): RestoreResult {
        when (vault.verify(currentSecret)) {
            is UnlockResult.Success -> Unit
            is UnlockResult.Throttled -> return RestoreResult.Failed("Too many attempts. Try again later.")
            else -> return RestoreResult.Failed("Current PIN is incorrect.")
        }

        val work = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        val stagedDb = File(work, "finance.db")
        val stagedBudget = File(work, "fixed_budget.preferences_pb")
        var dek: ByteArray? = null
        var manifest: JSONObject? = null
        try {
            try {
                BackupCodec.decryptWith(backupPassphrase, input) { plain ->
                    val zip = ZipInputStream(plain)
                    var e: ZipEntry? = zip.nextEntry
                    while (e != null) {
                        when (e.name) {
                            "manifest.json" -> manifest = JSONObject(String(zip.readBytes()))
                            "dek" -> dek = zip.readBytes()
                            "finance.db" -> stagedDb.outputStream().use { zip.copyTo(it) }
                            "fixed_budget.preferences_pb" -> stagedBudget.outputStream().use { zip.copyTo(it) }
                        }
                        e = zip.nextEntry
                    }
                }
            } catch (e: BadBackupException) {
                return RestoreResult.Failed(e.message ?: "Could not read the backup.")
            }

            val key = dek
            val m = manifest
            if (key == null || m == null || !stagedDb.exists()) return RestoreResult.Failed("The backup is incomplete.")
            if (m.optInt("format") != 1) return RestoreResult.Failed("This backup was made by a newer version of Tinward.")
            if (m.optInt("dbVersion") > MAX_DB_VERSION) return RestoreResult.Failed("This backup is from a newer Tinward than this one. Update the app first.")

            DatabaseEncryptor.loadNative()
            val ok = runCatching {
                SQLiteDatabase.openDatabase(stagedDb.path, DatabaseEncryptor.passphrase(key), null, SQLiteDatabase.OPEN_READONLY, null, null).use { db ->
                    db.rawQuery("SELECT COUNT(*) FROM sqlite_master", emptyArray()).use { it.moveToFirst() }
                }
            }.isSuccess
            if (!ok) return RestoreResult.Failed("The backup could not be opened. Nothing was changed.")

            swapIn(stagedDb, stagedBudget.takeIf { it.exists() }, key, currentSecret)?.let { return RestoreResult.Failed(it) }
        } finally {
            dek?.fill(0)
            work.listFiles()?.forEach(DatabaseEncryptor::shred)
            work.delete()
        }
        scheduleRestart()
        return RestoreResult.Restarting
    }

    /** Returns an error message, or null on success. Rolls back to the previous data on any failure. */
    private fun swapIn(newDb: File, newBudget: File?, key: ByteArray, secret: CharArray): String? {
        val undo = File(context.filesDir, "restore_undo").apply { deleteRecursively(); mkdirs() }
        val files = listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm"))
        val record = vault.snapshotRecord()
        try {
            files.filter { it.exists() }.forEach { it.copyTo(File(undo, it.name), overwrite = true) }
            if (budgetFile.exists()) budgetFile.copyTo(File(undo, "budget"), overwrite = true)

            runCatching { database.get().close() }
            files.forEach { it.delete() }
            newDb.copyTo(dbFile, overwrite = true)
            if (newBudget != null) { budgetFile.parentFile?.mkdirs(); newBudget.copyTo(budgetFile, overwrite = true) } else budgetFile.delete()
            vault.rewrap(secret, key)
            undo.listFiles()?.forEach(DatabaseEncryptor::shred); undo.delete()
            return null
        } catch (e: Exception) {
            files.forEach { it.delete() }
            files.forEach { f -> File(undo, f.name).takeIf { it.exists() }?.copyTo(f, overwrite = true) }
            File(undo, "budget").takeIf { it.exists() }?.copyTo(budgetFile, overwrite = true)
            vault.restoreRecord(record)
            return "Restore failed and your previous data was kept: ${e.message}"
        }
    }

    private fun scheduleRestart() {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(
            Intent(context, RestartActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(RestartActivity.EXTRA_PID, android.os.Process.myPid())
                .putExtra(RestartActivity.EXTRA_LAUNCH, launch),
        )
        vault.lock()
    }

    private fun ZipOutputStream.entry(name: String, body: ZipOutputStream.() -> Unit) {
        putNextEntry(ZipEntry(name)); body(); closeEntry()
    }

    private companion object {
        /** Highest Room schema this build can read. Keep in step with FinanceDatabase. */
        const val MAX_DB_VERSION = 14
    }
}
