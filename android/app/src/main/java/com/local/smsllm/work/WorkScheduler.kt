package com.local.smsllm.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.local.smsllm.repo.SettingsRepository
import com.local.smsllm.repo.clampInterval
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val PERIODIC_WORK_NAME = "extraction-periodic"
private const val ONE_TIME_WORK_NAME = "extraction-now"

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
