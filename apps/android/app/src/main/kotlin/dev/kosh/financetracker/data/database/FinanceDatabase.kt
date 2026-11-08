package dev.kosh.financetracker.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransactionEntity::class, MerchantCategoryMappingEntity::class, TransactionSourceLink::class, AccountEntity::class,
        RecurringEntity::class, BudgetEntity::class, GoalEntity::class],
    version = 11,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun merchantCategoryMappingDao(): MerchantCategoryMappingDao
    abstract fun sourceLinkDao(): TransactionSourceLinkDao
    abstract fun accountDao(): AccountDao
    abstract fun planningDao(): PlanningDao

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

        /** Soft delete, refund links, split categories and custom labels. All additive and nullable. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN refundOfId INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN splitsJson TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN customLabel TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN deletedAtEpochMillis INTEGER")
            }
        }

        /** Bills and income schedules, budgets, and savings goals. New tables only. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS recurring_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                        "amount TEXT NOT NULL, currency TEXT NOT NULL, kind TEXT NOT NULL, frequency TEXT NOT NULL, anchorEpochDay INTEGER NOT NULL, " +
                        "accountId INTEGER, category TEXT, remindDaysBefore INTEGER NOT NULL, active INTEGER NOT NULL, paidThroughEpochDay INTEGER)",
                )
                db.execSQL("CREATE TABLE IF NOT EXISTS budgets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, category TEXT, amount TEXT NOT NULL, currency TEXT NOT NULL)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS savings_goals (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, target TEXT NOT NULL, " +
                        "saved TEXT NOT NULL, currency TEXT NOT NULL, targetDateEpochDay INTEGER, createdAtEpochMillis INTEGER NOT NULL, archived INTEGER NOT NULL)",
                )
            }
        }
    }
}
