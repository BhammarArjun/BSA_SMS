package com.local.smsllm.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.local.smsllm.repo.SettingsRepository
import com.local.smsllm.repo.clampInterval
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val PERIODIC_WORK_NAME = "extraction-periodic"
private const val ONE_TIME_WORK_NAME = "extraction-now"
private const val IMPORT_WORK_NAME = "import-inbox"

/**
 * Schedules and manages [ExtractionWorker] runs via WorkManager.
 *
 * [ensureScheduled] and [reschedule] are suspend so they can read a settings snapshot
 * without blocking the calling thread with runBlocking.
 */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /**
     * Enqueues the periodic worker if not already running ([ExistingPeriodicWorkPolicy.KEEP]).
     * Safe to call on every incoming SMS — idempotent.
     */
    suspend fun ensureScheduled() {
        val snap = settings.snapshot()
        val request = buildPeriodicRequest(snap.processingIntervalMinutes, snap.requiresCharging, snap.requiresBatteryNotLow)
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Re-enqueues the periodic worker with updated interval and constraints ([ExistingPeriodicWorkPolicy.UPDATE]).
     * Called when the user changes settings.
     */
    suspend fun reschedule() {
        val snap = settings.snapshot()
        val request = buildPeriodicRequest(snap.processingIntervalMinutes, snap.requiresCharging, snap.requiresBatteryNotLow)
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * Enqueues a one-time expedited extraction run immediately.
     * Degrades gracefully if the expedited quota is exhausted (§11).
     */
    fun runNow() {
        val request = OneTimeWorkRequestBuilder<ExtractionWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * "Process now": run pending extraction immediately AND restart the periodic timer so the next
     * automatic run is a full interval from now. The new next-run time is reflected by
     * [observeNextRunAt].
     */
    suspend fun processNow() {
        runNow()
        resetSchedule()
    }

    /**
     * Deterministically resets the periodic timer: cancels the existing periodic work and enqueues
     * a fresh one, so WorkManager recomputes the next run as a full interval from now.
     */
    suspend fun resetSchedule() {
        val snap = settings.snapshot()
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        val request = buildPeriodicRequest(snap.processingIntervalMinutes, snap.requiresCharging, snap.requiresBatteryNotLow)
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Enqueues the one-time inbox import + processing job (last 30 days). Runs in the background so
     * it survives leaving the Settings screen. [ExistingWorkPolicy.KEEP] means tapping again while
     * one is already running is a no-op.
     */
    fun runImport() {
        val request = OneTimeWorkRequestBuilder<ImportWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(
            IMPORT_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /** Emits the import job's current [WorkInfo] (or null if it has never run). */
    fun observeImport(): Flow<WorkInfo?> =
        workManager.getWorkInfosForUniqueWorkFlow(IMPORT_WORK_NAME).map { it.firstOrNull() }

    /**
     * Emits the estimated epoch-millis of the next periodic run, or null when not enqueued/known.
     * Sourced from WorkManager's own [WorkInfo.getNextScheduleTimeMillis].
     */
    fun observeNextRunAt(): Flow<Long?> =
        workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_WORK_NAME).map { infos ->
            val info = infos.firstOrNull() ?: return@map null
            val next = info.nextScheduleTimeMillis
            if (info.state == WorkInfo.State.ENQUEUED && next != Long.MAX_VALUE) next else null
        }

    private fun buildPeriodicRequest(
        intervalMinutes: Int,
        requiresCharging: Boolean,
        requiresBatteryNotLow: Boolean,
    ) = PeriodicWorkRequestBuilder<ExtractionWorker>(
        clampInterval(intervalMinutes).toLong(),
        TimeUnit.MINUTES,
    ).setConstraints(
        Constraints.Builder()
            .setRequiresCharging(requiresCharging)
            .setRequiresBatteryNotLow(requiresBatteryNotLow)
            .build(),
    ).build()
}
