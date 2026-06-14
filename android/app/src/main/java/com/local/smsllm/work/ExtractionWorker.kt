package com.local.smsllm.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic WorkManager worker that delegates to [ExtractionProcessor].
 *
 * Keeps the worker itself thin: all logic lives in [ExtractionProcessor] for testability.
 */
@HiltWorker
class ExtractionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val processor: ExtractionProcessor,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            processor.runOnce()
            Result.success()
        } catch (t: Throwable) {
            // Transient model-load failures are retried; other unexpected errors surface as failure.
            Result.retry()
        }
    }
}
