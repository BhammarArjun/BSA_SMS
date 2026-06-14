package com.local.smsllm.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.smsllm.data.TransactionEntity
import com.local.smsllm.repo.SettingsRepository
import com.local.smsllm.repo.SmsRepository
import com.local.smsllm.repo.TransactionRepository
import com.local.smsllm.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

// ── UI State ──────────────────────────────────────────────────────────────────

data class DashboardUiState(
    val period: Period = Period.THIS_MONTH,
    val data: DashboardData = EmptyDashboardData,
    val pending: Int = 0,
    val lastRunAt: Long = 0L,
    val processNowQueued: Boolean = false,
    val isLoading: Boolean = true,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val transactionRepo: TransactionRepository,
    private val smsRepo: SmsRepository,
    private val settingsRepo: SettingsRepository,
    private val workScheduler: WorkScheduler,
) : ViewModel() {

    /** Mutable period selection — drives the aggregation combine. */
    private val _period = MutableStateFlow(Period.THIS_MONTH)

    /** Mutable "process queued" confirmation flag — auto-cleared in screen. */
    private val _processNowQueued = MutableStateFlow(false)

    // Pre-combine settings flows so the outer combine stays at ≤5 arguments
    // (kotlinx combine only has typed overloads up to 5 flows).
    private val _settingsMeta = settingsRepo.lastRunAt.combine(settingsRepo.confidenceThreshold) { lastRunAt, threshold ->
        lastRunAt to threshold
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        _period,
        transactionRepo.observeIncluded(),
        smsRepo.countPending(),
        _settingsMeta,
        _processNowQueued,
    ) { period, txns, pending, (lastRunAt, threshold), queued ->
        val data = aggregate(txns, period, threshold)
        DashboardUiState(
            period = period,
            data = data,
            pending = pending,
            lastRunAt = lastRunAt,
            processNowQueued = queued,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(isLoading = true),
    )

    fun setPeriod(period: Period) {
        _period.update { period }
    }

    fun processNow() {
        workScheduler.runNow()
        _processNowQueued.update { true }
    }

    fun clearProcessNowQueued() {
        _processNowQueued.update { false }
    }
}
