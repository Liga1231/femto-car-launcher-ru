package io.github.seijikohara.femto.data.navigation

import android.util.Log
import io.github.seijikohara.femto.data.places.PlaceTarget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "OsrmRoutingApi"
private const val PRIMARY_OSRM_URL = "https://router.project-osrm.org"
private const val FALLBACK_OSRM_URL = "https://routing.openstreetmap.de/routed-car"

internal class OsrmRoutingApi(
    private val client: OkHttpClient,
    private val userAgent: String,
    private val primaryBaseUrl: String = PRIMARY_OSRM_URL,
    private val fallbackBaseUrl: String = FALLBACK_OSRM_URL,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun calculateRoute(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double,
        destinationLabel: String = "",
    ): NavigationRoute? = calculateRoutes(startLat, startLon, destLat, destLon, destinationLabel).firstOrNull()

    suspend fun calculateRoutes(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double,
        destinationLabel: String = "",
    ): List<NavigationRoute> =
        withContext(ioDispatcher) {
            val primary = fetchRoutes(primaryBaseUrl, startLat, startLon, destLat, destLon, destinationLabel)
            if (primary.isNotEmpty()) {
                primary
            } else {
                fetchRoutes(fallbackBaseUrl, startLat, startLon, destLat, destLon, destinationLabel)
            }
        }

    private fun fetchRoutes(
        baseUrl: String,
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double,
        destinationLabel: String,
    ): List<NavigationRoute> {
        return runCatching {
            val coordString = String.format(
                Locale.ROOT,
                "%.6f,%.6f;%.6f,%.6f",
                startLon,
                startLat,
                destLon,
                destLat,
            )
            val url = baseUrl
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("route")
                .addPathSegment("v1")
                .addPathSegment("driving")
                .addPathSegment(coordString)
                .addQueryParameter("overview", "full")
                .addQueryParameter("geometries", "geojson")
                .addQueryParameter("steps", "true")
                .addQueryParameter("alternatives", "true")
                .build()

            val request = Request
                .Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "OSRM routing failed HTTP ${response.code} on $baseUrl")
                    return emptyList()
                }
                val body = response.body.string()
                parseRoutesJson(body, destLat, destLon, destinationLabel)
            }
        }.onFailure {
            if (it is CancellationException) throw it
            Log.w(TAG, "OSRM routing error on $baseUrl", it)
        }.getOrDefault(emptyList())
    }

    private fun parseRoutesJson(
        jsonString: String,
        destLat: Double,
        destLon: Double,
        destinationLabel: String,
    ): List<NavigationRoute> {
        val root = JSONObject(jsonString)
        val code = root.optString("code")
        if (code != "Ok") {
            Log.w(TAG, "OSRM response code was $code")
            return emptyList()
        }

        val routesArray = root.optJSONArray("routes") ?: return emptyList()
        val result = ArrayList<NavigationRoute>(routesArray.length())

        for (rIdx in 0 until routesArray.length()) {
            val routeObj = routesArray.getJSONObject(rIdx)
            val totalDistance = routeObj.optDouble("distance", 0.0)
            val totalDuration = routeObj.optDouble("duration", 0.0)
            val geometryObj = routeObj.optJSONObject("geometry") ?: continue

            val coordinatesArray = geometryObj.optJSONArray("coordinates") ?: JSONArray()
            val coordinates = ArrayList<Pair<Double, Double>>(coordinatesArray.length())
            for (i in 0 until coordinatesArray.length()) {
                val coord = coordinatesArray.getJSONArray(i)
                val lon = coord.getDouble(0)
                val lat = coord.getDouble(1)
                coordinates.add(Pair(lat, lon))
            }

            val steps = ArrayList<NavigationStep>()
            var legSummary = ""
            val legsArray = routeObj.optJSONArray("legs")
            if (legsArray != null && legsArray.length() > 0) {
                val legObj = legsArray.getJSONObject(0)
                legSummary = legObj.optString("summary", "").trim()
                val stepsArray = legObj.optJSONArray("steps")
                if (stepsArray != null) {
                    for (i in 0 until stepsArray.length()) {
                        val stepObj = stepsArray.getJSONObject(i)
                        val streetName = stepObj.optString("name", "").trim()
                        val distance = stepObj.optDouble("distance", 0.0)
                        val duration = stepObj.optDouble("duration", 0.0)
                        val maneuverObj = stepObj.optJSONObject("maneuver")

                        val maneuverType = maneuverObj?.optString("type", "turn") ?: "turn"
                        val modifier = maneuverObj?.optString("modifier")?.takeIf { it.isNotBlank() }
                        val maneuverLoc = maneuverObj?.optJSONArray("location")
                        val stepLon = maneuverLoc?.optDouble(0) ?: destLon
                        val stepLat = maneuverLoc?.optDouble(1) ?: destLat

                        val instruction = buildInstruction(maneuverType, modifier, streetName)
                        steps.add(
                            NavigationStep(
                                instruction = instruction,
                                streetName = streetName,
                                maneuverType = maneuverType,
                                maneuverModifier = modifier,
                                distanceMeters = distance,
                                durationSeconds = duration,
                                latitude = stepLat,
                                longitude = stepLon,
                            ),
                        )
                    }
                }
            }

            val featureCollection = JSONObject().apply {
                put("type", "FeatureCollection")
                val features = JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("type", "Feature")
                            put("geometry", geometryObj)
                            put(
                                "properties",
                                JSONObject().apply {
                                    put("selected", true)
                                    put("routeIndex", rIdx)
                                },
                            )
                        },
                    )
                    put(
                        JSONObject().apply {
                            put("type", "Feature")
                            put(
                                "geometry",
                                JSONObject().apply {
                                    put("type", "Point")
                                    put(
                                        "coordinates",
                                        JSONArray().apply {
                                            put(destLon)
                                            put(destLat)
                                        },
                                    )
                                },
                            )
                            put(
                                "properties",
                                JSONObject().apply {
                                    put("destination", true)
                                },
                            )
                        },
                    )
                }
                put("features", features)
            }

            val routeLabel = if (legSummary.isNotBlank()) {
                legSummary
            } else if (rIdx == 0) {
                "Основной"
            } else {
                "Альтернативный $rIdx"
            }

            result.add(
                NavigationRoute(
                    totalDistanceMeters = totalDistance,
                    totalDurationSeconds = totalDuration,
                    routeGeoJson = featureCollection.toString(),
                    coordinates = coordinates,
                    steps = steps,
                    destination = PlaceTarget.Point(destLat, destLon),
                    destinationLabel = destinationLabel,
                    summary = routeLabel,
                ),
            )
        }
        return result
    }

    private fun buildInstruction(
        type: String,
        modifier: String?,
        streetName: String,
    ): String {
        val action = when (type.lowercase(Locale.ROOT)) {
            "depart" -> "Начало движения"

            "arrive" -> "Прибытие в пункт назначения"

            "roundabout", "rotary" -> "Круговое движение"

            "merge" -> "Слияние с дорогой"

            "fork" -> when (modifier?.lowercase(Locale.ROOT)) {
                "left", "slight left" -> "Держитесь левее на развилке"
                "right", "slight right" -> "Держитесь правее на развилке"
                else -> "Развилка"
            }

            "end of road" -> when (modifier?.lowercase(Locale.ROOT)) {
                "left" -> "В конце дороги поверните налево"
                "right" -> "В конце дороги поверните направо"
                else -> "Конец дороги"
            }

            else -> when (modifier?.lowercase(Locale.ROOT)) {
                "left" -> "Поверните налево"
                "right" -> "Поверните направо"
                "slight left" -> "Держитесь левее"
                "slight right" -> "Держитесь правее"
                "sharp left" -> "Крутой поворот налево"
                "sharp right" -> "Крутой поворот направо"
                "uturn" -> "Развернитесь"
                "straight" -> "Двигайтесь прямо"
                else -> "Продолжайте движение"
            }
        }
        return if (streetName.isNotBlank() && type != "arrive") {
            "$action на $streetName"
        } else {
            action
        }
    }
}
