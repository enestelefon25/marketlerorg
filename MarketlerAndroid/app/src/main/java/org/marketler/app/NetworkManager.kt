package org.marketler.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper

/**
 * İnternet bağlantısını takip eder. Bağlantı geri geldiğinde [onNetworkAvailable] ana iş parçacığında çağrılır.
 */
class NetworkManager(
    context: Context,
    private val onNetworkAvailable: () -> Unit
) {
    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            mainHandler.post { onNetworkAvailable() }
        }
    }

    fun isOnline(): Boolean {
        val network = connectivityManager?.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun start() {
        if (registered || connectivityManager == null) return
        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
            registered = true
        } catch (_: RuntimeException) {
            // Bazı cihazlarda çok fazla callback kaydı hatası alınabilir; takip olmadan devam edilir.
        }
    }

    fun stop() {
        if (!registered) return
        try {
            connectivityManager?.unregisterNetworkCallback(callback)
        } catch (_: IllegalArgumentException) {
        }
        registered = false
    }
}
