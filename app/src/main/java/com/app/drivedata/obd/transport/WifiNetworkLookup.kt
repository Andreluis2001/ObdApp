package com.app.drivedata.obd.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

class WifiNetworkLookup(context: Context) {

    private val connectivityManager: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    fun activeWifiNetwork(): Network? {
        val manager = connectivityManager ?: return null
        return manager.allNetworks.firstOrNull { network ->
            manager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }
}
