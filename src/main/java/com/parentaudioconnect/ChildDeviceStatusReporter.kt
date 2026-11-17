package com.santethp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Publishes battery/network heartbeat independently from optional Wi-Fi SSID.
 * A rules rollout or SSID read failure must never suppress battery telemetry
 * or the device heartbeat used by the parent online indicator.
 */
object ChildDeviceStatusReporter {
    private const val TAG = "ChildDeviceStatus"

    suspend fun publish(context: Context) {
        val prefs = context.getSharedPreferences("parent_audio", Context.MODE_PRIVATE)
        val code = prefs.getString("pair_code", null)
        val role = prefs.getString("pair_role", null)
        if (code.isNullOrBlank() || role != "child") {
            Log.w(TAG, "Skipping report: child pairing is missing (role=$role)")
            return
        }

        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) {
            (level * 100f / scale).toInt().coerceIn(0, 100)
        } else {
            -1
        }
        val batteryStatus = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val pluggedIn = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val charging = pluggedIn || batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING
        val temperatureTenths = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_TEMPERATURE,
            -1
        ) ?: -1
        val batteryTempC = if (temperatureTenths >= 0) temperatureTenths / 10.0 else -1.0

        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivity?.activeNetwork
        val capabilities = activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val networkType = when {
            capabilities == null -> "Tidak terhubung"
            isWifi -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Data seluler"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Jaringan lain"
        }
        val internetValidated = capabilities?.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_VALIDATED
        ) == true

        val wifiSsid = readWifiSsid(context, capabilities, isWifi)
        val operatorName = try {
            context.getSystemService(TelephonyManager::class.java)
                ?.networkOperatorName
                ?.takeIf { it.isNotBlank() }
                ?: "Tidak terdeteksi / tanpa SIM"
        } catch (error: Exception) {
            Log.w(TAG, "Unable to read registered mobile operator", error)
            "Tidak tersedia"
        }

        val pairing = FirebaseFirestore.getInstance()
            .collection("pairings")
            .document(code)

        // Critical telemetry first. These fields are already supported by the
        // deployed rules; keep them independent from the newer SSID field.
        val coreStatus = mapOf(
            "deviceBatteryPercent" to batteryPercent,
            "deviceCharging" to charging,
            "deviceBatteryTempC" to batteryTempC,
            "deviceNetworkType" to networkType,
            "deviceInternetValidated" to internetValidated,
            "deviceOperatorName" to operatorName,
            "deviceStatusAt" to FieldValue.serverTimestamp()
        )

        withContext(Dispatchers.IO) {
            Tasks.await(pairing.update(coreStatus))
        }
        Log.i(TAG, "Battery/network heartbeat published for pairing $code")

        // SSID is optional. If the live Firestore rules have not yet been
        // published, its denial must not undo the successful heartbeat above.
        try {
            withContext(Dispatchers.IO) {
                Tasks.await(pairing.update("deviceWifiSsid", wifiSsid))
            }
            Log.i(TAG, "Wi-Fi SSID telemetry published")
        } catch (error: Exception) {
            Log.w(TAG, "Wi-Fi SSID update unavailable; core status remains healthy", error)
        }
    }

    private fun readWifiSsid(
        context: Context,
        capabilities: NetworkCapabilities?,
        isWifi: Boolean
    ): String {
        if (!isWifi ||
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "SSID unavailable: Wi-Fi=$isWifi, fineLocationGranted=" +
                (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED))
            return ""
        }

        return try {
            fun usableSsid(value: String?): String? {
                val cleaned = value?.trim()?.trim('"') ?: return null
                return cleaned.takeIf {
                    it.isNotBlank() &&
                        it != WifiManager.UNKNOWN_SSID &&
                        !it.equals("<unknown ssid>", ignoreCase = true) &&
                        !it.equals("unknown ssid", ignoreCase = true)
                }
            }

            // Android 10+ may provide a WifiInfo whose SSID is redacted.
            // In that case, still try the legacy WifiManager route rather
            // than treating the non-null modern WifiInfo as a successful read.
            val modernSsid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                usableSsid((capabilities?.transportInfo as? WifiInfo)?.ssid)
            } else null
            val legacySsid = if (modernSsid == null) {
                usableSsid(context.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid)
            } else null
            val result = (modernSsid ?: legacySsid)?.take(100).orEmpty()
            Log.d(TAG, "Wi-Fi SSID read: " + if (result.isBlank()) "unavailable" else "available")
            result
        } catch (error: Exception) {
            Log.w(TAG, "Unable to read connected Wi-Fi name", error)
            ""
        }
    }
}
