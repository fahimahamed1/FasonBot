package com.fasonbot.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.ContextCompat
import com.fasonbot.app.service.CoreService

class NetworkReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NetworkReceiver"
        private const val PREFS_NAME = "fasonbot_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Network event: ${intent.action}")

        when (intent.action) {
            ConnectivityManager.CONNECTIVITY_ACTION,
            "android.net.conn.CONNECTIVITY_CHANGE",
            "android.net.wifi.WIFI_STATE_CHANGED" -> {
                if (isNetworkAvailable(context)) {
                    ensureServiceRunning(context)
                }
            }
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun ensureServiceRunning(context: Context) {
        if (!shouldServiceRun(context)) return

        try {
            val intent = Intent(context, CoreService::class.java).apply {
                action = CoreService.ACTION_START
            }
            // Android 9+ always uses startForegroundService
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            Log.e(TAG, "Ensure service error: ${e.message}")
            CoreService.scheduleRestartAlarm(context, 3000)
        }
    }

    private fun shouldServiceRun(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SERVICE_ENABLED, true)
}
