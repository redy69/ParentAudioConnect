package com.santethp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Network gate shared by both ends of the consented Drive-backup transfer. */
object BackupNetworkPolicy {
    fun isWifiConnected(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun canUseBackupNetwork(context: Context, wifiOnly: Boolean): Boolean =
        !wifiOnly || isWifiConnected(context)
}
