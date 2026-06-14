package com.local.smsllm.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.local.smsllm.work.WorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "BootReceiver"

/**
 * Restarts the periodic extraction worker after device reboot.
 *
 * WorkManager persists work requests across reboots internally, but we re-schedule here
 * as a belt-and-suspenders measure and to ensure constraints/interval are current.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var workScheduler: WorkScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                workScheduler.ensureScheduled()
                Log.d(TAG, "Periodic extraction worker re-scheduled after boot.")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to schedule extraction worker on boot: ${t.javaClass.simpleName}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
