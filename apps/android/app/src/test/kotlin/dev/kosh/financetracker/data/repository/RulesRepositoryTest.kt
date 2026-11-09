package dev.kosh.financetracker.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.kosh.financetracker.core.finance.AutoRule
import dev.kosh.financetracker.core.model.PaymentMethod
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionSource
import dev.kosh.financetracker.core.model.TransactionType
import dev.kosh.financetracker.data.database.FinanceDatabase
import dev.kosh.financetracker.data.database.toDomain
import dev.kosh.financetracker.data.database.toEntity
import dev.kosh.financetracker.feature.plus.Entitlements
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RulesRepositoryTest {
    private lateinit var db: FinanceDatabase
    private lateinit var entitlements: Entitlements
    private lateinit var repo: RulesRepository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(ctx, FinanceDatabase::class.java).allowMainThreadQueries().build()
        entitlements = Entitlements(ctx)
        entitlements.setDebugPlus(false)   // debug builds default to Plus; the test controls the tier through Play results
        entitlements.onPlayResult(true, "tinward_plus")
        repo = RulesRepository(db, db.ruleDao(), db.transactionDao(), entitlements)
    }

    @After fun tearDown() { db.close() }

    private fun insert(id: Int, merchant: String, cat: TransactionCategory = TransactionCategory.UNCATEGORIZED, label: String? = null): Long = runBlocking {
        db.transactionDao().insertIfNew(Transaction(
            timestamp = Instant.EPOCH, amount = BigDecimal("100"), direction = TransactionDirection.DEBIT, type = TransactionType.EXPENSE, category = cat,
            merchant = merchant, rawMerchant = merchant, accountId = null, accountSuffix = null, bank = null, paymentMethod = PaymentMethod.UNKNOWN,
            source = TransactionSource.SMS, sourceMessageId = "m$id", rawSourceText = "x", confidence = 1.0, notes = null, createdAt = Instant.EPOCH, customLabel = label,
        ).toEntity())
    }
    private fun get(id: Long) = runBlocking { db.transactionDao().getByIdOnce(id)!!.toDomain() }
    private fun food() = AutoRule(name = "Food", merchantContains = "swiggy", setCategory = TransactionCategory.FOOD, setLabel = "Eating out")

    @Test fun `apply to existing changes matches, keeps the rest, and undo restores exactly`() = runBlocking {
        val a = insert(1, "Swiggy"); val b = insert(2, "Uber"); val c = insert(3, "Swiggy Instamart", cat = TransactionCategory.SHOPPING, label = "Mine")
        repo.save(food())
        val run = repo.applyToExisting()
        assertEquals(1, run.changed)                      // c keeps its own category and label
        assertEquals(TransactionCategory.FOOD, get(a).category); assertEquals("Eating out", get(a).customLabel)
        assertEquals(TransactionCategory.UNCATEGORIZED, get(b).category)
        assertEquals(TransactionCategory.SHOPPING, get(c).category); assertEquals("Mine", get(c).customLabel)
        assertEquals(1, repo.undo(run.batchId))
        assertEquals(TransactionCategory.UNCATEGORIZED, get(a).category); assertNull(get(a).customLabel)
        assertEquals(0, repo.undo(run.batchId))           // already undone: nothing more happens
    }

    @Test fun `undo does not overwrite something the user changed afterwards`() = runBlocking {
        val a = insert(1, "Swiggy"); repo.save(food())
        val run = repo.applyToExisting()
        db.transactionDao().updateCategory(a, TransactionCategory.SHOPPING)
        repo.undo(run.batchId)
        assertEquals(TransactionCategory.SHOPPING, get(a).category)
        assertNull(get(a).customLabel)
    }

    @Test fun `new transactions are labelled automatically with Plus and never without`() = runBlocking {
        repo.save(food())
        val a = insert(1, "Swiggy")
        assertEquals(1, repo.applyToNew(listOf(a)).changed)
        assertEquals(TransactionCategory.FOOD, get(a).category)

        val b = insert(2, "Swiggy")
        entitlements.onPlayResult(false, null)
        assertEquals(0, repo.applyToNew(listOf(b)).changed)
        assertEquals(0, repo.applyToExisting().changed)
        assertEquals(TransactionCategory.UNCATEGORIZED, get(b).category)
    }

    @Test fun `rules keep their order, can be moved and disabled`() = runBlocking {
        repo.save(food().copy(name = "one")); repo.save(food().copy(name = "two")); repo.save(food().copy(name = "three"))
        var names = repo.rulesFlow.first().map { it.name }; assertEquals(listOf("one", "two", "three"), names)
        repo.move(repo.rulesFlow.first()[2].id, -2)
        names = repo.rulesFlow.first().map { it.name }; assertEquals(listOf("three", "one", "two"), names)
        val a = insert(1, "Swiggy"); repo.rulesFlow.first().forEach { repo.setEnabled(it.id, false) }
        assertEquals(0, repo.applyToExisting().changed); assertEquals(TransactionCategory.UNCATEGORIZED, get(a).category)
    }

    @Test fun `the live preview counts matches without changing anything`() = runBlocking {
        val a = insert(1, "Swiggy"); insert(2, "Uber")
        assertEquals(1, repo.matchCount(food())); assertEquals(1, repo.previewChanges(food()).size)
        assertEquals(TransactionCategory.UNCATEGORIZED, get(a).category)
    }
}
