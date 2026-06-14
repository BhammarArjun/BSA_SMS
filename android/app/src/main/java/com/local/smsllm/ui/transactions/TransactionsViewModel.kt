package com.local.smsllm.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.smsllm.data.TransactionEntity
import com.local.smsllm.repo.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

// ── UI State ──────────────────────────────────────────────────────────────────

data class TransactionsUiState(
    val sections: List<DateSection> = emptyList(),
    val totalCount: Int = 0,
    val filteredCount: Int = 0,
    val filter: TransactionsFilter = TransactionsFilter(),
    val isLoading: Boolean = true,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repo: TransactionRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(TransactionsFilter())

    val uiState: StateFlow<TransactionsUiState> = combine(
        repo.observeAll(),
        _filter,
    ) { transactions, filter ->
        val nowMs = System.currentTimeMillis()
        val sections = filterAndGroup(transactions, filter, nowMs)
        val filteredCount = sections.sumOf { it.items.size }
        TransactionsUiState(
            sections = sections,
            totalCount = transactions.size,
            filteredCount = filteredCount,
            filter = filter,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionsUiState(isLoading = true),
    )

    fun setQuery(query: String) = _filter.update { it.copy(query = query) }

    fun setCategoryFilter(categoryIds: Set<String>) =
        _filter.update { it.copy(categoryIds = categoryIds) }

    fun setDirectionFilter(direction: String?) =
        _filter.update { it.copy(direction = direction) }

    fun setMinConfidence(minConfidence: Double) =
        _filter.update { it.copy(minConfidence = minConfidence) }

    fun clearFilters() = _filter.update { TransactionsFilter() }
}
