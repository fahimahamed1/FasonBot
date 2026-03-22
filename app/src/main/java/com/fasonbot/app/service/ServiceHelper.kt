package com.fasonbot.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fasonbot.app.receiver.ServiceRestartReceiver
import com.fasonbot.app.worker.ServiceCheckWorker
import java.util.concurrent.TimeUnit

object ServiceHelper {

    private const val TAG = "ServiceHelper"
    private const val REQUEST_CODE_RESTART = 12345
    private const val REQUEST_CODE_PERIODIC = 12346
    private const val RESTART_DELAY_MS = 5 * 60 * 1000L
    private const val PERIODIC_INTERVAL_MS = 15 * 60 * 1000L
    private const val WORK_TAG = "fasonbot_persistence"
    private const val WORK_NAME_PERIODIC = "fasonbot_periodic"
    private const val PREFS_NAME = "fasonbot_service_prefs"
    private const val KEY_SERVICE_ENABLED = "service_enabled"

    fun scheduleRestartAlarm(context: Context, delayMs: Long = RESTART_DELAY_MS) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ServiceRestartReceiver::class.java).apply {
            action = CoreService.ALARM_ACTION
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_RESTART, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val triggerTime = SystemClock.elapsedRealtime() + delayMs
            // Android 12+ requires permission check for exact alarms
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent
                    )
                }
            } else {
                // Android 9-11 supports setExactAndAllowWhileIdle without permission check
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Schedule restart alarm error: ${e.message}")
            scheduleImmediateWorkManager(context)
        }
    }

    fun schedulePeriodicAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ServiceRestartReceiver::class.java).apply {
            action = CoreService.ALARM_ACTION
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE_PERIODIC, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val triggerTime = SystemClock.elapsedRealtime() + PERIODIC_INTERVAL_MS
            // Android 9+ uses setWindow for battery efficiency
            alarmManager.setWindow(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime, PERIODIC_INTERVAL_MS / 2, pendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Schedule periodic alarm error: ${e.message}")
        }
    }

    fun scheduleWorkManagerFallback(context: Context) {
        try {
            val workRequest = PeriodicWorkRequestBuilder<ServiceCheckWorker>(
                15, TimeUnit.MINUTES
            ).addTag(WORK_TAG).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, workRequest
            )
        } catch (e: Exception) {
            Log.e(TAG, "WorkManager fallback error: ${e.message}")
        }
    }

    fun scheduleImmediateWorkManager(context: Context) {
        try {
            val workRequest = OneTimeWorkRequestBuilder<ServiceCheckWorker>()
                .addTag(WORK_TAG).build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "fasonbot_immediate",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        } catch (e: Exception) {
            Log.e(TAG, "Immediate work manager error: ${e.message}")
        }
    }

    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        // Android 9+ supports battery optimization checking
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun saveServiceState(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SERVICE_ENABLED, enabled)
            .apply()
    }

    fun shouldServiceRun(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SERVICE_ENABLED, true)

    fun initializePersistence(context: Context) {
        saveServiceState(context, true)
        schedulePeriodicAlarm(context)
        scheduleWorkManagerFallback(context)

        if (!CoreService.isRunning()) {
            CoreService.start(context)
        }
    }
}
