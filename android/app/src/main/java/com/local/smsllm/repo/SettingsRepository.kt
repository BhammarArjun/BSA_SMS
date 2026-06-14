package com.local.smsllm.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.local.smsllm.llm.BackendChoice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Clamps a raw interval value to a minimum of 15 minutes. Pure function — easy to unit-test. */
fun clampInterval(min: Int): Int = maxOf(15, min)

/**
 * Narrow interface used by [com.local.smsllm.work.ExtractionProcessor] so the processor
 * can be unit-tested with a simple fake without needing a real DataStore/Context.
 */
interface SettingsAccess {
    suspend fun snapshot(): SettingsSnapshot
    suspend fun setLastRunAt(epochMs: Long)
    suspend fun setLastRunPendingCount(count: Int)
}

/** Snapshot of all settings values for use in workers (one-shot read). */
data class SettingsSnapshot(
    val processingIntervalMinutes: Int,
    val requiresCharging: Boolean,
    val requiresBatteryNotLow: Boolean,
    val backendPreference: BackendChoice,
    val modelId: String,
    val maxMessagesPerRun: Int,
    val confidenceThreshold: Double,
    val lastRunAt: Long,
    val lastRunPendingCount: Int,
)

@Singleton
open class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsAccess {
    private val store: DataStore<Preferences> = context.settingsDataStore

    private object Keys {
        val processingIntervalMinutes = intPreferencesKey("processing_interval_minutes")
        val requiresCharging = booleanPreferencesKey("requires_charging")
        val requiresBatteryNotLow = booleanPreferencesKey("requires_battery_not_low")
        val backendPreference = stringPreferencesKey("backend_preference")
        val modelId = stringPreferencesKey("model_id")
        val maxMessagesPerRun = intPreferencesKey("max_messages_per_run")
        val confidenceThreshold = doublePreferencesKey("confidence_threshold")
        val lastRunAt = longPreferencesKey("last_run_at")
        val lastRunPendingCount = intPreferencesKey("last_run_pending_count")
    }

    // --- Flows ---

    val processingIntervalMinutes: Flow<Int> = store.data.map { prefs ->
        prefs[Keys.processingIntervalMinutes] ?: 30
    }

    val requiresCharging: Flow<Boolean> = store.data.map { prefs ->
        prefs[Keys.requiresCharging] ?: false
    }

    val requiresBatteryNotLow: Flow<Boolean> = store.data.map { prefs ->
        prefs[Keys.requiresBatteryNotLow] ?: true
    }

    val backendPreference: Flow<BackendChoice> = store.data.map { prefs ->
        prefs[Keys.backendPreference]?.let { runCatching { BackendChoice.valueOf(it) }.getOrNull() }
            ?: BackendChoice.AUTO
    }

    val modelId: Flow<String> = store.data.map { prefs ->
        prefs[Keys.modelId] ?: "qwen3_0_6b"
    }

    val maxMessagesPerRun: Flow<Int> = store.data.map { prefs ->
        prefs[Keys.maxMessagesPerRun] ?: 50
    }

    val confidenceThreshold: Flow<Double> = store.data.map { prefs ->
        prefs[Keys.confidenceThreshold] ?: 0.0
    }

    val lastRunAt: Flow<Long> = store.data.map { prefs ->
        prefs[Keys.lastRunAt] ?: 0L
    }

    val lastRunPendingCount: Flow<Int> = store.data.map { prefs ->
        prefs[Keys.lastRunPendingCount] ?: 0
    }

    // --- Suspend snapshot (for workers) ---

    override suspend fun snapshot(): SettingsSnapshot {
        val prefs = store.data.first()
        return SettingsSnapshot(
            processingIntervalMinutes = prefs[Keys.processingIntervalMinutes] ?: 30,
            requiresCharging = prefs[Keys.requiresCharging] ?: false,
            requiresBatteryNotLow = prefs[Keys.requiresBatteryNotLow] ?: true,
            backendPreference = prefs[Keys.backendPreference]
                ?.let { runCatching { BackendChoice.valueOf(it) }.getOrNull() }
                ?: BackendChoice.AUTO,
            modelId = prefs[Keys.modelId] ?: "qwen3_0_6b",
            maxMessagesPerRun = prefs[Keys.maxMessagesPerRun] ?: 50,
            confidenceThreshold = prefs[Keys.confidenceThreshold] ?: 0.0,
            lastRunAt = prefs[Keys.lastRunAt] ?: 0L,
            lastRunPendingCount = prefs[Keys.lastRunPendingCount] ?: 0,
        )
    }

    // --- Suspend setters ---

    suspend fun setProcessingIntervalMinutes(minutes: Int) {
        store.edit { prefs -> prefs[Keys.processingIntervalMinutes] = clampInterval(minutes) }
    }

    suspend fun setRequiresCharging(value: Boolean) {
        store.edit { prefs -> prefs[Keys.requiresCharging] = value }
    }

    suspend fun setRequiresBatteryNotLow(value: Boolean) {
        store.edit { prefs -> prefs[Keys.requiresBatteryNotLow] = value }
    }

    suspend fun setBackendPreference(value: BackendChoice) {
        store.edit { prefs -> prefs[Keys.backendPreference] = value.name }
    }

    suspend fun setModelId(value: String) {
        store.edit { prefs -> prefs[Keys.modelId] = value }
    }

    suspend fun setMaxMessagesPerRun(value: Int) {
        store.edit { prefs -> prefs[Keys.maxMessagesPerRun] = value }
    }

    suspend fun setConfidenceThreshold(value: Double) {
        store.edit { prefs -> prefs[Keys.confidenceThreshold] = value }
    }

    override suspend fun setLastRunAt(epochMs: Long) {
        store.edit { prefs -> prefs[Keys.lastRunAt] = epochMs }
    }

    override suspend fun setLastRunPendingCount(count: Int) {
        store.edit { prefs -> prefs[Keys.lastRunPendingCount] = count }
    }
}
