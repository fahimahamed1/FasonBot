package com.fasonbot.app.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.fasonbot.app.service.CoreService
import com.fasonbot.app.service.ServiceHelper

class ServiceRestartWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        private const val TAG = "ServiceRestartWorker"
    }

    override fun doWork(): Result {
        Log.d(TAG, "Service restart worker started")

        return try {
            val intent = Intent(applicationContext, CoreService::class.java).apply {
                action = CoreService.ACTION_START
            }

            // Android 9+ always uses startForegroundService
            ContextCompat.startForegroundService(applicationContext, intent)

            Log.d(TAG, "Service restart triggered")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Service restart error: ${e.message}")
            ServiceHelper.scheduleRestartAlarm(applicationContext, 3000)
            Result.retry()
        }
    }
}
