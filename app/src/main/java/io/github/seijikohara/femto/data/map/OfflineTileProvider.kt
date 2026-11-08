package io.github.seijikohara.femto.data.map

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.util.Log
import android.util.LruCache
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.GZIPInputStream

private const val TAG = "OfflineTileProvider"

/**
 * Provides offline vector tiles from an MBTiles archive (SQLite) stored locally.
 * Specifically searches for map files on internal or external storage.
 */
internal class OfflineTileProvider(private val context: Context) {
    private var database: SQLiteDatabase? = null
    private var activeFile: File? = null
    private val tileCache = object : LruCache<Long, ByteArray>(256) {}

    // Bounds: [minLon, minLat, maxLon, maxLat]
    var bounds: DoubleArray = doubleArrayOf(53.18, 51.57, 60.01, 56.55)
    var center: DoubleArray = doubleArrayOf(55.97, 54.73, 10.0) // Ufa

    init {
        openArchive()
    }

    @Synchronized
    fun isAvailable(): Boolean {
        if (database?.isOpen == true) return true
        return openArchive()
    }

    @Synchronized
    private fun openArchive(): Boolean {
        if (database?.isOpen == true) return true

        val candidates = listOfNotNull(
            context.getExternalFilesDir("maps")?.let { File(it, "map.mbtiles") },
            context.getExternalFilesDir("maps")?.let { File(it, "bashkortostan.mbtiles") },
            File(context.filesDir, "map.mbtiles"),
            File(context.filesDir, "bashkortostan.mbtiles"),
            File("/sdcard/Android/data/io.github.seijikohara.femto/files/maps/map.mbtiles"),
            File("/sdcard/Android/data/io.github.seijikohara.femto/files/maps/bashkortostan.mbtiles"),
            File("/sdcard/Femto/maps/map.mbtiles"),
            File("/sdcard/Femto/maps/bashkortostan.mbtiles"),
            File(Environment.getExternalStorageDirectory(), "Femto/maps/map.mbtiles"),
            File(Environment.getExternalStorageDirectory(), "Femto/maps/bashkortostan.mbtiles"),
        )

        for (candidate in candidates) {
            if (candidate.exists() && candidate.canRead() && candidate.length() > 0) {
                try {
                    val db = SQLiteDatabase.openDatabase(
                        candidate.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                    )
                    database = db
                    activeFile = candidate
                    readMetadata(db)
                    Log.i(TAG, "Opened offline MBTiles archive: ${candidate.absolutePath} (${candidate.length() / 1024 / 1024} MB)")
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to open MBTiles candidate ${candidate.absolutePath}", e)
                }
            }
        }

        // Search directory context.getExternalFilesDir("maps") or /sdcard/Femto/maps/ for any .mbtiles
        val searchDirs = listOfNotNull(
            context.getExternalFilesDir("maps"),
            context.filesDir,
            File("/sdcard/Android/data/io.github.seijikohara.femto/files/maps"),
            File("/sdcard/Femto/maps"),
            File(Environment.getExternalStorageDirectory(), "Femto/maps"),
        )
        for (dir in searchDirs) {
            if (dir.exists() && dir.isDirectory) {
                val anyMbtiles = dir.listFiles { f -> f.extension.equals("mbtiles", ignoreCase = true) }?.firstOrNull()
                if (anyMbtiles != null && anyMbtiles.canRead() && anyMbtiles.length() > 0) {
                    try {
                        val db = SQLiteDatabase.openDatabase(
                            anyMbtiles.absolutePath,
                            null,
                            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                        )
                        database = db
                        activeFile = anyMbtiles
                        readMetadata(db)
                        Log.i(TAG, "Opened fallback MBTiles archive: ${anyMbtiles.absolutePath}")
                        return true
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to open fallback MBTiles ${anyMbtiles.absolutePath}", e)
                    }
                }
            }
        }

        return false
    }

    private fun readMetadata(db: SQLiteDatabase) {
        try {
            val cursor = db.rawQuery("SELECT name, value FROM metadata", null)
            cursor.use {
                val nameCol = it.getColumnIndex("name")
                val valCol = it.getColumnIndex("value")
                while (it.moveToNext()) {
                    val name = it.getString(nameCol)
                    val value = it.getString(valCol)
                    when (name) {
                        "bounds" -> {
                            val parts = value.split(',').mapNotNull { p -> p.trim().toDoubleOrNull() }
                            if (parts.size == 4) {
                                bounds = parts.toDoubleArray()
                            }
                        }
                        "center" -> {
                            val parts = value.split(',').mapNotNull { p -> p.trim().toDoubleOrNull() }
                            if (parts.size >= 2) {
                                center = parts.toDoubleArray()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "No metadata table in MBTiles, using defaults")
        }
    }

    /**
     * Fetch a vector tile PBF blob by standard XYZ coordinates.
     * Note: MBTiles uses TMS y-coordinates: tms_y = (2^z - 1) - y
     */
    fun getTile(z: Int, x: Int, y: Int): ByteArray? {
        val key = (z.toLong() shl 42) or (x.toLong() shl 21) or (y.toLong() and 0x1FFFFF)
        tileCache.get(key)?.let { return it }

        if (!isAvailable()) return null
        val db = database ?: return null

        val tmsY = (1 shl z) - 1 - y

        return try {
            val pbf = synchronized(this) {
                val cursor = db.rawQuery(
                    "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ? LIMIT 1",
                    arrayOf(z.toString(), x.toString(), tmsY.toString()),
                )
                cursor.use {
                    if (it.moveToFirst()) {
                        it.getBlob(0)
                    } else {
                        null
                    }
                }
            }
            if (pbf != null && pbf.isNotEmpty()) {
                val decompressed = if (pbf.size >= 2 && pbf[0] == 0x1f.toByte() && pbf[1] == 0x8b.toByte()) {
                    try {
                        GZIPInputStream(pbf.inputStream()).use { it.readBytes() }
                    } catch (e: Exception) {
                        pbf
                    }
                } else {
                    pbf
                }
                tileCache.put(key, decompressed)
                decompressed
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching tile z=$z, x=$x, y=$y", e)
            null
        }
    }

    /**
     * Builds TileJSON describing the offline vector tiles source.
     */
    fun getTileJson(tileUrlTemplate: String = "https://appassets.androidplatform.net/offline-tiles/tiles/{z}/{x}/{y}.pbf"): String {
        var minZoom = 0
        var maxZoom = 14
        var vectorLayers: JSONArray? = null

        synchronized(this) {
            database?.let { db ->
                try {
                    val cursor = db.rawQuery("SELECT name, value FROM metadata", null)
                    cursor.use {
                        val nameCol = it.getColumnIndex("name")
                        val valCol = it.getColumnIndex("value")
                        while (it.moveToNext()) {
                            val name = it.getString(nameCol)
                            val value = it.getString(valCol)
                            when (name) {
                                "minzoom" -> value.toIntOrNull()?.let { z -> minZoom = z }
                                "maxzoom" -> value.toIntOrNull()?.let { z -> maxZoom = z }
                                "json" -> {
                                    runCatching {
                                        val obj = JSONObject(value)
                                        if (obj.has("vector_layers")) {
                                            vectorLayers = obj.getJSONArray("vector_layers")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "No metadata table in MBTiles, using defaults")
                }
            }
        }

        val json = JSONObject().apply {
            put("tilejson", "3.0.0")
            put("name", "Offline Vector Map")
            put("description", "Local offline vector map archive")
            put("version", "1.0.0")
            put("scheme", "xyz")
            put("minzoom", minZoom)
            put("maxzoom", maxZoom)
            put("bounds", JSONArray().apply {
                put(-180.0)
                put(-85.05113)
                put(180.0)
                put(85.05113)
            })
            put("center", JSONArray().apply {
                put(center[0])
                put(center[1])
                put(if (center.size > 2) center[2].toInt() else 10)
            })
            put("tiles", JSONArray().apply {
                put(tileUrlTemplate)
            })
            if (vectorLayers != null) {
                put("vector_layers", vectorLayers)
            }
        }

        return json.toString()
    }

    @Synchronized
    fun close() {
        try {
            database?.close()
        } catch (ignored: Exception) {}
        database = null
        activeFile = null
    }
}
