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
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI State ──────────────────────────────────────────────────────────────────

data class DashboardUiState(
    val period: Period = Period.THIS_MONTH,
    val data: DashboardData = EmptyDashboardData,
    val pending: Int = 0,
    val lastRunAt: Long = 0L,
    val nextRunAt: Long? = null,
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

    // Pre-combine settings + schedule flows so the outer combine stays at ≤5 arguments
    // (kotlinx combine only has typed overloads up to 5 flows).
    private val _meta = combine(
        settingsRepo.lastRunAt,
        settingsRepo.confidenceThreshold,
        workScheduler.observeNextRunAt(),
    ) { lastRunAt, threshold, nextRunAt ->
        DashMeta(lastRunAt, threshold, nextRunAt)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        _period,
        transactionRepo.observeIncluded(),
        smsRepo.countPending(),
        _meta,
        _processNowQueued,
    ) { period, txns, pending, meta, queued ->
        val data = aggregate(txns, period, meta.threshold)
        DashboardUiState(
            period = period,
            data = data,
            pending = pending,
            lastRunAt = meta.lastRunAt,
            nextRunAt = meta.nextRunAt,
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
        _processNowQueued.update { true }
        // Process pending now AND reset the periodic timer so the next auto-run is a full
        // interval from now (reflected by nextRunAt).
        viewModelScope.launch {
            workScheduler.processNow()
        }
    }

    fun clearProcessNowQueued() {
        _processNowQueued.update { false }
    }

    /** Intermediate combine bucket — avoids a >5-arg combine. */
    private data class DashMeta(
        val lastRunAt: Long,
        val threshold: Double,
        val nextRunAt: Long?,
    )
}
