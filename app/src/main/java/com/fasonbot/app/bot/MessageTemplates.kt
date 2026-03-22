package com.fasonbot.app.bot

import android.content.Context
import com.fasonbot.app.config.BotConfig
import com.fasonbot.app.config.DeviceManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MessageTemplates {

    private fun timestamp(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun batteryIcon(level: Int): String = when {
        level >= 80 -> "🔋"
        level >= 50 -> "🪫"
        level >= 20 -> "⚠️"
        else -> "🔴"
    }

    private fun signalIcon(context: Context): String {
        val provider = BotConfig.getProviderName(context)
        return if (provider.isNotEmpty()) "📶" else "📵"
    }

    fun sendOnlineNotification(bot: TelegramBot, context: Context) {
        val device = DeviceManager.getThisDeviceInfo(context)
        val battery = BotConfig.getBatteryPercentage(context)
        bot.sendMessage(
            buildString {
                append("👋 <b>Device Connected</b>\n\n")
                append("📱 <b>${device.deviceName}</b>\n")
                append("━━━━━━━━━━━━━━━━━━\n")
                append("🆔 <code>${device.deviceId}</code>\n")
                append("${batteryIcon(battery)} Battery: <b>$battery%</b>\n")
                append("🤖 Android: <b>${device.androidVersion}</b>\n")
                append("${signalIcon(context)} Network: <b>${BotConfig.getProviderName(context)}</b>\n")
                append("🕐 <i>${timestamp()}</i>")
            },
            parseMode = "HTML"
        )
    }

    fun sendOfflineNotification(bot: TelegramBot, context: Context) {
        val battery = BotConfig.getBatteryPercentage(context)
        bot.sendMessage(
            buildString {
                append("👋 <b>Device Disconnected</b>\n\n")
                append("📱 <b>${BotConfig.getDeviceName()}</b>\n")
                append("━━━━━━━━━━━━━━━━━━\n")
                append("${batteryIcon(battery)} Last Battery: <b>$battery%</b>\n")
                append("🕐 <i>${timestamp()}</i>")
            },
            parseMode = "HTML"
        )
    }

    fun sendProcessingMessage(context: Context) {
        TelegramApi.sendMessage(
            context,
            "⏳ <i>Processing your request...</i>",
            parseMode = "HTML"
        )
    }

    fun formatDeviceInfo(device: DeviceManager.DeviceInfo, context: Context): String {
        val battery = BotConfig.getBatteryPercentage(context)
        val status = if (device.isOnline) "🟢 Online" else "🔴 Offline"
        return buildString {
            append("📱 <b>${device.deviceName}</b>\n")
            append("━━━━━━━━━━━━━━━━━━\n")
            append("🆔 <code>${device.deviceId}</code>\n")
            append("🤖 Android: <b>${device.androidVersion}</b>\n")
            append("${batteryIcon(battery)} Battery: <b>$battery%</b>\n")
            append("${signalIcon(context)} Network: <b>${BotConfig.getProviderName(context)}</b>\n")
            append("📊 Status: $status")
        }
    }

    fun formatSuccess(title: String, details: String = ""): String = buildString {
        append("✅ <b>$title</b>")
        if (details.isNotEmpty()) {
            append("\n━━━━━━━━━━━━━━━━━━\n")
            append(details)
        }
    }

    fun formatError(title: String, details: String = ""): String = buildString {
        append("❌ <b>$title</b>")
        if (details.isNotEmpty()) {
            append("\n━━━━━━━━━━━━━━━━━━\n")
            append(details)
        }
    }

    fun formatProgress(title: String, details: String = ""): String = buildString {
        append("⏳ <b>$title</b>")
        if (details.isNotEmpty()) {
            append("\n━━━━━━━━━━━━━━━━━━\n")
            append(details)
        }
    }
}
