package io.github.seijikohara.femto.data.navigation

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
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

private const val TAG = "DestinationGeocoder"
private const val NOMINATIM_SEARCH_URL = "https://nominatim.openstreetmap.org"

internal data class AddressSuggestion(
    val title: String,
    val subtitle: String = "",
    val point: PlaceTarget.Point,
)

internal class DestinationGeocoder(
    private val context: Context,
    private val client: OkHttpClient,
    private val userAgent: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    init {
        OfflineNavigationEngine.appContext = context.applicationContext
    }
    suspend fun resolveTarget(target: PlaceTarget): PlaceTarget.Point? =
        when (target) {
            is PlaceTarget.Point -> target
            is PlaceTarget.Query -> resolveQuery(target.text)
        }

    suspend fun resolveQuery(query: String): PlaceTarget.Point? =
        withContext(ioDispatcher) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext null

            // 0. Direct coordinate parsing
            val coordMatch = Regex("""^([+-]?\d+(?:\.\d+)?)[,\s]+([+-]?\d+(?:\.\d+)?)$""").find(trimmed)
            if (coordMatch != null) {
                val lat = coordMatch.groupValues[1].toDoubleOrNull()
                val lon = coordMatch.groupValues[2].toDoubleOrNull()
                if (lat != null && lon != null) {
                    return@withContext PlaceTarget.Point(lat, lon)
                }
            }

            // 1. OFFLINE FIRST: Resolve locally via SQLite geocoding.db in <2ms
            val offlinePoint = runCatching {
                OfflineNavigationEngine.resolveTarget(trimmed)
            }.getOrNull()
            if (offlinePoint != null) {
                Log.d(TAG, "resolveQuery: instantly resolved '$trimmed' via offline database -> $offlinePoint")
                return@withContext offlinePoint
            }

            // 2. Online geocoding fallback if offline database doesn't have the entry
            val hasExplicitCity = listOf(
                "москва", "казань", "самара", "челябинск", "екатеринбург", "оренбург", "уфа",
                "город", "г.", "область", "район", "респ", "край"
            ).any { trimmed.contains(it, ignoreCase = true) }

            val queriesToTry = if (!hasExplicitCity) {
                listOf("$trimmed, Уфа", trimmed, "$trimmed, Россия")
            } else {
                listOf(trimmed)
            }

            // Try Android platform Geocoder if available
            for (candidate in queriesToTry) {
                runCatching {
                    if (Geocoder.isPresent()) {
                        val geocoder = Geocoder(context, Locale.getDefault())

                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocationName(candidate, 5)
                        if (!addresses.isNullOrEmpty()) {
                            val valid = addresses.firstOrNull {
                                isRussianLocation(
                                    it.featureName ?: it.thoroughfare ?: "",
                                    formatAddressSubtitle(it),
                                    it.latitude,
                                    it.longitude,
                                    it.countryCode,
                                )
                            }
                            if (valid != null) {
                                return@withContext PlaceTarget.Point(valid.latitude, valid.longitude)
                            }
                        }
                    }
                }.onFailure {
                    if (it is CancellationException) throw it
                    Log.d(TAG, "Platform geocoder lookup failed for '$candidate': ${it.message}")
                }
            }

            // Fallback to Nominatim search API with regional viewbox bias
            val effectiveLocation = Location("default").apply {
                latitude = 54.7350
                longitude = 55.9580
            }
            val delta = 0.6
            val viewbox =
                String.format(
                    Locale.ROOT,
                    "%.4f,%.4f,%.4f,%.4f",
                    effectiveLocation.longitude - delta,
                    effectiveLocation.latitude + delta,
                    effectiveLocation.longitude + delta,
                    effectiveLocation.latitude - delta,
                )

            for (candidate in queriesToTry) {
                val point = runCatching {
                    val url =
                        NOMINATIM_SEARCH_URL
                            .toHttpUrl()
                            .newBuilder()
                            .addPathSegment("search")
                            .addQueryParameter("format", "json")
                            .addQueryParameter("limit", "5")
                            .addQueryParameter("countrycodes", "ru")
                            .addQueryParameter("accept-language", "ru")
                            .addQueryParameter("addressdetails", "1")
                            .addQueryParameter("viewbox", viewbox)
                            .addQueryParameter("bounded", "0")
                            .addQueryParameter("q", candidate)
                            .build()

                    val request =
                        Request
                            .Builder()
                            .url(url)
                            .header("User-Agent", userAgent)
                            .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        val body = response.body.string()
                        val array = JSONArray(body)
                        for (i in 0 until array.length()) {
                            val first = array.getJSONObject(i)
                            val lat = first.getDouble("lat")
                            val lon = first.getDouble("lon")
                            val addr = first.optJSONObject("address")
                            val cCode = addr?.optString("country_code")?.lowercase(Locale.ROOT)
                            val displayName = first.optString("display_name")
                            if (isRussianLocation("", displayName, lat, lon, cCode)) {
                                return@use PlaceTarget.Point(lat, lon)
                            }
                        }
                        null
                    }
                }.onFailure {
                    if (it is CancellationException) throw it
                    Log.w(TAG, "Nominatim search failed for '$candidate'", it)
                }.getOrNull()

                if (point != null) {
                    return@withContext point
                }
            }

            null
        }

    suspend fun searchSuggestions(
        query: String,
        userLocation: Location? = null,
        maxResults: Int = 80,
    ): List<AddressSuggestion> =
        withContext(ioDispatcher) {
            val trimmed = query.trim()
            if (trimmed.length < 2) return@withContext emptyList()

            // 0. Direct coordinate input
            val coordMatch = Regex("""^([+-]?\d+(?:\.\d+)?)[,\s]+([+-]?\d+(?:\.\d+)?)$""").find(trimmed)
            if (coordMatch != null) {
                val lat = coordMatch.groupValues[1].toDoubleOrNull()
                val lon = coordMatch.groupValues[2].toDoubleOrNull()
                if (lat != null && lon != null) {
                    return@withContext listOf(
                        AddressSuggestion(
                            title = "Координаты ($lat, $lon)",
                            subtitle = "Прямая точка назначения",
                            point = PlaceTarget.Point(lat, lon),
                        )
                    )
                }
            }

            // 1. OFFLINE FIRST: Query local SQLite geocoding.db immediately (<5ms)
            val userLat = userLocation?.latitude
            val userLon = userLocation?.longitude
            if (userLat != null && userLon != null) {
                OfflineNavigationEngine.updateUserLocation(userLat, userLon)
            }
            val localResults = runCatching {
                OfflineNavigationEngine.searchPlaces(query, maxResults, userLat, userLon)
            }.getOrDefault(emptyList())

            val onlineResults = ArrayList<AddressSuggestion>()

            // 2. Query online sources only to enrich results if internet is available, NEVER blocking local
            val isOnline = NetworkMonitor.isOnline()
            if (isOnline) {
                // Platform geocoder
                runCatching {
                    if (Geocoder.isPresent()) {
                        val geocoder = Geocoder(context, Locale.getDefault())

                        @Suppress("DEPRECATION")
                        val addresses =
                            if (userLocation != null) {
                                val delta = 0.5
                                geocoder.getFromLocationName(
                                    trimmed,
                                    maxResults,
                                    userLocation.latitude - delta,
                                    userLocation.longitude - delta,
                                    userLocation.latitude + delta,
                                    userLocation.longitude + delta,
                                )
                            } else {
                                geocoder.getFromLocationName(trimmed, maxResults)
                            }

                        if (!addresses.isNullOrEmpty()) {
                            for (address in addresses) {
                                val cCode = address.countryCode?.lowercase(Locale.ROOT)
                                if (cCode != null && cCode != "ru") continue
                                val title = formatAddressTitle(address, trimmed)
                                val subtitle = formatAddressSubtitle(address)
                                if (!isRussianLocation(title, subtitle, address.latitude, address.longitude, cCode)) {
                                    continue
                                }
                                onlineResults.add(
                                    AddressSuggestion(
                                        title = title,
                                        subtitle = subtitle,
                                        point = PlaceTarget.Point(address.latitude, address.longitude),
                                    ),
                                )
                            }
                        }
                    }
                }.onFailure {
                    if (it is CancellationException) throw it
                    Log.d(TAG, "Platform geocoder suggestions failed: ${it.message}")
                }

                // Nominatim search API
                runCatching {
                    val effectiveLocation = userLocation ?: Location("default").apply {
                        latitude = 54.7350
                        longitude = 55.9580
                    }
                    val delta = 0.6
                    val viewbox =
                        String.format(
                            Locale.ROOT,
                            "%.4f,%.4f,%.4f,%.4f",
                            effectiveLocation.longitude - delta,
                            effectiveLocation.latitude + delta,
                            effectiveLocation.longitude + delta,
                            effectiveLocation.latitude - delta,
                        )

                    val urlBuilder =
                        NOMINATIM_SEARCH_URL
                            .toHttpUrl()
                            .newBuilder()
                            .addPathSegment("search")
                            .addQueryParameter("format", "json")
                            .addQueryParameter("limit", (maxResults - localResults.size).coerceAtLeast(10).toString())
                            .addQueryParameter("addressdetails", "1")
                            .addQueryParameter("countrycodes", "ru")
                            .addQueryParameter("accept-language", "ru")
                            .addQueryParameter("viewbox", viewbox)
                            .addQueryParameter("bounded", "0")
                            .addQueryParameter("q", trimmed)

                    val request =
                        Request
                            .Builder()
                            .url(urlBuilder.build())
                            .header("User-Agent", userAgent)
                            .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body.string()
                            val array = JSONArray(body)
                            for (i in 0 until array.length()) {
                                val obj = array.getJSONObject(i)
                                val lat = obj.optDouble("lat", 0.0)
                                val lon = obj.optDouble("lon", 0.0)
                                if (lat != 0.0 && lon != 0.0) {
                                    val addr = obj.optJSONObject("address")
                                    val cCode = addr?.optString("country_code")?.lowercase(Locale.ROOT)
                                    if (cCode != null && cCode != "ru") continue
                                    val (title, subtitle) = parseNominatimSuggestion(obj, trimmed)
                                    if (!isRussianLocation(title, subtitle, lat, lon, cCode)) continue
                                    onlineResults.add(
                                        AddressSuggestion(
                                            title = title,
                                            subtitle = subtitle,
                                            point = PlaceTarget.Point(lat, lon),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }.onFailure {
                    if (it is CancellationException) throw it
                    Log.w(TAG, "Nominatim suggestion search failed", it)
                }
            }

            // 3. Combine: Local results ALWAYS prioritized at the top, followed by online enrichments
            val combined = (localResults + onlineResults)
                .filter { isRussianLocation(it.title, it.subtitle, it.point.latitude, it.point.longitude) }
                .distinctBy {
                    // Normalize deduplication key (preserves distinct locations / branches)
                    val cleanTitle = it.title.lowercase(Locale.ROOT).replace(Regex("""[,.\s]+"""), " ").trim()
                    "${cleanTitle}_${String.format(Locale.ROOT, "%.3f_%.3f", it.point.latitude, it.point.longitude)}"
                }
                .take(maxResults)

            combined
        }

    private fun formatAddressTitle(
        address: Address,
        fallbackQuery: String,
    ): String {
        val street = address.thoroughfare
        val house = address.subThoroughfare
        val feature = address.featureName
        return when {
            !street.isNullOrBlank() -> {
                if (!house.isNullOrBlank()) "$street, $house" else street
            }

            !feature.isNullOrBlank() && feature != address.locality -> {
                feature
            }

            else -> {
                address
                    .getAddressLine(0)
                    ?.split(",")
                    ?.firstOrNull()
                    ?.trim() ?: fallbackQuery
            }
        }
    }

    private fun formatAddressSubtitle(address: Address): String {
        val parts = mutableListOf<String>()
        val city = address.locality ?: address.subAdminArea ?: address.adminArea
        if (!city.isNullOrBlank()) parts.add(city)
        val country = address.countryName
        if (!country.isNullOrBlank()) parts.add(country)
        return parts.joinToString(", ")
    }

    private fun parseNominatimSuggestion(
        obj: JSONObject,
        fallbackQuery: String,
    ): Pair<String, String> {
        val addr = obj.optJSONObject("address")
        val road = addr?.optString("road")?.takeIf { it.isNotBlank() }
        val house = addr?.optString("house_number")?.takeIf { it.isNotBlank() }
        val name = obj.optString("name").takeIf { it.isNotBlank() }
        val displayName = obj.optString("display_name")

        val title =
            when {
                !name.isNullOrBlank() && !road.isNullOrBlank() && !house.isNullOrBlank() -> {
                    if (name.equals(house, ignoreCase = true) || name.equals(road, ignoreCase = true)) {
                        "$road, $house"
                    } else {
                        "$name, $road, $house"
                    }
                }
                !road.isNullOrBlank() && !house.isNullOrBlank() -> "$road, $house"
                !name.isNullOrBlank() && !road.isNullOrBlank() -> "$name ($road)"
                !name.isNullOrBlank() -> name
                !road.isNullOrBlank() -> road
                else -> displayName.split(",").firstOrNull()?.trim() ?: fallbackQuery
            }

        val district = addr?.optString("city_district")?.takeIf { it.isNotBlank() }
        val city =
            (
                addr?.optString("city")
                    ?: addr?.optString("town")
                    ?: addr?.optString("village")
                    ?: addr?.optString("municipality")
            )?.takeIf { it.isNotBlank() }
        val country = addr?.optString("country")?.takeIf { it.isNotBlank() }
        val subtitle = listOfNotNull(district, city, country).joinToString(", ")

        return Pair(title, subtitle)
    }

    companion object {
        fun isRussianLocation(
            title: String,
            subtitle: String,
            lat: Double,
            lon: Double,
            countryCode: String? = null,
        ): Boolean {
            if (countryCode != null && countryCode.isNotBlank()) {
                if (!countryCode.equals("ru", ignoreCase = true)) return false
            }

            val text = "${title.lowercase(Locale.ROOT)} ${subtitle.lowercase(Locale.ROOT)}"
            val foreignKeywords = listOf(
                "израиль", "israel", "сша", "usa", "united states", "германи", "germany",
                "польш", "poland", "украин", "ukraine", "казахстан", "kazakhstan",
                "беларусь", "belarus", "турци", "turkey", "франци", "france", "грузи", "georgia",
                "армени", "armenia", "азербайджан", "azerbaijan", "узбекистан", "uzbekistan",
                "таджикистан", "китай", "china", "литва", "латви", "эстони", "финлянд", "finland"
            )
            if (foreignKeywords.any { text.contains(it) }) {
                return false
            }

            // Geographic boundary of the Russian Federation
            if (lat !in 41.0..82.0) return false
            if (lon !in 19.0..180.0 && lon !in -180.0..-168.0) return false

            return true
        }
    }
}
