package com.fasonbot.app.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.fasonbot.app.service.CoreService

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val PREFS_NAME = "fasonbot_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldServiceRun(context)) return

        Log.d(TAG, "Boot received: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED -> startServiceWithBackup(context)
            Intent.ACTION_USER_PRESENT -> {
                // User unlocked - start immediately without delay
                startServiceDirect(context)
                saveServiceState(context, true)
            }
            Intent.ACTION_SHUTDOWN -> saveServiceState(context, true)
            else -> startServiceWithBackup(context)
        }
    }

    private fun startServiceWithBackup(context: Context) {
        startServiceDirect(context)
        // Multiple backup attempts for reliability
        scheduleAlarmBackup(context, 100)
        scheduleAlarmBackup(context, 500)
        scheduleAlarmBackup(context, 1500)
        scheduleAlarmBackup(context, 5000)
        saveServiceState(context, true)
    }

    private fun startServiceDirect(context: Context) {
        try {
            val serviceIntent = Intent(context, CoreService::class.java).apply {
                action = CoreService.ACTION_START
            }
            // Android 9+ always requires startForegroundService for foreground services
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Start service error: ${e.message}")
        }
    }

    private fun scheduleAlarmBackup(context: Context, delayMs: Long) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ServiceRestartReceiver::class.java).apply {
                action = CoreService.ALARM_ACTION
            }
            // Use unique request codes based on delay to avoid collision
            val requestCode = when (delayMs) {
                100L -> 1001
                500L -> 1004
                1000L -> 1002
                1500L -> 1005
                3000L -> 1003
                5000L -> 1006
                else -> (System.currentTimeMillis() % 10000 + delayMs).toInt()
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerTime = SystemClock.elapsedRealtime() + delayMs
            // Android 9+ supports setExactAndAllowWhileIdle
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Schedule alarm error: ${e.message}")
        }
    }

    private fun shouldServiceRun(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SERVICE_ENABLED, true)

    private fun saveServiceState(context: Context, running: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SERVICE_ENABLED, running)
            .apply()
    }
}
