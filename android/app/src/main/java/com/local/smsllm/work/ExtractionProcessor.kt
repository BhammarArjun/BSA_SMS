package com.local.smsllm.work

import android.util.Log
import com.local.smsllm.domain.ProcessingStatus
import com.local.smsllm.llm.LlmService
import com.local.smsllm.repo.SettingsAccess
import com.local.smsllm.repo.SmsRepository
import com.local.smsllm.repo.TransactionRepository
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ExtractionProcessor"

/** Summary of a single processor run. */
data class RunStats(
    val skipped: Boolean,
    val processed: Int,
    val errors: Int,
)

/**
 * Core extraction loop: loads the model once, processes each pending SMS, releases the model.
 *
 * Extracted from [ExtractionWorker] so the logic can be unit-tested without WorkerParameters.
 *
 * Security §13: no SMS bodies, amounts, counterparties, or model output are logged. Only counts/timings.
 */
@Singleton
class ExtractionProcessor @Inject constructor(
    private val smsRepo: SmsRepository,
    private val txnRepo: TransactionRepository,
    private val llm: LlmService,
    private val settings: SettingsAccess,
) {
    /**
     * Runs one extraction pass.
     *
     * - If the pending queue is empty, skips model loading entirely and returns [RunStats.skipped]=true.
     * - On model load failure, throws so the worker can retry.
     * - Per-message errors are caught and stored; processing continues for remaining messages.
     * - The model is always closed after the batch via a finally block.
     */
    suspend fun runOnce(): RunStats {
        val snap = settings.snapshot()
        val pending = smsRepo.pending(snap.maxMessagesPerRun)

        val now = System.currentTimeMillis()

        if (pending.isEmpty()) {
            Log.d(TAG, "No pending messages; skipping model load.")
            settings.setLastRunAt(now)
            settings.setLastRunPendingCount(0)
            return RunStats(skipped = true, processed = 0, errors = 0)
        }

        Log.d(TAG, "Starting extraction pass: pendingCount=${pending.size}")
        val startMs = System.currentTimeMillis()

        // May throw — worker catches and returns Result.retry()
        llm.ensureLoaded(snap.backendPreference)

        var processed = 0
        var errors = 0

        try {
            for (msg in pending) {
                try {
                    val result = llm.extract(msg.body)
                    val msgNow = System.currentTimeMillis()
                    txnRepo.upsertFromExtraction(
                        smsId = msg.id,
                        isTransaction = result.isTransaction,
                        direction = result.direction?.name?.lowercase(),
                        amount = result.amount,
                        currency = result.currency,
                        dateText = result.dateText,
                        dateEpoch = null,
                        counterparty = result.counterparty,
                        category = result.category?.id,
                        confidence = result.confidence,
                        rawModelOutput = result.raw,
                        modelId = snap.modelId,
                        backend = llm.loadedBackend() ?: "unknown",
                        includedInAnalytics = result.isTransaction,
                        createdAt = msgNow,
                        updatedAt = msgNow,
                    )
                    smsRepo.setStatus(
                        id = msg.id,
                        status = if (result.isTransaction) ProcessingStatus.PROCESSED else ProcessingStatus.NON_TXN,
                        processedAt = msgNow,
                        error = null,
                    )
                    processed++
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e            // never swallow structured cancellation
                } catch (t: Exception) {
                    errors++
                    // Log the error type but NOT the message body or extraction output (§13)
                    Log.w(TAG, "Extraction failed for smsId=${msg.id}: ${t.javaClass.simpleName}")
                    smsRepo.setStatus(
                        id = msg.id,
                        status = ProcessingStatus.ERROR,
                        processedAt = null,
                        error = t.javaClass.simpleName.take(200),
                    )
                    // Continue to next message — one failure must not abort the batch
                }
            }
        } finally {
            llm.close()
        }

        val elapsed = System.currentTimeMillis() - startMs
        Log.d(TAG, "Extraction pass done: processed=$processed errors=$errors elapsed=${elapsed}ms")

        val finishNow = System.currentTimeMillis()
        settings.setLastRunAt(finishNow)
        settings.setLastRunPendingCount(pending.size)

        return RunStats(skipped = false, processed = processed, errors = errors)
    }
}
