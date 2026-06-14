package com.local.smsllm.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.local.smsllm.domain.ModelRegistry
import com.local.smsllm.llm.BackendChoice
import com.local.smsllm.llm.DownloadProgress
import com.local.smsllm.llm.ModelManager
import com.local.smsllm.repo.SettingsRepository
import com.local.smsllm.repo.TransactionRepository
import com.local.smsllm.work.ImportWorker
import com.local.smsllm.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI State ──────────────────────────────────────────────────────────────────

data class SettingsUiState(
    // Settings values
    val processingIntervalMinutes: Int = 30,
    val requiresCharging: Boolean = false,
    val requiresBatteryNotLow: Boolean = true,
    val backendPreference: BackendChoice = BackendChoice.AUTO,
    val maxMessagesPerRun: Int = 50,
    val confidenceThreshold: Double = 0.0,
    // Model state
    val modelReady: Boolean = false,
    val downloadProgress: Float? = null,        // null = idle; 0..1 = in-progress; after Done reset to null
    val downloadError: String? = null,
    // Import state (driven by the background ImportWorker's WorkInfo)
    val importRunning: Boolean = false,
    val importResult: String? = null,           // shown after completion
    // Export state
    val exportStatus: ExportStatus = ExportStatus.Idle,
    // Loading guard
    val isLoading: Boolean = true,
)

sealed interface ExportStatus {
    data object Idle : ExportStatus
    data object Building : ExportStatus
    data object Exported : ExportStatus
    data class Failed(val reason: String) : ExportStatus
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val workScheduler: WorkScheduler,
    private val modelManager: ModelManager,
    private val transactionRepo: TransactionRepository,
) : ViewModel() {

    private val _transient = MutableStateFlow(TransientState())

    // Combine all persisted settings Flows + model-ready + transient into one UiState.
    // Split into two combines to stay within the 6-arg typed overload limit.
    private val _settingsCore = combine(
        settingsRepo.processingIntervalMinutes,
        settingsRepo.requiresCharging,
        settingsRepo.requiresBatteryNotLow,
        settingsRepo.backendPreference,
        settingsRepo.maxMessagesPerRun,
    ) { interval, charging, batteryNotLow, backend, maxMsgs ->
        SettingsCore(interval, charging, batteryNotLow, backend, maxMsgs)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        _settingsCore,
        settingsRepo.confidenceThreshold,
        _transient,
    ) { core, threshold, tr ->
        SettingsUiState(
            processingIntervalMinutes = core.interval,
            requiresCharging = core.charging,
            requiresBatteryNotLow = core.batteryNotLow,
            backendPreference = core.backend,
            maxMessagesPerRun = core.maxMsgs,
            confidenceThreshold = threshold,
            modelReady = tr.modelReady,
            downloadProgress = tr.downloadProgress,
            downloadError = tr.downloadError,
            importRunning = tr.importRunning,
            importResult = tr.importResult,
            exportStatus = tr.exportStatus,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(isLoading = true),
    )

    init {
        // Check model readiness once on startup.
        _transient.update { it.copy(modelReady = modelManager.isReady(ModelRegistry.QWEN3_0_6B)) }

        // Reflect the background import job's state into the UI (survives leaving this screen).
        viewModelScope.launch {
            workScheduler.observeImport().collect { info ->
                when (info?.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING ->
                        _transient.update { it.copy(importRunning = true) }
                    WorkInfo.State.SUCCEEDED -> {
                        val s = ImportWorker.summaryOf(info.outputData)
                        _transient.update {
                            it.copy(
                                importRunning = false,
                                importResult = "Imported ${s.inserted} new from last 30 days · ${s.processed} processed",
                            )
                        }
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED ->
                        _transient.update { it.copy(importRunning = false, importResult = "Import failed — try again") }
                    WorkInfo.State.BLOCKED, null -> Unit
                }
            }
        }
    }

    // ── Settings actions ──────────────────────────────────────────────────────

    fun setInterval(min: Int) {
        viewModelScope.launch {
            settingsRepo.setProcessingIntervalMinutes(min)
            workScheduler.reschedule()
        }
    }

    fun setRequiresCharging(value: Boolean) {
        viewModelScope.launch {
            settingsRepo.setRequiresCharging(value)
            workScheduler.reschedule()
        }
    }

    fun setRequiresBatteryNotLow(value: Boolean) {
        viewModelScope.launch {
            settingsRepo.setRequiresBatteryNotLow(value)
            workScheduler.reschedule()
        }
    }

    fun setBackend(choice: BackendChoice) {
        viewModelScope.launch {
            settingsRepo.setBackendPreference(choice)
        }
    }

    fun setConfidenceThreshold(value: Double) {
        viewModelScope.launch {
            settingsRepo.setConfidenceThreshold(value)
        }
    }

    fun setMaxMessagesPerRun(value: Int) {
        viewModelScope.launch {
            settingsRepo.setMaxMessagesPerRun(value)
        }
    }

    // ── Import action ─────────────────────────────────────────────────────────

    /**
     * Starts the background import (last 30 days) + processing job. Runs via WorkManager so it
     * keeps going after the user leaves this screen. State is reflected via [observeImport].
     */
    fun importInbox() {
        if (_transient.value.importRunning) return
        _transient.update { it.copy(importRunning = true, importResult = null) }
        workScheduler.runImport()
    }

    fun clearImportResult() {
        _transient.update { it.copy(importResult = null) }
    }

    // ── Model download action ─────────────────────────────────────────────────

    fun redownloadModel() {
        viewModelScope.launch {
            val spec = ModelRegistry.QWEN3_0_6B
            _transient.update { it.copy(downloadProgress = 0f, downloadError = null) }
            modelManager.download(spec).collect { event ->
                when (event) {
                    is DownloadProgress.Progress -> {
                        val pct = if (event.total > 0) event.bytes.toFloat() / event.total else 0f
                        _transient.update { it.copy(downloadProgress = pct) }
                    }
                    is DownloadProgress.Done -> {
                        _transient.update {
                            it.copy(downloadProgress = null, modelReady = true, downloadError = null)
                        }
                    }
                    is DownloadProgress.Failed -> {
                        _transient.update {
                            it.copy(downloadProgress = null, downloadError = event.error)
                        }
                    }
                }
            }
        }
    }

    fun clearDownloadError() {
        _transient.update { it.copy(downloadError = null) }
    }

    // ── CSV export ────────────────────────────────────────────────────────────

    /** Builds the CSV string from current transactions. Safe to call from a coroutine. */
    suspend fun csvForExport(): String {
        val txns = transactionRepo.observeAll().first()
        return transactionsToCsv(txns)
    }

    fun setExportStatus(status: ExportStatus) {
        _transient.update { it.copy(exportStatus = status) }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Intermediate combine bucket — avoids >5-arg combine. */
    private data class SettingsCore(
        val interval: Int,
        val charging: Boolean,
        val batteryNotLow: Boolean,
        val backend: BackendChoice,
        val maxMsgs: Int,
    )

    /** Mutable transient (non-persisted) state. */
    private data class TransientState(
        val modelReady: Boolean = false,
        val downloadProgress: Float? = null,
        val downloadError: String? = null,
        val importRunning: Boolean = false,
        val importResult: String? = null,
        val exportStatus: ExportStatus = ExportStatus.Idle,
    )
}
