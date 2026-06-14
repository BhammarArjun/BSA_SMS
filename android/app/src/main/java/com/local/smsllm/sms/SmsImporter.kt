package com.local.smsllm.sms

import android.content.Context
import android.provider.Telephony
import com.local.smsllm.gate.RegexGate
import com.local.smsllm.repo.SmsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot importer that reads the device SMS inbox and inserts messages into the local DB.
 *
 * Uses [SmsRepository.insertImported] which calls insertIgnore, so re-running is safe —
 * duplicates are silently skipped and counted.
 *
 * Security §13: cursor row contents (address, body) are never logged.
 */
@Singleton
class SmsImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smsRepo: SmsRepository,
) {

    data class ImportResult(val total: Int, val inserted: Int, val gatePassed: Int)

    companion object {
        /** Import window: only the most recent [WINDOW_DAYS] days of the inbox are scanned. */
        const val WINDOW_DAYS = 30
        private const val WINDOW_MS = WINDOW_DAYS * 24L * 60 * 60 * 1000
    }

    /**
     * Imports the SMS inbox (only messages newer than [sinceMillis], default the last
     * [WINDOW_DAYS] days) on [Dispatchers.IO]. Inserts raw rows only — no model work; the
     * inserted PENDING messages are processed by the extraction worker.
     * [onProgress] is called periodically (every 50 rows) with (rowsScanned, totalRows).
     * Returns an [ImportResult] with aggregate counts.
     */
    suspend fun importInbox(
        sinceMillis: Long = System.currentTimeMillis() - WINDOW_MS,
        onProgress: (imported: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportResult = withContext(Dispatchers.IO) {
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(
                Telephony.Sms.Inbox.ADDRESS,
                Telephony.Sms.Inbox.BODY,
                Telephony.Sms.Inbox.DATE,
            ),
            "${Telephony.Sms.Inbox.DATE} >= ?",
            arrayOf(sinceMillis.toString()),
            "${Telephony.Sms.Inbox.DATE} DESC",
        )

        if (cursor == null) return@withContext ImportResult(0, 0, 0)

        cursor.use { c ->
            val total = c.count
            var scanned = 0
            var inserted = 0
            var gatePassed = 0

            val colAddress = c.getColumnIndexOrThrow(Telephony.Sms.Inbox.ADDRESS)
            val colBody = c.getColumnIndexOrThrow(Telephony.Sms.Inbox.BODY)
            val colDate = c.getColumnIndexOrThrow(Telephony.Sms.Inbox.DATE)

            while (c.moveToNext()) {
                val address = c.getString(colAddress) ?: ""
                val body = c.getString(colBody) ?: ""
                val date = c.getLong(colDate)

                val passed = RegexGate.passes(address, body)
                if (passed) gatePassed++

                val rowId = smsRepo.insertImported(address, body, date, passed)
                if (rowId != -1L) inserted++

                scanned++
                if (scanned % 50 == 0) {
                    onProgress(scanned, total)
                }
            }

            // Final progress callback if not already at a multiple of 50
            if (scanned % 50 != 0) {
                onProgress(scanned, total)
            }

            ImportResult(total = total, inserted = inserted, gatePassed = gatePassed)
        }
    }
}
