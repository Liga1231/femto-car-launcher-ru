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
    suspend fun resolveTarget(target: PlaceTarget): PlaceTarget.Point? =
        when (target) {
            is PlaceTarget.Point -> target
            is PlaceTarget.Query -> resolveQuery(target.text)
        }

    suspend fun resolveQuery(query: String): PlaceTarget.Point? =
        withContext(ioDispatcher) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext null

            // 1. Try Android platform Geocoder first if available
            runCatching {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale.getDefault())

                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(trimmed, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val first = addresses[0]
                        return@withContext PlaceTarget.Point(first.latitude, first.longitude)
                    }
                }
            }.onFailure {
                if (it is CancellationException) throw it
                Log.d(TAG, "Platform geocoder lookup failed, trying Nominatim fallback: ${it.message}")
            }

            // 2. Fallback to Nominatim search API
            runCatching {
                val url =
                    NOMINATIM_SEARCH_URL
                        .toHttpUrl()
                        .newBuilder()
                        .addPathSegment("search")
                        .addQueryParameter("format", "json")
                        .addQueryParameter("limit", "1")
                        .addQueryParameter("q", trimmed)
                        .build()

                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("User-Agent", userAgent)
                        .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body.string()
                    val array = JSONArray(body)
                    if (array.length() > 0) {
                        val first = array.getJSONObject(0)
                        val lat = first.getDouble("lat")
                        val lon = first.getDouble("lon")
                        PlaceTarget.Point(lat, lon)
                    } else {
                        null
                    }
                }
            }.onFailure {
                if (it is CancellationException) throw it
                Log.w(TAG, "Nominatim search failed", it)
            }.getOrNull()
        }

    suspend fun searchSuggestions(
        query: String,
        userLocation: Location? = null,
        maxResults: Int = 5,
    ): List<AddressSuggestion> =
        withContext(ioDispatcher) {
            val trimmed = query.trim()
            if (trimmed.length < 2) return@withContext emptyList()

            val results = ArrayList<AddressSuggestion>()

            // 1. Try Android platform Geocoder first if available
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
                            val title = formatAddressTitle(address, trimmed)
                            val subtitle = formatAddressSubtitle(address)
                            results.add(
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

            if (results.isNotEmpty()) {
                return@withContext results.take(maxResults)
            }

            // 2. Fallback to Nominatim search API
            runCatching {
                val urlBuilder =
                    NOMINATIM_SEARCH_URL
                        .toHttpUrl()
                        .newBuilder()
                        .addPathSegment("search")
                        .addQueryParameter("format", "json")
                        .addQueryParameter("limit", maxResults.toString())
                        .addQueryParameter("addressdetails", "1")
                        .addQueryParameter("accept-language", Locale.getDefault().language)
                        .addQueryParameter("q", trimmed)

                if (userLocation != null) {
                    val delta = 0.5
                    val viewbox =
                        String.format(
                            Locale.ROOT,
                            "%.4f,%.4f,%.4f,%.4f",
                            userLocation.longitude - delta,
                            userLocation.latitude + delta,
                            userLocation.longitude + delta,
                            userLocation.latitude - delta,
                        )
                    urlBuilder.addQueryParameter("viewbox", viewbox)
                    urlBuilder.addQueryParameter("bounded", "0")
                }

                val request =
                    Request
                        .Builder()
                        .url(urlBuilder.build())
                        .header("User-Agent", userAgent)
                        .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val body = response.body.string()
                    val array = JSONArray(body)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val lat = obj.optDouble("lat", 0.0)
                        val lon = obj.optDouble("lon", 0.0)
                        if (lat != 0.0 && lon != 0.0) {
                            val (title, subtitle) = parseNominatimSuggestion(obj, trimmed)
                            results.add(
                                AddressSuggestion(
                                    title = title,
                                    subtitle = subtitle,
                                    point = PlaceTarget.Point(lat, lon),
                                ),
                            )
                        }
                    }
                }
            }.onFailure {
                if (it is CancellationException) throw it
                Log.w(TAG, "Nominatim suggestion search failed", it)
            }

            results.distinctBy { "${it.point.latitude},${it.point.longitude}" }.take(maxResults)
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
                road != null -> if (house != null) "$road, $house" else road
                name != null -> name
                else -> displayName.split(",").firstOrNull()?.trim() ?: fallbackQuery
            }

        val city =
            (
                addr?.optString("city")
                    ?: addr?.optString("town")
                    ?: addr?.optString("village")
                    ?: addr?.optString("municipality")
            )?.takeIf { it.isNotBlank() }
        val country = addr?.optString("country")?.takeIf { it.isNotBlank() }
        val subtitle = listOfNotNull(city, country).joinToString(", ")

        return Pair(title, subtitle)
    }
}
