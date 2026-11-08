package dev.kosh.financetracker.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.kosh.financetracker.core.model.PaymentMethod
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionSource
import dev.kosh.financetracker.core.model.TransactionType
import java.math.BigDecimal
import java.time.Instant

@Entity(
    tableName = "transactions",
    // Unique per source SMS so rescanning the inbox never double-imports the same
    // message; manual entries (sourceMessageId = null) never collide with each other.
    indices = [Index(value = ["sourceMessageId"], unique = true)],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpochMillis: Long,
    val amount: BigDecimal,
    val direction: TransactionDirection,
    val type: TransactionType,
    val category: TransactionCategory?,
    val merchant: String?,
    val rawMerchant: String?,
    val accountId: Long?,
    val accountSuffix: String?,
    val bank: String?,
    val paymentMethod: PaymentMethod?,
    val source: TransactionSource,
    val sourceMessageId: String?,
    val rawSourceText: String?,
    val confidence: Double,
    val notes: String?,
    val createdAtEpochMillis: Long,
    val balanceAfter: BigDecimal?,
    val transactionRef: String? = null,
    val currency: String = "INR",
    val transferGroupId: String? = null,
    val refundOfId: Long? = null,
    val splitsJson: String? = null,
    val customLabel: String? = null,
    /** Set when the user deletes it. Kept (not erased) so Undo works and a rescan cannot resurrect it. */
    val deletedAtEpochMillis: Long? = null,
)

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    timestamp = Instant.ofEpochMilli(timestampEpochMillis),
    amount = amount,
    direction = direction,
    type = type,
    category = category,
    merchant = merchant,
    rawMerchant = rawMerchant,
    accountId = accountId,
    accountSuffix = accountSuffix,
    bank = bank,
    paymentMethod = paymentMethod,
    source = source,
    sourceMessageId = sourceMessageId,
    rawSourceText = rawSourceText,
    confidence = confidence,
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    balanceAfter = balanceAfter,
    transactionRef = transactionRef,
    currency = currency,
    transferGroupId = transferGroupId,
    refundOfId = refundOfId,
    splits = dev.kosh.financetracker.core.model.SplitCodec.decode(splitsJson),
    customLabel = customLabel,
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    timestampEpochMillis = timestamp.toEpochMilli(),
    amount = amount,
    direction = direction,
    type = type,
    category = category,
    merchant = merchant,
    rawMerchant = rawMerchant,
    accountId = accountId,
    accountSuffix = accountSuffix,
    bank = bank,
    paymentMethod = paymentMethod,
    source = source,
    sourceMessageId = sourceMessageId,
    rawSourceText = rawSourceText,
    confidence = confidence,
    notes = notes,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    balanceAfter = balanceAfter,
    transactionRef = transactionRef,    currency = currency,
    transferGroupId = transferGroupId,    refundOfId = refundOfId,
    splitsJson = dev.kosh.financetracker.core.model.SplitCodec.encode(splits),
    customLabel = customLabel,
)
