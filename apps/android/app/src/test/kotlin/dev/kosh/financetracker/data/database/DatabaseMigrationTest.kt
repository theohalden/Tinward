package dev.kosh.financetracker.data.database

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.math.BigDecimal

/**
 * Builds a database exactly as an older Tinward/Kosh release would have left it, upgrades it
 * through every migration to the current version, and lets Room verify the result matches the
 * current schema. This is the guard against losing or corrupting someone's data on update.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DatabaseMigrationTest {
    private lateinit var context: Context
    private val dbName = "migration-test.db"
    private val file: File get() = context.getDatabasePath(dbName)

    @Before fun setUp() { context = ApplicationProvider.getApplicationContext(); context.deleteDatabase(dbName) }
    @After fun tearDown() { context.deleteDatabase(dbName) }

    private val txColumnsV7 =
        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestampEpochMillis INTEGER NOT NULL, amount TEXT NOT NULL, direction TEXT NOT NULL, " +
            "type TEXT NOT NULL, category TEXT, merchant TEXT, rawMerchant TEXT, accountId INTEGER, accountSuffix TEXT, bank TEXT, paymentMethod TEXT, " +
            "source TEXT NOT NULL, sourceMessageId TEXT, rawSourceText TEXT, confidence REAL NOT NULL, notes TEXT, createdAtEpochMillis INTEGER NOT NULL, balanceAfter TEXT"

    /** The schema as shipped at version 7 or 8. Version 8 added transactionRef and the source-link table. */
    private fun createOldDatabase(version: Int) {
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL("CREATE TABLE transactions ($txColumnsV7${if (version >= 8) ", transactionRef TEXT" else ""})")
        db.execSQL("CREATE UNIQUE INDEX index_transactions_sourceMessageId ON transactions (sourceMessageId)")
        db.execSQL("CREATE TABLE merchant_category_mappings (merchant TEXT NOT NULL, category TEXT NOT NULL, updatedAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(merchant))")
        if (version >= 8) db.execSQL("CREATE TABLE transaction_source_links (observationId INTEGER NOT NULL, canonicalId INTEGER NOT NULL, PRIMARY KEY(observationId))")

        val cols = "timestampEpochMillis, amount, direction, type, category, merchant, rawMerchant, accountSuffix, bank, paymentMethod, source, sourceMessageId, rawSourceText, confidence, notes, createdAtEpochMillis, balanceAfter"
        db.execSQL("INSERT INTO transactions ($cols) VALUES (1730000000000, '1164.00', 'DEBIT', 'EXPENSE', 'TECHNOLOGY', 'HOSTINGER', 'HOSTINGER', '5590', 'HDFC', 'UPI', 'SMS', 'sms-1', 'Rs.1164.00 debited from A/c XX5590', 0.95, NULL, 1730000000001, '9000.50')")
        db.execSQL("INSERT INTO transactions ($cols) VALUES (1730100000000, '25000.00', 'CREDIT', 'INCOME', NULL, 'ACME PAYROLL', 'ACME PAYROLL', '9996', 'Kotak', 'NEFT', 'STATEMENT', 'stmt-1:0', NULL, 1.0, 'note', 1730100000001, NULL)")
        db.execSQL("INSERT INTO transactions ($cols) VALUES (1730200000000, '100.00', 'DEBIT', 'EXPENSE', 'FOOD', 'Cafe', 'Cafe', NULL, NULL, NULL, 'MANUAL', NULL, NULL, 1.0, NULL, 1730200000001, NULL)")
        db.execSQL("INSERT INTO merchant_category_mappings VALUES ('hostinger', 'TECHNOLOGY', 1730000000002)")
        db.version = version
        db.close()
    }

    private fun open(): FinanceDatabase = Room.databaseBuilder(context, FinanceDatabase::class.java, dbName)
        .allowMainThreadQueries()
        .addMigrations(
            FinanceDatabase.MIGRATION_7_8, FinanceDatabase.MIGRATION_8_9, FinanceDatabase.MIGRATION_9_10,
            FinanceDatabase.MIGRATION_10_11, FinanceDatabase.MIGRATION_11_12, FinanceDatabase.MIGRATION_12_13, FinanceDatabase.MIGRATION_13_14,
        ).build()

    private fun checkUpgraded(db: FinanceDatabase) = runBlocking {
        // Room validates the migrated schema against the current entities when it opens; getting here means it matched.
        val all = db.transactionDao().getAllObservations()
        assertEquals("every transaction survives", 3, all.size)

        val hostinger = all.first { it.sourceMessageId == "sms-1" }
        assertEquals(BigDecimal("1164.00"), hostinger.amount)
        assertEquals("HOSTINGER", hostinger.merchant)
        assertEquals("Rs.1164.00 debited from A/c XX5590", hostinger.rawSourceText)
        assertEquals(BigDecimal("9000.50"), hostinger.balanceAfter)

        // Columns added since: old rows get safe defaults.
        assertTrue("existing rows are rupee rows", all.all { it.currency == "INR" })
        assertTrue(all.all { it.deletedAtEpochMillis == null && it.refundOfId == null && it.splitsJson == null && it.customLabel == null && it.transferGroupId == null })

        assertEquals("the learned merchant category is kept", 1, db.merchantCategoryMappingDao().getAll().size)
        // The unique index still protects against re-importing the same message.
        assertEquals(-1L, db.transactionDao().insertIfNew(hostinger.copy(id = 0)))

        // New tables exist and work.
        assertTrue(db.accountDao().getAll().isEmpty())
        val accountId = db.accountDao().insert(
            AccountEntity(name = "HDFC", type = dev.kosh.financetracker.core.model.AccountType.BANK, institution = "HDFC", last4 = "5590", currency = "INR",
                openingBalance = BigDecimal("100"), openingDateEpochMillis = 0, archived = false, sortOrder = 0, linkedAccountId = null, creditLimit = null,
                statementDay = null, dueDay = null, createdAtEpochMillis = 0),
        )
        assertEquals(null, db.accountDao().getAll().single().apr)
        assertNotNull(accountId)
        db.planningDao().insertBudget(BudgetEntity(category = null, amount = BigDecimal("5000"), currency = "INR", rollover = false, startEpochDay = null))
        assertFalse(db.planningDao().budgets().single().rollover)
        assertEquals(0, db.planningDao().recurringCount())
        assertTrue(db.planningDao().activeGoals().isEmpty())
    }

    @Test fun `version 8 database upgrades to the current version with data intact`() {
        createOldDatabase(8)
        val db = open()
        try { checkUpgraded(db) } finally { db.close() }
    }

    @Test fun `version 7 database upgrades through every step`() {
        createOldDatabase(7)
        val db = open()
        try {
            checkUpgraded(db)
            // version 7 had no transactionRef: it is simply empty after upgrade
            runBlocking { assertTrue(db.transactionDao().getAllObservations().all { it.transactionRef == null }) }
        } finally { db.close() }
    }

    @Test fun `the rules tables arrive empty and usable after upgrade`() {
        createOldDatabase(8)
        val db = open()
        try {
            runBlocking {
                assertTrue(db.ruleDao().getRules().isEmpty())
                val id = db.ruleDao().insert(AutoRuleEntity(name = "Food", enabled = true, position = 0, merchantContains = "swiggy", amountMin = null, amountMax = null, direction = "ANY", accountId = null, setCategory = null, setLabel = "x", overwrite = false))
                assertEquals(id, db.ruleDao().getRules().single().id)
                assertEquals(3, db.transactionDao().getAllObservations().size)
            }
        } finally { db.close() }
    }

    @Test fun `an upgraded database reopens cleanly and keeps new data`() {
        createOldDatabase(8)
        open().also { db -> runBlocking { db.planningDao().insertBudget(BudgetEntity(category = null, amount = BigDecimal("1"), currency = "INR", rollover = true, startEpochDay = 20000)) }; db.close() }
        val again = open()
        try {
            runBlocking {
                assertEquals(3, again.transactionDao().getAllObservations().size)
                assertTrue(again.planningDao().budgets().single().rollover)
            }
        } finally { again.close() }
    }

    @Test fun `soft deleted rows stay out of the lists the app reads`() {
        createOldDatabase(8)
        val db = open()
        try {
            runBlocking {
                val first = db.transactionDao().getAllObservations().first()
                db.transactionDao().markDeleted(listOf(first.id), 123L)
                assertEquals(2, db.transactionDao().getAllObservations().size)
                assertEquals(2, db.transactionDao().getAll().size)
                assertNull(db.transactionDao().getByIdOnce(first.id)?.takeIf { it.deletedAtEpochMillis == null })
                db.transactionDao().markDeleted(listOf(first.id), null)
                assertEquals(3, db.transactionDao().getAllObservations().size)
            }
        } finally { db.close() }
    }
}
