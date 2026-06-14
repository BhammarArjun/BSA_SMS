package com.local.smsllm.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.smsllm.data.SmsMessageEntity
import com.local.smsllm.data.TransactionEntity
import com.local.smsllm.domain.ProcessingStatus
import com.local.smsllm.repo.SmsRepository
import com.local.smsllm.repo.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI State ──────────────────────────────────────────────────────────────────

data class DetailUiState(
    val txn: TransactionEntity? = null,
    val sms: SmsMessageEntity? = null,
    val loading: Boolean = true,
    val notFound: Boolean = false,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val txnRepo: TransactionRepository,
    private val smsRepo: SmsRepository,
) : ViewModel() {

    // Nav arg key matches Routes.DETAIL = "detail/{id}"
    private val transactionId: Long = savedStateHandle.get<String>("id")?.toLongOrNull() ?: -1L

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        if (transactionId != -1L) {
            observeTransaction()
        } else {
            _uiState.update { it.copy(loading = false, notFound = true) }
        }
    }

    private fun observeTransaction() {
        viewModelScope.launch {
            txnRepo.observeById(transactionId).collectLatest { txn ->
                if (txn == null) {
                    _uiState.update { it.copy(loading = false, notFound = true) }
                } else {
                    // Fetch SMS whenever txn changes and sms isn't already loaded for this smsId
                    val currentSms = _uiState.value.sms
                    val sms = if (currentSms?.id == txn.smsId) currentSms else smsRepo.getById(txn.smsId)
                    _uiState.update { it.copy(txn = txn, sms = sms, loading = false, notFound = false) }
                }
            }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    fun editCategory(categoryId: String?) {
        viewModelScope.launch {
            txnRepo.setCategory(transactionId, categoryId, System.currentTimeMillis())
        }
    }

    fun editFields(
        direction: String?,
        amount: Double?,
        dateText: String?,
        counterparty: String?,
    ) {
        viewModelScope.launch {
            txnRepo.editFields(
                id = transactionId,
                direction = direction,
                amount = amount,
                dateText = dateText,
                counterparty = counterparty,
                now = System.currentTimeMillis(),
            )
        }
    }

    fun markNotTransaction() {
        viewModelScope.launch {
            val smsId = _uiState.value.txn?.smsId ?: return@launch
            txnRepo.setIncluded(transactionId, false, System.currentTimeMillis())
            smsRepo.setStatus(smsId, ProcessingStatus.NON_TXN, System.currentTimeMillis(), null)
        }
    }

    fun reverify() {
        viewModelScope.launch {
            val smsId = _uiState.value.txn?.smsId ?: return@launch
            smsRepo.requeueForReverify(smsId)
            _events.emit("Queued for re-verification")
        }
    }
}
