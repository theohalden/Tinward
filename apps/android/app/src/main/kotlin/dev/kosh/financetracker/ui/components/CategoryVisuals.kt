package dev.kosh.financetracker.ui.components

import androidx.compose.ui.graphics.Color
import dev.kosh.financetracker.core.model.TransactionCategory
import androidx.compose.runtime.Composable
import dev.kosh.financetracker.ui.theme.Tinward

/** One stable identity color per category, used everywhere a category shows up
 * (Home, Activity, Review, Spending) so it reads like a recognizable icon color
 * rather than a random per-list-position color. */
@Composable
fun colorFor(category: TransactionCategory?): Color {
    val palette = Tinward.colors.category
    val index = when (category) {
        TransactionCategory.FOOD -> 0
        TransactionCategory.GROCERIES -> 2
        TransactionCategory.TRAVEL -> 1
        TransactionCategory.FUEL -> 5
        TransactionCategory.SHOPPING -> 3
        TransactionCategory.BILLS -> 0
        TransactionCategory.SUBSCRIPTIONS -> 4
        TransactionCategory.RENT -> 5
        TransactionCategory.FAMILY -> 3
        TransactionCategory.HEALTH -> 2
        TransactionCategory.ENTERTAINMENT -> 3
        TransactionCategory.EDUCATION -> 1
        TransactionCategory.PERSONAL_CARE -> 4
        TransactionCategory.HOME -> 5
        TransactionCategory.TECHNOLOGY -> 7
        TransactionCategory.BUSINESS -> 6
        TransactionCategory.FEES -> 4
        TransactionCategory.OTHER -> 6
        TransactionCategory.UNCATEGORIZED, null -> 6
    }
    return palette[index]
}

fun labelFor(category: TransactionCategory): String = when (category) {
    TransactionCategory.FOOD -> "Food & Dining"
    TransactionCategory.GROCERIES -> "Groceries"
    TransactionCategory.TRAVEL -> "Transport"
    TransactionCategory.FUEL -> "Fuel"
    TransactionCategory.SHOPPING -> "Shopping"
    TransactionCategory.BILLS -> "Bills & Utilities"
    TransactionCategory.SUBSCRIPTIONS -> "Subscriptions"
    TransactionCategory.RENT -> "Rent"
    TransactionCategory.FAMILY -> "Family"
    TransactionCategory.HEALTH -> "Health"
    TransactionCategory.ENTERTAINMENT -> "Entertainment"
    TransactionCategory.EDUCATION -> "Education"
    TransactionCategory.PERSONAL_CARE -> "Personal Care"
    TransactionCategory.HOME -> "Home"
    TransactionCategory.TECHNOLOGY -> "Technology"
    TransactionCategory.BUSINESS -> "Business"
    TransactionCategory.FEES -> "Fees"
    TransactionCategory.OTHER -> "Other"
    TransactionCategory.UNCATEGORIZED -> "Uncategorized"
}

/** The short list offered in the Review picker — the full enum has 19 values, too
 * many to review one at a time; these cover the overwhelming majority of spending. */
val REVIEW_PICKER_CATEGORIES = listOf(
    TransactionCategory.FOOD,
    TransactionCategory.BILLS,
    TransactionCategory.SHOPPING,
    TransactionCategory.TRAVEL,
    TransactionCategory.OTHER,
)
