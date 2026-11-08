package dev.kosh.financetracker.feature.undo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kosh.financetracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UndoEvent(val message: String, val ids: List<Long>)

/** Lets any screen offer "Undo" for a delete; the app shell shows the snackbar. */
object UndoBus {
    val events = MutableSharedFlow<UndoEvent>(extraBufferCapacity = 4)
}

@HiltViewModel
class UndoViewModel @Inject constructor(private val repository: TransactionRepository) : ViewModel() {
    fun restore(ids: List<Long>) { viewModelScope.launch { repository.restore(ids) } }
}
