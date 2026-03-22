package com.fasonbot.app.config

import android.content.Context
import android.os.Build
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object DeviceManager {

    private const val PREFS_NAME = "device_manager"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_REGISTERED_DEVICES = "registered_devices"
    private const val KEY_SELECTED_DEVICE = "selected_device"

    // Using unique separator that won't appear in device names
    private const val SEPARATOR = "\u001F" // Unit separator

    data class DeviceInfo(
        val deviceId: String,
        val deviceName: String,
        val androidVersion: String,
        val lastSeen: Long = System.currentTimeMillis(),
        val isOnline: Boolean = true
    ) {
        fun toStorageString(): String =
            "$deviceId$SEPARATOR$deviceName$SEPARATOR$androidVersion$SEPARATOR$lastSeen$SEPARATOR$isOnline"

        companion object {
            fun fromStorageString(str: String): DeviceInfo? {
                val parts = str.split(SEPARATOR)
                if (parts.size < 5) return null
                return DeviceInfo(
                    deviceId = parts[0],
                    deviceName = parts[1],
                    androidVersion = parts[2],
                    lastSeen = parts[3].toLongOrNull() ?: System.currentTimeMillis(),
                    isOnline = parts[4].toBoolean()
                )
            }
        }
    }

    // Cache for device list operations
    private val deviceCache = ConcurrentHashMap<String, DeviceInfo>()

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)

        if (deviceId.isNullOrEmpty()) {
            deviceId = generateDeviceId()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }

        return deviceId
    }

    private fun generateDeviceId(): String {
        val manufacturer = Build.MANUFACTURER.take(3).uppercase()
        val model = Build.MODEL.take(5).lowercase()
        val random = UUID.randomUUID().toString().take(8)
        return "${manufacturer}_${model}_$random"
    }

    fun getThisDeviceInfo(context: Context): DeviceInfo = DeviceInfo(
        deviceId = getDeviceId(context),
        deviceName = BotConfig.getDeviceName(),
        androidVersion = BotConfig.getAndroidVersionName(),
        lastSeen = System.currentTimeMillis(),
        isOnline = true
    )

    fun registerDevice(context: Context) {
        val thisDevice = getThisDeviceInfo(context)
        deviceCache[thisDevice.deviceId] = thisDevice

        val devices = getRegisteredDevices(context).toMutableList()
        val existingIndex = devices.indexOfFirst { it.deviceId == thisDevice.deviceId }

        if (existingIndex >= 0) {
            devices[existingIndex] = thisDevice
        } else {
            devices.add(thisDevice)
        }

        saveRegisteredDevices(context, devices)
    }

    fun getRegisteredDevices(context: Context): List<DeviceInfo> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val devicesStr = prefs.getString(KEY_REGISTERED_DEVICES, "") ?: ""

        if (devicesStr.isEmpty()) return emptyList()

        return devicesStr.split("\n")
            .mapNotNull { DeviceInfo.fromStorageString(it) }
            .sortedByDescending { it.lastSeen }
    }

    private fun saveRegisteredDevices(context: Context, devices: List<DeviceInfo>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val devicesStr = devices.joinToString("\n") { it.toStorageString() }
        prefs.edit().putString(KEY_REGISTERED_DEVICES, devicesStr).apply()
    }

    fun updateDeviceStatus(context: Context, deviceId: String, isOnline: Boolean) {
        val devices = getRegisteredDevices(context).toMutableList()
        val index = devices.indexOfFirst { it.deviceId == deviceId }

        if (index >= 0) {
            devices[index] = devices[index].copy(
                isOnline = isOnline,
                lastSeen = System.currentTimeMillis()
            )
            deviceCache[deviceId] = devices[index]
            saveRegisteredDevices(context, devices)
        }
    }

    fun setSelectedDevice(context: Context, deviceId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_DEVICE, deviceId)
            .apply()
    }

    fun getSelectedDevice(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_DEVICE, null)

    fun isThisDevice(context: Context, deviceId: String): Boolean =
        getDeviceId(context) == deviceId

    fun removeDevice(context: Context, deviceId: String) {
        val devices = getRegisteredDevices(context).toMutableList()
        devices.removeAll { it.deviceId == deviceId }
        deviceCache.remove(deviceId)
        saveRegisteredDevices(context, devices)
    }

    fun clearDevices(context: Context) {
        val thisDevice = getThisDeviceInfo(context)
        deviceCache.clear()
        deviceCache[thisDevice.deviceId] = thisDevice
        saveRegisteredDevices(context, listOf(thisDevice))
    }

    fun getDeviceDisplayName(device: DeviceInfo): String {
        val statusIcon = if (device.isOnline) "🟢" else "🔴"
        return "$statusIcon ${device.deviceName}"
    }

    fun formatDeviceInfo(device: DeviceInfo): String {
        val status = if (device.isOnline) "🟢 Online" else "🔴 Offline"
        return buildString {
            append("📱 <b>${device.deviceName}</b>\n")
            append("━━━━━━━━━━━━━━━━━━\n")
            append("🆔 <code>${device.deviceId}</code>\n")
            append("🤖 Android: <b>${device.androidVersion}</b>\n")
            append("📊 Status: $status")
        }
    }
}
