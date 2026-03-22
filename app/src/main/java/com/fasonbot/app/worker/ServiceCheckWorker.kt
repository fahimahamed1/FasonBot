package com.fasonbot.app.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.fasonbot.app.service.CoreService
import com.fasonbot.app.service.ServiceHelper

class ServiceCheckWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "ServiceCheckWorker"
        private const val CHECK_DELAY = 500L
        private const val MAX_RETRIES = 3
    }

    override fun doWork(): Result {
        Log.d(TAG, "Service check started")

        return try {
            if (!ServiceHelper.shouldServiceRun(applicationContext)) {
                Log.d(TAG, "Service should not run")
                return Result.success()
            }

            if (CoreService.isRunning()) {
                Log.d(TAG, "Service already running")
                sendHeartbeat()
                return Result.success()
            }

            Log.d(TAG, "Service not running, attempting start")
            val started = attemptStartService()

            if (started) {
                Result.success()
            } else {
                if (runAttemptCount >= MAX_RETRIES) {
                    Log.w(TAG, "Max retries reached, saving state")
                    ServiceHelper.saveServiceState(applicationContext, true)
                    Result.success()
                } else {
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Worker error: ${e.message}")
            ServiceHelper.saveServiceState(applicationContext, true)
            Result.retry()
        }
    }

    private fun attemptStartService(): Boolean {
        // Method 1: Use CoreService.start
        try {
            CoreService.start(applicationContext)
            Thread.sleep(CHECK_DELAY)
            if (CoreService.isRunning()) {
                Log.d(TAG, "Service started via CoreService.start")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Method 1 failed: ${e.message}")
        }

        // Method 2: Direct intent
        try {
            val intent = Intent(applicationContext, CoreService::class.java).apply {
                action = CoreService.ACTION_START
            }
            // Android 9+ always uses startForegroundService
            ContextCompat.startForegroundService(applicationContext, intent)
            Thread.sleep(CHECK_DELAY)
            if (CoreService.isRunning()) {
                Log.d(TAG, "Service started via direct intent")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Method 2 failed: ${e.message}")
        }

        // Method 3: Schedule alarm
        try {
            ServiceHelper.scheduleRestartAlarm(applicationContext, 1000)
            Log.d(TAG, "Scheduled restart alarm")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Method 3 failed: ${e.message}")
        }

        return false
    }

    private fun sendHeartbeat() {
        try {
            val intent = Intent(applicationContext, CoreService::class.java).apply {
                action = CoreService.ACTION_START
            }
            applicationContext.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat error: ${e.message}")
        }
    }
}
