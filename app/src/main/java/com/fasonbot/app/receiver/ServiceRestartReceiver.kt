package com.fasonbot.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.fasonbot.app.service.CoreService

class ServiceRestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ServiceRestartReceiver"
        private const val PREFS_NAME = "fasonbot_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Restart alarm received")

        if (!shouldServiceRun(context)) return

        try {
            val serviceIntent = Intent(context, CoreService::class.java).apply {
                action = CoreService.ACTION_START
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Service restart error: ${e.message}")
            CoreService.scheduleRestartAlarm(context, 3000)
        }
    }

    private fun shouldServiceRun(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SERVICE_ENABLED, true)
}
