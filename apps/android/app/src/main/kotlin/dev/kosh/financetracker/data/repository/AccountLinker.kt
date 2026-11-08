package dev.kosh.financetracker.data.repository

import dev.kosh.financetracker.data.database.TransactionDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps new SMS and statement rows attached to accounts. Started after unlock because it reads
 * the encrypted database; safe to start more than once.
 */
@Singleton
class AccountLinker @Inject constructor(
    private val repository: AccountRepository,
    private val transactions: TransactionDao,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            transactions.observeAll()
                .map { rows -> rows.count { it.accountId == null && !it.accountSuffix.isNullOrBlank() } }
                .distinctUntilChanged()
                .filter { it > 0 }
                .collect { runCatching { repository.linkUnassigned() } }
        }
    }
}
