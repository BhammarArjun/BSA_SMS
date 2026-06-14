package com.local.smsllm.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.local.smsllm.sms.SmsImporter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

private const val TAG = "ImportWorker"

/**
 * One-time WorkManager job that imports the last [SmsImporter.WINDOW_DAYS] days of the SMS inbox
 * and then processes the newly-pending messages one-by-one (via [ExtractionProcessor]).
 *
 * Runs in the background so it survives navigation away from the Settings screen and even the app
 * being closed — fixing the "import stops when I change tabs" problem. Progress/result is surfaced
 * to the UI via WorkInfo state + [Result.success] output data (see [keys]).
 *
 * Security §13: no SMS content is logged — only counts.
 */
@HiltWorker
class ImportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val importer: SmsImporter,
    private val processor: ExtractionProcessor,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            // Phase 1 — import the last 30 days (insert-only, no model).
            val result = importer.importInbox()
            Log.d(TAG, "Import done: total=${result.total} inserted=${result.inserted} gatePassed=${result.gatePassed}")

            // Phase 2 — drain the pending queue one batch at a time, in sequence, until empty.
            // Each runOnce loads the model once, processes up to maxMessagesPerRun, and releases it.
            // Best-effort: if processing can't run now (e.g. model not downloaded yet), the imported
            // messages stay PENDING and the periodic worker picks them up — the import still succeeds.
            var processed = 0
            try {
                var guard = 0
                while (guard++ < MAX_DRAIN_PASSES) {
                    val stats = processor.runOnce()
                    processed += stats.processed
                    if (stats.skipped || (stats.processed == 0 && stats.errors == 0)) break
                }
                Log.d(TAG, "Import processing done: processed=$processed")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Exception) {
                Log.w(TAG, "Import processing deferred to scheduler: ${t.javaClass.simpleName}")
            }

            Result.success(
                workDataOf(
                    KEY_TOTAL to result.total,
                    KEY_INSERTED to result.inserted,
                    KEY_GATE_PASSED to result.gatePassed,
                    KEY_PROCESSED to processed,
                ),
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // never swallow structured cancellation
        } catch (t: Exception) {
            Log.w(TAG, "Import worker failed: ${t.javaClass.simpleName}")
            Result.retry()
        }
    }

    companion object {
        /** Safety cap on processing passes so a huge backlog can't loop unbounded. */
        private const val MAX_DRAIN_PASSES = 100

        const val KEY_TOTAL = "total"
        const val KEY_INSERTED = "inserted"
        const val KEY_GATE_PASSED = "gate_passed"
        const val KEY_PROCESSED = "processed"

        /** Reads the summary fields from a finished worker's output [Data]. */
        fun summaryOf(data: Data): ImportSummary = ImportSummary(
            total = data.getInt(KEY_TOTAL, 0),
            inserted = data.getInt(KEY_INSERTED, 0),
            gatePassed = data.getInt(KEY_GATE_PASSED, 0),
            processed = data.getInt(KEY_PROCESSED, 0),
        )
    }
}

/** Result summary surfaced from [ImportWorker] to the UI. */
data class ImportSummary(
    val total: Int,
    val inserted: Int,
    val gatePassed: Int,
    val processed: Int,
)
