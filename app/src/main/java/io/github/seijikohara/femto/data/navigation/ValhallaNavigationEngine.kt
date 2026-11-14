package io.github.seijikohara.femto.data.navigation

import android.content.Context
import android.os.Environment
import android.util.Log
import io.github.seijikohara.femto.data.places.PlaceTarget
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG = "ValhallaNavEngine"

internal object ValhallaNavigationEngine {
    private val bridge by lazy { ValhallaBridge() }
    @Volatile
    private var actorHandle: Long = 0L
    private val initLock = Any()

    fun isAvailable(context: Context): Boolean {
        return findTilesFile(context) != null
    }

    fun findTilesFile(context: Context): File? {
        val candidates = listOfNotNull(
            File("/sdcard/Femto/maps/valhalla_tiles.tar"),
            File(Environment.getExternalStorageDirectory(), "Femto/maps/valhalla_tiles.tar"),
            File("/sdcard/Android/data/io.github.seijikohara.femto/files/maps/valhalla_tiles.tar"),
            context.getExternalFilesDir("maps")?.let { File(it, "valhalla_tiles.tar") },
            File(context.filesDir, "valhalla_tiles.tar"),
        )
        for (f in candidates) {
            if (f.exists() && f.canRead() && f.length() > 0) {
                return f
            }
        }
        return null
    }

    private fun getOrCreateConfig(context: Context, tilesFile: File): File? {
        val configDir = context.filesDir.resolve("valhalla")
        if (!configDir.exists()) configDir.mkdirs()
        val configFile = configDir.resolve("valhalla.json")

        // Read template from assets or build fallback
        val json = try {
            val templateStr = context.assets.open("valhalla_template.json").bufferedReader().use { it.readText() }
            JSONObject(templateStr)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read valhalla_template.json from assets, using fallback", e)
            JSONObject()
        }

        val mjolnir = json.optJSONObject("mjolnir") ?: JSONObject().also { json.put("mjolnir", it) }
        mjolnir.put("tile_extract", tilesFile.absolutePath)
        mjolnir.put("tile_dir", "")
        mjolnir.put("use_lru_mem_cache", true)
        mjolnir.put("lru_mem_cache_hard_control", true)
        mjolnir.put("max_cache_size", 30000000)
        mjolnir.put("concurrency", 2)

        val serviceLimits = json.optJSONObject("service_limits")
        serviceLimits?.remove("auto_pedestrian")
        serviceLimits?.remove("max_exclude_polygons_vertices")
        val ped = serviceLimits?.optJSONObject("pedestrian")
        if (ped != null) {
            if (!ped.has("min_transit_walking_distance")) ped.put("min_transit_walking_distance", 1)
            if (!ped.has("max_transit_walking_distance")) ped.put("max_transit_walking_distance", 10000)
        }

        configFile.writeText(json.toString(2))
        return configFile
    }

    private fun ensureActor(context: Context): Long {
        if (actorHandle != 0L) return actorHandle
        synchronized(initLock) {
            if (actorHandle != 0L) return actorHandle
            val tiles = findTilesFile(context) ?: run {
                Log.w(TAG, "valhalla_tiles.tar not found on device")
                return 0L
            }
            val config = getOrCreateConfig(context, tiles) ?: run {
                Log.w(TAG, "Failed to generate valhalla.json config")
                return 0L
            }

            return try {
                Log.i(TAG, "Initializing native ValhallaActor with config: ${config.absolutePath}")
                val t0 = System.currentTimeMillis()
                val handle = bridge.createActor(config.absolutePath)
                val dt = System.currentTimeMillis() - t0
                if (handle != 0L) {
                    Log.i(TAG, "ValhallaActor successfully created (handle=$handle) in ${dt}ms!")
                    actorHandle = handle
                } else {
                    Log.e(TAG, "bridge.createActor returned 0")
                }
                handle
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize native ValhallaActor", e)
                0L
            }
        }
    }

    fun calculateRoutes(
        context: Context,
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double,
        destinationLabel: String = "Пункт назначения",
        numRoutes: Int = 2,
    ): List<NavigationRoute> {
        val handle = ensureActor(context)
        if (handle == 0L) {
            Log.w(TAG, "calculateRoutes: Valhalla handle unavailable")
            return emptyList()
        }

        return try {
            val req = JSONObject().apply {
                put("locations", JSONArray().apply {
                    put(JSONObject().apply {
                        put("lat", startLat)
                        put("lon", startLon)
                        put("type", "break")
                    })
                    put(JSONObject().apply {
                        put("lat", destLat)
                        put("lon", destLon)
                        put("type", "break")
                    })
                })
                put("costing", "auto")
                if (numRoutes > 1) {
                    put("alternates", numRoutes - 1)
                }
                put("directions_options", JSONObject().apply {
                    put("units", "kilometers")
                    put("language", "ru-RU")
                })
            }

            val t0 = System.currentTimeMillis()
            val reqStr = req.toString()
            Log.d(TAG, "Valhalla route req: $reqStr")
            val responseJsonStr = synchronized(initLock) {
                bridge.route(handle, reqStr)
            }
            val dt = System.currentTimeMillis() - t0
            Log.d(TAG, "Valhalla route response ($dt ms): $responseJsonStr")

            parseValhallaResponse(responseJsonStr, destLat, destLon, destinationLabel)
        } catch (e: Exception) {
            Log.w(TAG, "Valhalla route calculation failed", e)
            emptyList()
        }
    }

    private fun parseValhallaResponse(
        jsonString: String,
        destLat: Double,
        destLon: Double,
        destinationLabel: String,
    ): List<NavigationRoute> {
        val root = JSONObject(jsonString)
        if (root.has("error")) {
            Log.e(TAG, "Valhalla returned error response: code=${root.optInt("error_code")} msg=${root.optString("error")} status=${root.optString("status")}")
            return emptyList()
        }

        val routes = ArrayList<NavigationRoute>()

        // 1. Primary Trip
        val primaryTrip = root.optJSONObject("trip")
        if (primaryTrip != null) {
            val primaryRoute = parseTrip(primaryTrip, destLat, destLon, destinationLabel, routeIndex = 0, isPrimary = true)
            if (primaryRoute != null) {
                routes.add(primaryRoute)
            } else {
                Log.w(TAG, "parseTrip returned null for primary trip!")
            }
        } else {
            Log.w(TAG, "No 'trip' object in Valhalla response! Keys: ${root.keys().asSequence().toList()}")
        }

        // 2. Alternative Trips
        val alternatesArray = root.optJSONArray("alternates")
        if (alternatesArray != null) {
            for (i in 0 until alternatesArray.length()) {
                val altObj = alternatesArray.getJSONObject(i)
                val altTrip = altObj.optJSONObject("trip") ?: continue
                val altRoute = parseTrip(
                    altTrip,
                    destLat,
                    destLon,
                    destinationLabel,
                    routeIndex = i + 1,
                    isPrimary = false,
                    primaryDurationSeconds = routes.firstOrNull()?.totalDurationSeconds,
                )
                if (altRoute != null) {
                    routes.add(altRoute)
                }
            }
        }

        return routes
    }

    private fun parseTrip(
        tripObj: JSONObject,
        destLat: Double,
        destLon: Double,
        destinationLabel: String,
        routeIndex: Int,
        isPrimary: Boolean,
        primaryDurationSeconds: Double? = null,
    ): NavigationRoute? {
        val summary = tripObj.optJSONObject("summary") ?: return null
        val totalDistanceMeters = summary.optDouble("length", 0.0) * 1000.0
        val totalDurationSeconds = summary.optDouble("time", 0.0)

        val legs = tripObj.optJSONArray("legs") ?: return null
        if (legs.length() == 0) return null
        val leg0 = legs.getJSONObject(0)

        val shapeStr = leg0.optString("shape", "")
        if (shapeStr.isBlank()) return null

        val coordinates = decodePolyline6(shapeStr)
        if (coordinates.isEmpty()) return null

        val steps = ArrayList<NavigationStep>()
        val maneuversArray = leg0.optJSONArray("maneuvers")
        var mainStreetName = ""

        if (maneuversArray != null) {
            for (i in 0 until maneuversArray.length()) {
                val mObj = maneuversArray.getJSONObject(i)
                val instruction = mObj.optString("instruction", "").trim()
                val streetNamesArr = mObj.optJSONArray("street_names")
                val streetName = if (streetNamesArr != null && streetNamesArr.length() > 0) {
                    streetNamesArr.getString(0).trim()
                } else ""

                if (streetName.isNotBlank() && mainStreetName.isBlank()) {
                    mainStreetName = streetName
                }

                val distMeters = mObj.optDouble("length", 0.0) * 1000.0
                val durSeconds = mObj.optDouble("time", 0.0)
                val mTypeInt = mObj.optInt("type", 1)

                val (mType, mMod) = classifyValhallaManeuver(mTypeInt)

                val beginIdx = mObj.optInt("begin_shape_index", 0).coerceIn(0, coordinates.lastIndex)
                val (stepLat, stepLon) = coordinates[beginIdx]

                steps.add(
                    NavigationStep(
                        instruction = instruction.ifBlank { "Двигайтесь вперед" },
                        streetName = streetName,
                        maneuverType = mType,
                        maneuverModifier = mMod,
                        distanceMeters = distMeters,
                        durationSeconds = durSeconds,
                        latitude = stepLat,
                        longitude = stepLon,
                    )
                )
            }
        }

        // Build GeoJSON FeatureCollection
        val featureCollection = JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "Feature")
                    put("geometry", JSONObject().apply {
                        put("type", "LineString")
                        put("coordinates", JSONArray().apply {
                            for ((lat, lon) in coordinates) {
                                put(JSONArray().apply {
                                    put(lon)
                                    put(lat)
                                })
                            }
                        })
                    })
                    put("properties", JSONObject().apply {
                        put("selected", routeIndex == 0)
                        put("routeIndex", routeIndex)
                    })
                })
            })
        }

        val km = totalDistanceMeters / 1000.0
        val summaryText = if (isPrimary) {
            val viaText = if (mainStreetName.isNotBlank()) "через $mainStreetName" else "Быстрый"
            "Основной ($viaText) • ${String.format(Locale.ROOT, "%.1f км", km)}"
        } else {
            val diffSec = totalDurationSeconds - (primaryDurationSeconds ?: totalDurationSeconds)
            val diffStr = if (diffSec > 30.0) {
                "+${(diffSec / 60.0).roundToInt()} мин"
            } else if (diffSec < -30.0) {
                "-${(-diffSec / 60.0).roundToInt()} мин"
            } else {
                "Столько же"
            }
            "Альтернативный ($diffStr) • ${String.format(Locale.ROOT, "%.1f км", km)}"
        }

        return NavigationRoute(
            totalDistanceMeters = totalDistanceMeters,
            totalDurationSeconds = totalDurationSeconds,
            routeGeoJson = featureCollection.toString(),
            coordinates = coordinates,
            steps = steps,
            destination = PlaceTarget.Point(destLat, destLon),
            destinationLabel = destinationLabel,
            summary = summaryText,
        )
    }

    private fun classifyValhallaManeuver(type: Int): Pair<String, String?> {
        return when (type) {
            1 -> Pair("depart", null)
            4, 5, 6 -> Pair("arrive", null)
            7 -> Pair("straight", null)
            8 -> Pair("turn", "slight right")
            9 -> Pair("turn", "right")
            10 -> Pair("turn", "sharp right")
            11 -> Pair("turn", "uturn")
            12 -> Pair("turn", "uturn")
            13 -> Pair("turn", "sharp left")
            14 -> Pair("turn", "left")
            15 -> Pair("turn", "slight left")
            16, 17, 18, 19, 20 -> Pair("ramp", null)
            21 -> Pair("straight", null)
            22 -> Pair("turn", "slight right")
            23 -> Pair("turn", "slight left")
            24 -> Pair("merge", null)
            25, 26 -> Pair("roundabout", null)
            else -> Pair("turn", null)
        }
    }

    /**
     * High-speed polyline6 decoder (1e-6 precision).
     */
    fun decodePolyline6(encoded: String): List<Pair<Double, Double>> {
        val poly = ArrayList<Pair<Double, Double>>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            poly.add(Pair(lat / 1e6, lng / 1e6))
        }
        return poly
    }
}
