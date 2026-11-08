package dev.kosh.financetracker.data.database

import androidx.room.TypeConverter
import dev.kosh.financetracker.core.model.PaymentMethod
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.TransactionDirection
import dev.kosh.financetracker.core.model.TransactionSource
import dev.kosh.financetracker.core.model.TransactionType
import dev.kosh.financetracker.core.finance.Frequency
import dev.kosh.financetracker.core.finance.RecurringKind
import dev.kosh.financetracker.core.model.AccountType
import java.math.BigDecimal

class Converters {
    @TypeConverter
    fun fromBigDecimal(value: BigDecimal): String = value.toPlainString()

    @TypeConverter
    fun toBigDecimal(value: String): BigDecimal = BigDecimal(value)

    @TypeConverter
    fun fromDirection(value: TransactionDirection): String = value.name

    @TypeConverter
    fun toDirection(value: String): TransactionDirection = TransactionDirection.valueOf(value)

    @TypeConverter
    fun fromType(value: TransactionType): String = value.name

    @TypeConverter
    fun toType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromCategory(value: TransactionCategory?): String? = value?.name

    @TypeConverter
    fun toCategory(value: String?): TransactionCategory? = value?.let { TransactionCategory.valueOf(it) }

    @TypeConverter
    fun fromPaymentMethod(value: PaymentMethod?): String? = value?.name

    @TypeConverter
    fun toPaymentMethod(value: String?): PaymentMethod? = value?.let { PaymentMethod.valueOf(it) }

    @TypeConverter
    fun fromSource(value: TransactionSource): String = value.name

    @TypeConverter
    fun toSource(value: String): TransactionSource = TransactionSource.valueOf(value)

    @TypeConverter
    fun fromAccountType(value: AccountType): String = value.name

    @TypeConverter
    fun toAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter
    fun fromNullableBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun toNullableBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }

    @TypeConverter
    fun fromFrequency(value: Frequency): String = value.name

    @TypeConverter
    fun toFrequency(value: String): Frequency = Frequency.valueOf(value)

    @TypeConverter
    fun fromRecurringKind(value: RecurringKind): String = value.name

    @TypeConverter
    fun toRecurringKind(value: String): RecurringKind = RecurringKind.valueOf(value)
}
