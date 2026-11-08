package io.github.seijikohara.femto.data.navigation

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "NetworkMonitor"

/**
 * Event-driven network connectivity monitor.
 * Operates purely via Android's NetworkCallback with zero periodic polling loops.
 */
internal class NetworkMonitor private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkCurrentOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkLostListeners = mutableListOf<() -> Unit>()
    private val networkAvailableListeners = mutableListOf<() -> Unit>()

    init {
        val request =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

        runCatching {
            connectivityManager?.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.i(TAG, "Internet connection became available")
                        _isOnline.value = true
                        notifyNetworkAvailable()
                    }

                    override fun onLost(network: Network) {
                        Log.i(TAG, "Internet connection lost")
                        _isOnline.value = false
                        notifyNetworkLost()
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        val hasInternet =
                            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        val prev = _isOnline.value
                        _isOnline.value = hasInternet
                        if (prev && !hasInternet) {
                            Log.i(TAG, "Internet validation capability lost")
                            notifyNetworkLost()
                        } else if (!prev && hasInternet) {
                            Log.i(TAG, "Internet validation capability acquired")
                            notifyNetworkAvailable()
                        }
                    }
                },
            )
        }.onFailure {
            Log.w(TAG, "Failed to register network callback", it)
        }
    }

    fun checkCurrentOnline(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun addOnNetworkLostListener(listener: () -> Unit) {
        synchronized(networkLostListeners) {
            networkLostListeners.add(listener)
        }
    }

    fun addOnNetworkAvailableListener(listener: () -> Unit) {
        synchronized(networkAvailableListeners) {
            networkAvailableListeners.add(listener)
        }
    }

    private fun notifyNetworkLost() {
        val listCopy = synchronized(networkLostListeners) { networkLostListeners.toList() }
        for (listener in listCopy) {
            try {
                listener()
            } catch (e: Exception) {
                Log.w(TAG, "Error invoking network lost listener", e)
            }
        }
    }

    private fun notifyNetworkAvailable() {
        val listCopy = synchronized(networkAvailableListeners) { networkAvailableListeners.toList() }
        for (listener in listCopy) {
            try {
                listener()
            } catch (e: Exception) {
                Log.w(TAG, "Error invoking network available listener", e)
            }
        }
    }

    companion object {
        @Volatile
        private var instance: NetworkMonitor? = null

        fun get(context: Context): NetworkMonitor =
            instance ?: synchronized(this) {
                instance ?: NetworkMonitor(context).also { instance = it }
            }

        fun isOnline(): Boolean = instance?.checkCurrentOnline() ?: true
    }
}
