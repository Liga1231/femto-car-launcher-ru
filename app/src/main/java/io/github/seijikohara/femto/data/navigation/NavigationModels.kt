package io.github.seijikohara.femto.data.navigation

import androidx.compose.runtime.Immutable
import io.github.seijikohara.femto.data.places.PlaceTarget
import org.json.JSONArray
import org.json.JSONObject

/**
 * A single maneuver step in a driving route.
 */
@Immutable
internal data class NavigationStep(
    val instruction: String,
    val streetName: String,
    val maneuverType: String,
    val maneuverModifier: String?,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val latitude: Double,
    val longitude: Double,
)

/**
 * A complete navigation route from vehicle origin to destination.
 */
internal data class NavigationRoute(
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
    val routeGeoJson: String,
    val coordinates: List<Pair<Double, Double>>, // (lat, lon)
    val steps: List<NavigationStep>,
    val destination: PlaceTarget.Point,
    val destinationLabel: String,
    val summary: String = "",
)

/**
 * Current state of the in-app turn-by-turn navigator.
 */
@Immutable
internal sealed interface NavigationState {
    /** No active navigation. */
    data object Idle : NavigationState

    /** Calculating route to destination. */
    data class Loading(
        val destinationLabel: String,
    ) : NavigationState

    /** Route preview and selection with alternatives before driving. */
    data class RouteSelection(
        val routes: List<NavigationRoute>,
        val selectedIndex: Int = 0,
        val destinationLabel: String,
    ) : NavigationState {
        val selectedRoute: NavigationRoute
            get() = routes.getOrElse(selectedIndex) { routes[0] }

        val combinedRouteGeoJson: String
            get() = buildCombinedRouteGeoJson(routes, selectedIndex, preview = true)
    }

    /** Active navigation following vehicle along the route. */
    data class Active(
        val route: NavigationRoute,
        val currentStepIndex: Int,
        val distanceToNextManeuverMeters: Double,
        val remainingDistanceMeters: Double,
        val remainingDurationSeconds: Double,
        val etaTimestampMillis: Long,
        val remainingRouteGeoJson: String = route.routeGeoJson,
    ) : NavigationState {
        val currentStep: NavigationStep?
            get() = route.steps.getOrNull(currentStepIndex)
        val nextStep: NavigationStep?
            get() = route.steps.getOrNull(currentStepIndex + 1)
    }

    /** Vehicle reached the destination. */
    data class Arrived(
        val destinationLabel: String,
    ) : NavigationState

    /** Route calculation failed or destination not reachable. */
    data class Error(
        val message: String,
        val destinationLabel: String,
    ) : NavigationState
}

internal fun buildCombinedRouteGeoJson(
    routes: List<NavigationRoute>,
    selectedIndex: Int,
    preview: Boolean = true,
): String {
    val root = JSONObject()
    root.put("type", "FeatureCollection")
    if (preview) {
        root.put("preview", true)
    }
    val features = JSONArray()

    // 1. Put unselected routes first (rendered underneath)
    routes.forEachIndexed { index, route ->
        if (index != selectedIndex) {
            runCatching {
                val routeObj = JSONObject(route.routeGeoJson)
                val routeFeatures = routeObj.optJSONArray("features")
                if (routeFeatures != null) {
                    for (i in 0 until routeFeatures.length()) {
                        val f = routeFeatures.getJSONObject(i)
                        if (f.optJSONObject("geometry")?.optString("type") == "LineString") {
                            val copy = JSONObject(f.toString())
                            val props = copy.optJSONObject("properties") ?: JSONObject()
                            props.put("selected", false)
                            props.put("routeIndex", index)
                            copy.put("properties", props)
                            features.put(copy)
                        }
                    }
                }
            }
        }
    }

    // 2. Put selected route second (rendered on top in bold color)
    val selectedRoute = routes.getOrElse(selectedIndex) { routes.firstOrNull() }
    if (selectedRoute != null) {
        runCatching {
            val routeObj = JSONObject(selectedRoute.routeGeoJson)
            val routeFeatures = routeObj.optJSONArray("features")
            if (routeFeatures != null) {
                for (i in 0 until routeFeatures.length()) {
                    val f = routeFeatures.getJSONObject(i)
                    if (f.optJSONObject("geometry")?.optString("type") == "LineString") {
                        val copy = JSONObject(f.toString())
                        val props = copy.optJSONObject("properties") ?: JSONObject()
                        props.put("selected", true)
                        props.put("routeIndex", selectedIndex)
                        copy.put("properties", props)
                        features.put(copy)
                    }
                }
            }
        }

        // 3. Put destination point
        val destFeature = JSONObject().apply {
            put("type", "Feature")
            put(
                "geometry",
                JSONObject().apply {
                    put("type", "Point")
                    put(
                        "coordinates",
                        JSONArray().apply {
                            put(selectedRoute.destination.longitude)
                            put(selectedRoute.destination.latitude)
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
        }
        features.put(destFeature)
    }

    root.put("features", features)
    return root.toString()
}
