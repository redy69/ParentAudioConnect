package com.santethp

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.telephony.TelephonyManager
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Publishes lightweight battery and active-network diagnostics for a paired child device.
 * It never reads message content, phone numbers, or device identifiers.
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

        try {
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
            // EXTRA_PLUGGED also covers a device connected to power at FULL.
            val pluggedIn = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            val charging = pluggedIn || batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING
            val temperatureTenths = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_TEMPERATURE,
                -1
            ) ?: -1
            val batteryTempC = if (temperatureTenths >= 0) {
                temperatureTenths / 10.0
            } else {
                -1.0
            }

            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val activeNetwork = connectivity?.activeNetwork
            val capabilities = activeNetwork?.let {
                connectivity.getNetworkCapabilities(it)
            }
            val networkType = when {
                capabilities == null -> "Tidak terhubung"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Data seluler"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Jaringan lain"
            }
            val internetValidated = capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            ) == true

            val operatorName = try {
                context.getSystemService(TelephonyManager::class.java)
                    ?.networkOperatorName
                    ?.takeIf { it.isNotBlank() }
                    ?: "Tidak terdeteksi / tanpa SIM"
            } catch (error: Exception) {
                Log.w(TAG, "Unable to read registered mobile operator", error)
                "Tidak tersedia"
            }

            val status = mapOf(
                "deviceBatteryPercent" to batteryPercent,
                "deviceCharging" to charging,
                "deviceBatteryTempC" to batteryTempC,
                "deviceNetworkType" to networkType,
                "deviceInternetValidated" to internetValidated,
                "deviceOperatorName" to operatorName,
                "deviceStatusAt" to FieldValue.serverTimestamp()
            )

            withContext(Dispatchers.IO) {
                Tasks.await(
                    FirebaseFirestore.getInstance()
                        .collection("pairings")
                        .document(code)
                        .update(status)
                )
            }
        } catch (error: Exception) {
            Log.e(
                TAG,
                "Unable to publish child device status (${error.javaClass.simpleName}): ${error.message}",
                error
            )
            throw error
        }
    }
}
