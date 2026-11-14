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

    @Volatile
    private var rootAvailableCached: Boolean? = null

    fun isRootAvailable(): Boolean {
        rootAvailableCached?.let { return it }
        val available = try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val finished = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                process.waitFor(1000L, java.util.concurrent.TimeUnit.MILLISECONDS)
            } else {
                process.waitFor() == 0
                true
            }
            if (!finished) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
        } catch (_: Exception) {
            false
        }
        rootAvailableCached = available
        return available
    }

    fun run(command: String, timeoutMs: Long = 1500L): Boolean {
        if (rootAvailableCached == false) return false
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val finished = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            } else {
                process.waitFor() == 0
                true
            }
            if (!finished) {
                process.destroy()
                return false
            }
            val success = process.exitValue() == 0
            if (success) rootAvailableCached = true
            success
        } catch (e: Exception) {
            rootAvailableCached = false
            Log.w(TAG, "Root command failed: $command", e)
            false
        }
    }

    suspend fun runAsync(command: String, timeoutMs: Long = 1500L): Boolean = withContext(Dispatchers.IO) {
        run(command, timeoutMs)
    }

    fun runOut(command: String, timeoutMs: Long = 1500L): String {
        if (rootAvailableCached == false) return ""
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val finished = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            } else {
                process.waitFor() == 0
                true
            }
            if (!finished) {
                process.destroyForcibly()
                return ""
            }
            val res = process.inputStream.bufferedReader().use { it.readText() }.trim()
            rootAvailableCached = true
            res
        } catch (e: Exception) {
            rootAvailableCached = false
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
        if (lock) {
            run("cmd statusbar send-disable-flag expand")
        } else {
            run("cmd statusbar send-disable-flag none")
        }
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
