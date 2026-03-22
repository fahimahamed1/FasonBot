package com.fasonbot.app.action

import android.content.Context
import com.fasonbot.app.bot.TelegramApi
import java.io.File

abstract class BaseExecutor(protected val context: Context) {

    protected fun sendResponse(message: String, parseMode: String? = "HTML") {
        TelegramApi.sendMessage(context, message, parseMode = parseMode)
    }

    protected fun sendDocument(file: File, caption: String? = null) {
        TelegramApi.sendDocument(context, file, caption)
    }

    protected fun createTempFile(name: String, content: String): File? {
        return try {
            File.createTempFile("${name}_", ".txt").apply {
                writeText(content)
            }
        } catch (e: Exception) {
            sendResponse("❌ <b>Error</b>\n\n${e.message}")
            null
        }
    }

    protected fun deleteTempFile(file: File?) {
        try {
            file?.delete()
        } catch (_: Exception) {}
    }
}
