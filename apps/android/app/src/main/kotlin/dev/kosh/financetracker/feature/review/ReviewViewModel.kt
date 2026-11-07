package dev.kosh.financetracker.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kosh.financetracker.core.model.Transaction
import dev.kosh.financetracker.core.model.TransactionCategory
import dev.kosh.financetracker.core.model.countsAsSpending
import dev.kosh.financetracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: TransactionRepository,
) : ViewModel() {

    /** Spending the classifier could not categorise confidently. */
    val queue: StateFlow<List<Transaction>> = repository.observeTransactions()
        .map { all -> all.filter { it.type.countsAsSpending() && (it.category == null || it.category == TransactionCategory.UNCATEGORIZED) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saving also teaches the merchant mapping, so same-merchant items leave the queue too. */
    fun confirm(id: Long, category: TransactionCategory) {
        viewModelScope.launch { repository.setCategory(id, category) }
    }
}
