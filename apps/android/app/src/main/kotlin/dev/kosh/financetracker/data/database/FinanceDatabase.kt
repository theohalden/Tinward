package dev.kosh.financetracker.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransactionEntity::class, MerchantCategoryMappingEntity::class, TransactionSourceLink::class, AccountEntity::class],
    version = 9,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun merchantCategoryMappingDao(): MerchantCategoryMappingDao
    abstract fun sourceLinkDao(): TransactionSourceLinkDao
    abstract fun accountDao(): AccountDao

    companion object {
        const val DB_NAME = "finance-tracker.db"
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN transactionRef TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS transaction_source_links (observationId INTEGER NOT NULL, canonicalId INTEGER NOT NULL, PRIMARY KEY(observationId))")
            }
        }

        /**
         * Adds real accounts and a per-transaction currency. Existing rows keep their data:
         * every transaction becomes INR (the only currency the app understood so far) and is
         * linked to an account by the post-unlock AccountBootstrap, never by guesswork here.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS accounts (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, " +
                        "institution TEXT, last4 TEXT, currency TEXT NOT NULL, openingBalance TEXT NOT NULL, " +
                        "openingDateEpochMillis INTEGER NOT NULL, archived INTEGER NOT NULL, sortOrder INTEGER NOT NULL, " +
                        "linkedAccountId INTEGER, creditLimit TEXT, statementDay INTEGER, dueDay INTEGER, " +
                        "createdAtEpochMillis INTEGER NOT NULL)",
                )
                db.execSQL("ALTER TABLE transactions ADD COLUMN currency TEXT NOT NULL DEFAULT 'INR'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN transferGroupId TEXT")
            }
        }
    }
}
