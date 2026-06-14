package com.local.smsllm.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.smsllm.domain.ModelRegistry
import com.local.smsllm.llm.DownloadProgress
import com.local.smsllm.llm.ModelManager
import com.local.smsllm.repo.SettingsRepository
import com.local.smsllm.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep {
    HERO,       // Privacy hero card
    PERMISSIONS, // SMS + notification perms
    DOWNLOAD,   // Model download
    DONE,       // All complete
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.HERO,
    // Permissions
    val smsGranted: Boolean = false,
    val notifGranted: Boolean = false,
    val permsDenied: Boolean = false,
    // Download
    val modelAlreadyReady: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 497_664_000L,
    val downloadDone: Boolean = false,
    val downloadError: String? = null,
)

val OnboardingUiState.downloadFraction: Float
    get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

val OnboardingUiState.downloadedMb: Float get() = downloadedBytes / (1024f * 1024f)
val OnboardingUiState.totalMb: Float get() = totalBytes / (1024f * 1024f)
val OnboardingUiState.downloadPercent: Int get() = (downloadFraction * 100).toInt()

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager,
    private val workScheduler: WorkScheduler,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        // Check if model is already ready (adb-pushed or re-entering onboarding)
        val alreadyReady = modelManager.isReady(ModelRegistry.QWEN3_0_6B)
        _uiState.update { it.copy(modelAlreadyReady = alreadyReady) }
        checkPermsState()
    }

    // ── Navigation between steps ─────────────────────────────────────────────

    fun proceedFromHero() {
        _uiState.update { it.copy(step = OnboardingStep.PERMISSIONS) }
    }

    /** Called after permissions result is processed. */
    fun proceedToDownload() {
        _uiState.update { it.copy(step = OnboardingStep.DOWNLOAD) }
        val state = _uiState.value
        if (state.modelAlreadyReady) {
            // Model already present — skip download, go straight to done
            viewModelScope.launch { finalize() }
        }
    }

    fun proceedToSettings() {
        // Called when perms denied — user needs to open system settings
    }

    // ── Permissions ──────────────────────────────────────────────────────────

    /** Called by the UI once the ActivityResultLauncher delivers results. */
    fun onPermissionsResult(grants: Map<String, Boolean>) {
        val smsOk = grants[Manifest.permission.RECEIVE_SMS] == true &&
            grants[Manifest.permission.READ_SMS] == true
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            grants[Manifest.permission.POST_NOTIFICATIONS] == true
        } else true

        val anySmsGranted = grants[Manifest.permission.RECEIVE_SMS] == true ||
            grants[Manifest.permission.READ_SMS] == true

        _uiState.update {
            it.copy(
                smsGranted = smsOk,
                notifGranted = notifOk,
                permsDenied = !smsOk,
            )
        }

        if (smsOk) {
            proceedToDownload()
        }
    }

    /** Re-checks permissions — call when returning from system settings. */
    fun recheckPermissions() {
        checkPermsState()
        val state = _uiState.value
        if (state.smsGranted) proceedToDownload()
    }

    private fun checkPermsState() {
        val receive = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS)
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
        val smsOk = receive == PackageManager.PERMISSION_GRANTED && read == PackageManager.PERMISSION_GRANTED

        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true

        _uiState.update { it.copy(smsGranted = smsOk, notifGranted = notifOk) }
    }

    // ── Model download ───────────────────────────────────────────────────────

    fun startDownload() {
        if (_uiState.value.isDownloading) return
        _uiState.update { it.copy(isDownloading = true, downloadError = null, downloadedBytes = 0L) }

        viewModelScope.launch {
            modelManager.download(ModelRegistry.QWEN3_0_6B).collect { progress ->
                when (progress) {
                    is DownloadProgress.Progress -> {
                        _uiState.update {
                            it.copy(
                                downloadedBytes = progress.bytes,
                                totalBytes = if (progress.total > 0) progress.total else it.totalBytes,
                            )
                        }
                    }
                    is DownloadProgress.Done -> {
                        _uiState.update {
                            it.copy(isDownloading = false, downloadDone = true, modelAlreadyReady = true)
                        }
                        finalize()
                    }
                    is DownloadProgress.Failed -> {
                        _uiState.update {
                            it.copy(isDownloading = false, downloadError = progress.error)
                        }
                    }
                }
            }
        }
    }

    fun retryDownload() {
        _uiState.update { it.copy(downloadError = null, downloadedBytes = 0L) }
        startDownload()
    }

    private suspend fun finalize() {
        workScheduler.ensureScheduled()
        _uiState.update { it.copy(step = OnboardingStep.DONE) }
    }
}
