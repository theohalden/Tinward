package dev.kosh.financetracker.core.model

import java.math.BigDecimal

/** One slice of a transaction that spans several categories (e.g. a supermarket bill that was groceries and home). */
data class CategorySplit(val category: TransactionCategory, val amount: BigDecimal)

/** Plain-text storage for splits, so the model needs no JSON library and stays JVM-testable. */
object SplitCodec {
    fun encode(splits: List<CategorySplit>): String? =
        splits.takeIf { it.isNotEmpty() }?.joinToString(";") { "${it.category.name}:${it.amount.toPlainString()}" }

    fun decode(text: String?): List<CategorySplit> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split(';').mapNotNull { part ->
            val (c, a) = part.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            val category = runCatching { TransactionCategory.valueOf(c) }.getOrNull() ?: return@mapNotNull null
            val amount = a.toBigDecimalOrNull() ?: return@mapNotNull null
            CategorySplit(category, amount)
        }
    }

    /**
     * A refund of part of a split purchase refunds each part in proportion. Rounded to cents, with
     * any rounding gap given to the largest part so the pieces always add up to [newTotal].
     * Returns empty when fewer than two non-zero parts remain (the caller falls back to one category).
     */
    fun scale(splits: List<CategorySplit>, oldTotal: BigDecimal, newTotal: BigDecimal): List<CategorySplit> {
        if (splits.size < 2 || oldTotal.signum() <= 0) return emptyList()
        val scaled = splits.map { it.copy(amount = it.amount.multiply(newTotal).divide(oldTotal, 2, java.math.RoundingMode.HALF_UP)) }.toMutableList()
        val gap = newTotal - scaled.fold(BigDecimal.ZERO) { a, s -> a + s.amount }
        val biggest = scaled.indices.maxByOrNull { scaled[it].amount }!!
        scaled[biggest] = scaled[biggest].copy(amount = scaled[biggest].amount + gap)
        return scaled.filter { it.amount.signum() > 0 }.takeIf { it.size >= 2 } ?: emptyList()
    }

    /** Splits must add up to the whole, to the paisa, and use at least two slices. */
    fun problem(total: BigDecimal, splits: List<CategorySplit>): String? = when {
        splits.size < 2 -> "A split needs at least two parts."
        splits.any { it.amount.signum() <= 0 } -> "Every part must be above zero."
        splits.fold(BigDecimal.ZERO) { a, s -> a + s.amount }.compareTo(total) != 0 -> "The parts must add up to the full amount."
        else -> null
    }
}
