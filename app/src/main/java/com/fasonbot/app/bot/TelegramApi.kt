package com.fasonbot.app.bot

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.fasonbot.app.config.BotConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object TelegramApi {

    private const val TAG = "TelegramApi"
    private const val BASE_URL = "https://api.telegram.org/bot"
    private const val MAX_FILE_SIZE = 50L * 1024 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private fun getApiUrl(context: Context): String = "$BASE_URL${BotConfig.getBotToken()}"

    private fun getChatId(context: Context): String = BotConfig.getChatId()

    fun sendMessage(
        context: Context,
        text: String,
        parseMode: String? = null,
        replyMarkup: JsonObject? = null
    ) {
        try {
            val json = JsonObject().apply {
                addProperty("chat_id", getChatId(context))
                addProperty("text", text)
                parseMode?.let { addProperty("parse_mode", it) }
                replyMarkup?.let { add("reply_markup", it) }
            }
            val request = Request.Builder()
                .url("${getApiUrl(context)}/sendMessage")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use {}
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage error: ${e.message}")
        }
    }

    fun editMessageText(
        context: Context,
        messageId: Int?,
        text: String,
        parseMode: String? = null,
        replyMarkup: JsonObject? = null
    ) {
        if (messageId == null) return
        try {
            val json = JsonObject().apply {
                addProperty("chat_id", getChatId(context))
                addProperty("message_id", messageId)
                addProperty("text", text)
                parseMode?.let { addProperty("parse_mode", it) }
                replyMarkup?.let { add("reply_markup", it) }
            }
            val request = Request.Builder()
                .url("${getApiUrl(context)}/editMessageText")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use {}
        } catch (e: Exception) {
            Log.e(TAG, "editMessageText error: ${e.message}")
        }
    }

    fun deleteMessage(context: Context, messageId: Int?) {
        if (messageId == null) return
        try {
            val url = "${getApiUrl(context)}/deleteMessage?chat_id=${getChatId(context)}&message_id=$messageId"
            client.newCall(Request.Builder().url(url).get().build()).execute().use {}
        } catch (e: Exception) {
            Log.e(TAG, "deleteMessage error: ${e.message}")
        }
    }

    fun sendDocument(context: Context, file: File, caption: String? = null) {
        try {
            if (!file.exists()) {
                sendMessage(context, "❌ File not found: ${file.name}")
                return
            }
            if (file.length() > MAX_FILE_SIZE) {
                sendMessage(context, "❌ File too large: ${file.name}")
                return
            }
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", getChatId(context))
                .addFormDataPart("document", file.name, file.asRequestBody("*/*".toMediaType()))
            caption?.let { requestBody.addFormDataPart("caption", it) }
            val request = Request.Builder()
                .url("${getApiUrl(context)}/sendDocument")
                .post(requestBody.build())
                .build()
            client.newCall(request).execute().use {}
        } catch (e: Exception) {
            Log.e(TAG, "sendDocument error: ${e.message}")
        }
    }

    fun answerCallbackQuery(context: Context, callbackQueryId: String, text: String? = null) {
        try {
            val json = JsonObject().apply {
                addProperty("callback_query_id", callbackQueryId)
                text?.let { addProperty("text", it) }
            }
            val request = Request.Builder()
                .url("${getApiUrl(context)}/answerCallbackQuery")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use {}
        } catch (e: Exception) {
            Log.e(TAG, "answerCallbackQuery error: ${e.message}")
        }
    }

    fun sendPhoto(context: Context, photoFile: File, caption: String? = null) {
        try {
            if (!photoFile.exists()) {
                sendMessage(context, "❌ Photo file not found")
                return
            }
            if (photoFile.length() > MAX_FILE_SIZE) {
                sendMessage(context, "❌ Photo file too large")
                return
            }
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", getChatId(context))
                .addFormDataPart("photo", photoFile.name, photoFile.asRequestBody("image/jpeg".toMediaType()))
            caption?.let { requestBody.addFormDataPart("caption", it) }
            requestBody.addFormDataPart("parse_mode", "HTML")
            val request = Request.Builder()
                .url("${getApiUrl(context)}/sendPhoto")
                .post(requestBody.build())
                .build()
            client.newCall(request).execute().use {}
        } catch (e: Exception) {
            Log.e(TAG, "sendPhoto error: ${e.message}")
        }
    }

    fun sendLocation(context: Context, latitude: Double, longitude: Double, caption: String? = null) {
        try {
            // Send the location first
            val locationJson = JsonObject().apply {
                addProperty("chat_id", getChatId(context))
                addProperty("latitude", latitude)
                addProperty("longitude", longitude)
            }
            val locationRequest = Request.Builder()
                .url("${getApiUrl(context)}/sendLocation")
                .post(locationJson.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(locationRequest).execute().use {}

            // Send caption as separate message if provided
            if (caption != null) {
                sendMessage(context, caption, parseMode = "HTML")
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendLocation error: ${e.message}")
        }
    }
}
