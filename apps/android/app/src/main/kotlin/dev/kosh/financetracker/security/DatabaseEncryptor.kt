package dev.kosh.financetracker.security

import android.content.Context
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.RandomAccessFile

/**
 * One-time conversion of the existing plaintext Room database to a SQLCipher database.
 *
 * Crash-safe by construction: the plaintext file is only removed after the encrypted copy has
 * been written, opened with the new key, and every table's row count matched. Re-running is a
 * no-op once converted, and an interrupted run is finished by the next unlock.
 */
object DatabaseEncryptor {
    fun passphrase(dek: ByteArray): ByteArray = "x'${Crypto.toHex(dek)}'".toByteArray()

    fun loadNative() { System.loadLibrary("sqlcipher") }

    fun isPlaintext(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        val head = ByteArray(15)
        file.inputStream().use { it.read(head) }
        return String(head, Charsets.US_ASCII) == "SQLite format 3"
    }

    fun ensureEncrypted(context: Context, dek: ByteArray, dbName: String = dev.kosh.financetracker.data.database.FinanceDatabase.DB_NAME) {
        loadNative()
        val db = context.getDatabasePath(dbName)
        val enc = File(db.path + ".enc")

        // Finish an interrupted swap: plaintext already shredded, encrypted copy waiting.
        if (!db.exists() && enc.exists() && opensWith(enc, dek)) {
            check(enc.renameTo(db)) { "Could not move encrypted database into place" }
            return
        }
        if (!isPlaintext(db)) return

        listOf(enc, File(enc.path + "-wal"), File(enc.path + "-shm"), File(enc.path + "-journal")).forEach { it.delete() }
        val plain = SQLiteDatabase.openDatabase(db.path, "", null, SQLiteDatabase.OPEN_READONLY, null)
        val counts: Map<String, Long>
        try {
            copyInto(plain, enc, dek)
            counts = tableCounts(plain)
        } finally {
            plain.close()
        }

        val encrypted = SQLiteDatabase.openDatabase(enc.path, passphrase(dek), null, SQLiteDatabase.OPEN_READONLY, null, null)
        try {
            val after = tableCounts(encrypted)
            check(after == counts) { "Encrypted copy does not match the original; leaving plaintext untouched" }
        } finally {
            encrypted.close()
        }

        listOf(db, File(db.path + "-wal"), File(db.path + "-shm"), File(db.path + "-journal")).forEach(::shred)
        check(enc.renameTo(db)) { "Could not move encrypted database into place" }
    }

    /**
     * Copies schema and rows into a new encrypted database using two ordinary connections.
     * (SQLCipher's ATTACH + sqlcipher_export is the textbook route, but ATTACH is per-connection and
     * the connection pool does not guarantee the follow-up statements run on the same one.)
     */
    private fun copyInto(src: SQLiteDatabase, dest: File, dek: ByteArray) {
        val out = SQLiteDatabase.openOrCreateDatabase(dest.path, passphrase(dek), null, null, null)
        try {
            out.disableWriteAheadLogging()
            val schema = ArrayList<Triple<String, String, String>>()
            src.rawQuery(
                "SELECT type, name, sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
                emptyArray(),
            ).use { while (it.moveToNext()) schema += Triple(it.getString(0), it.getString(1), it.getString(2)) }

            out.beginTransaction()
            try {
                // Tables first so indexes, triggers and views can reference them.
                schema.filter { it.first == "table" }.forEach { out.execSQL(it.third) }
                for ((_, name, _) in schema.filter { it.first == "table" }) {
                    src.rawQuery("SELECT * FROM \"$name\"", emptyArray()).use { c ->
                        val marks = List(c.columnCount) { "?" }.joinToString(",")
                        val insert = "INSERT INTO \"$name\" VALUES ($marks)"
                        while (c.moveToNext()) {
                            val args = Array<Any?>(c.columnCount) { i ->
                                when (c.getType(i)) {
                                    android.database.Cursor.FIELD_TYPE_NULL -> null
                                    android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                                    android.database.Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                                    android.database.Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
                                    else -> c.getString(i)
                                }
                            }
                            out.execSQL(insert, args)
                        }
                    }
                }
                schema.filter { it.first != "table" }.forEach { out.execSQL(it.third) }
                out.setTransactionSuccessful()
            } finally {
                out.endTransaction()
            }
            out.version = src.version
        } finally {
            out.close()
        }
    }

    private fun opensWith(file: File, dek: ByteArray): Boolean = runCatching {
        SQLiteDatabase.openDatabase(file.path, passphrase(dek), null, SQLiteDatabase.OPEN_READONLY, null, null).use { tableCounts(it); true }
    }.getOrDefault(false)

    private fun tableCounts(db: SQLiteDatabase): Map<String, Long> {
        val names = ArrayList<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
            emptyArray(),
        ).use { while (it.moveToNext()) names += it.getString(0) }
        return names.associateWith { n ->
            db.rawQuery("SELECT COUNT(*) FROM \"$n\"", emptyArray()).use { c -> c.moveToFirst(); c.getLong(0) }
        }
    }

    /** Best-effort overwrite before delete. Flash storage can keep old blocks, which is why the file is also encrypted going forward. */
    fun shred(file: File) {
        if (!file.exists()) return
        runCatching {
            RandomAccessFile(file, "rws").use { raf ->
                val zeros = ByteArray(8192)
                var left = raf.length()
                raf.seek(0)
                while (left > 0) { val n = minOf(left, zeros.size.toLong()).toInt(); raf.write(zeros, 0, n); left -= n }
            }
        }
        file.delete()
    }
}
