package com.local.smsllm.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.local.smsllm.gate.RegexGate
import com.local.smsllm.repo.SmsRepository
import com.local.smsllm.work.WorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SmsReceiver"

/**
 * Receives incoming SMS broadcasts, applies the regex gate, persists each message,
 * and ensures the periodic extraction worker is scheduled.
 *
 * Security §13: no SMS content (sender, body, gate decision) is ever logged.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var smsRepo: SmsRepository

    @Inject
    lateinit var workScheduler: WorkScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return

        // Assemble multipart messages: group by originating address, concatenate bodies in order.
        val assembled = assembleMessages(parts.map { Triple(it.originatingAddress ?: "", it.messageBody ?: "", it.timestampMillis) })

        if (assembled.isEmpty()) return

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                var insertCount = 0
                for ((sender, body, receivedAt) in assembled) {
                    val gatePassed = RegexGate.passes(sender, body)
                    smsRepo.insertLive(sender, body, receivedAt, gatePassed)
                    insertCount++
                }
                workScheduler.ensureScheduled()
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Processed $insertCount assembled message(s) from broadcast.")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error handling SMS broadcast: ${t.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Groups SMS parts by sender and concatenates their bodies in order.
 * Each element in [parts] is a Triple of (sender, bodyPart, timestampMillis).
 * Returns one [AssembledSms] per distinct sender, using the timestamp of the first part.
 *
 * Extracted as an internal pure function so it can be unit-tested without Android framework.
 */
internal data class AssembledSms(val sender: String, val body: String, val receivedAt: Long)

internal fun assembleMessages(parts: List<Triple<String, String, Long>>): List<AssembledSms> {
    // Preserve insertion order per sender; LinkedHashMap keeps first-seen order.
    val senderParts = LinkedHashMap<String, MutableList<Triple<String, String, Long>>>()
    for (part in parts) {
        senderParts.getOrPut(part.first) { mutableListOf() }.add(part)
    }
    return senderParts.map { (sender, senderTriples) ->
        val body = senderTriples.joinToString("") { it.second }
        val receivedAt = senderTriples.first().third
        AssembledSms(sender, body, receivedAt)
    }
}
