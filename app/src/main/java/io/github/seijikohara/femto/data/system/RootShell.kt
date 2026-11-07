package io.github.seijikohara.femto.data.system

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes privileged system commands via Magisk root shell (`su -c`).
 * Handles quick settings toggles (Wi-Fi, Bluetooth, Airplane mode, Mobile Data, GPS)
 * and locking down the Android status bar / notification shade while the launcher is active.
 */
internal object RootShell {
    private const val TAG = "RootShell"

    fun run(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val exit = process.waitFor()
            exit == 0
        } catch (e: Exception) {
            Log.w(TAG, "Root command failed: $command", e)
            false
        }
    }

    suspend fun runAsync(command: String): Boolean = withContext(Dispatchers.IO) {
        run(command)
    }

    fun runOut(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            process.waitFor()
            output
        } catch (e: Exception) {
            Log.w(TAG, "Root command out failed: $command", e)
            ""
        }
    }

    /**
     * Locks or unlocks the Android status bar / notification shade.
     * When locked, swipe-down expansion of the shade and notification peeking
     * are completely disabled by Android's StatusBarManager.
     */
    fun lockStatusBar(lock: Boolean) {
        run("cmd statusbar send-disable-flag none")
    }

    suspend fun lockStatusBarAsync(lock: Boolean) = withContext(Dispatchers.IO) {
        lockStatusBar(lock)
    }

    suspend fun setWifi(enabled: Boolean) = withContext(Dispatchers.IO) {
        run("svc wifi ${if (enabled) "enable" else "disable"}")
    }

    suspend fun setBluetooth(enabled: Boolean) = withContext(Dispatchers.IO) {
        run("svc bluetooth ${if (enabled) "enable" else "disable"}")
    }

    suspend fun setAirplaneMode(enabled: Boolean) = withContext(Dispatchers.IO) {
        run("cmd connectivity airplane-mode ${if (enabled) "enable" else "disable"}")
    }

    suspend fun setMobileData(enabled: Boolean) = withContext(Dispatchers.IO) {
        run("svc data ${if (enabled) "enable" else "disable"}")
    }

    suspend fun setLocation(enabled: Boolean) = withContext(Dispatchers.IO) {
        run("cmd location set-location-enabled ${if (enabled) "true" else "false"}")
    }

    suspend fun reboot() = withContext(Dispatchers.IO) {
        run("reboot")
    }

    suspend fun powerOff() = withContext(Dispatchers.IO) {
        run("reboot -p")
    }
}
