package com.local.smsllm.ui.nav

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.local.smsllm.domain.ModelRegistry
import com.local.smsllm.llm.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GateState(
    val smsPermissionsGranted: Boolean = false,
    val modelReady: Boolean = false,
    val checked: Boolean = false,
)

/**
 * Evaluates the two conditions that gate entry into the main app:
 *  1. SMS permissions (RECEIVE_SMS + READ_SMS) are granted.
 *  2. The on-device model file is present and correctly sized.
 *
 * Exposed as a [StateFlow] so the NavHost can react to changes when the user
 * returns from system settings or the model finishes downloading.
 */
@HiltViewModel
class AppGateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager,
) : ViewModel() {

    private val _state = MutableStateFlow(GateState())
    val state: StateFlow<GateState> = _state.asStateFlow()

    init {
        recheck()
    }

    /** Re-evaluate both conditions. Call when returning from settings or after download. */
    fun recheck() {
        viewModelScope.launch {
            val smsOk = hasSmsPermissions()
            val modelOk = modelManager.isReady(ModelRegistry.QWEN3_0_6B)
            _state.update { it.copy(smsPermissionsGranted = smsOk, modelReady = modelOk, checked = true) }
        }
    }

    private fun hasSmsPermissions(): Boolean {
        val receive = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS)
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
        return receive == PackageManager.PERMISSION_GRANTED && read == PackageManager.PERMISSION_GRANTED
    }

    val isReady: Boolean get() = _state.value.let { it.smsPermissionsGranted && it.modelReady }
}
