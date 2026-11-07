package io.github.seijikohara.femto.data.navigation

import android.location.Location
import android.os.SystemClock
import android.util.Log
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.places.SearchHistoryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "NavigationManager"
private const val ARRIVAL_DISTANCE_METERS = 35.0
private const val STEP_ADVANCE_DISTANCE_METERS = 35.0
private const val OFF_ROUTE_DISTANCE_METERS = 75.0
private const val RECALCULATE_COOLDOWN_MS = 8000L

internal class NavigationManager(
    private val routingApi: OsrmRoutingApi,
    private val destinationGeocoder: DestinationGeocoder,
    private val historyStore: SearchHistoryStore? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val _state = MutableStateFlow<NavigationState>(NavigationState.Idle)
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    private var currentRouteJob: Job? = null
    private var lastRecalculateMs: Long = 0L
    private var lastKnownLocation: Location? = null
    private var pendingTarget: PlaceTarget? = null
    private var pendingLabel: String = ""

    fun startNavigation(
        origin: Location?,
        target: PlaceTarget,
        label: String = "",
    ) {
        val destLabel = label.ifBlank {
            when (target) {
                is PlaceTarget.Query -> target.text
                is PlaceTarget.Point -> "Пункт назначения"
            }
        }

        val startLoc = origin ?: lastKnownLocation
        if (startLoc == null) {
            _state.value = NavigationState.Error("Нет GPS сигнала", destLabel)
            return
        }
        lastKnownLocation = startLoc
        pendingTarget = target
        pendingLabel = destLabel

        historyStore?.let { store ->
            scope.launch(Dispatchers.IO) {
                store.add(destLabel, target)
            }
        }

        currentRouteJob?.cancel()
        _state.value = NavigationState.Loading(destLabel)

        currentRouteJob = scope.launch {
            try {
                val destPoint = destinationGeocoder.resolveTarget(target)
                if (destPoint == null) {
                    _state.value = NavigationState.Error("Пункт назначения не найден", destLabel)
                    return@launch
                }

                val routes = routingApi.calculateRoutes(
                    startLat = startLoc.latitude,
                    startLon = startLoc.longitude,
                    destLat = destPoint.latitude,
                    destLon = destPoint.longitude,
                    destinationLabel = destLabel,
                )

                if (routes.isEmpty()) {
                    _state.value = NavigationState.Error("Маршрут не найден или нет подключения к сети", destLabel)
                    return@launch
                }

                // Show route preview with alternatives on map & HUD!
                _state.value = NavigationState.RouteSelection(
                    routes = routes,
                    selectedIndex = 0,
                    destinationLabel = destLabel,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Navigation setup failed", e)
                _state.value = NavigationState.Error("Ошибка навигации", destLabel)
            }
        }
    }

    fun selectRoute(index: Int) {
        val current = _state.value as? NavigationState.RouteSelection ?: return
        if (index in current.routes.indices) {
            _state.value = current.copy(selectedIndex = index)
        }
    }

    fun confirmRoute(
        index: Int? = null,
        origin: Location? = null,
    ) {
        val current = _state.value as? NavigationState.RouteSelection ?: return
        val chosenIndex = index ?: current.selectedIndex
        val route = current.routes.getOrElse(chosenIndex) { current.routes[0] }
        val loc = origin ?: lastKnownLocation

        val distanceToFirst = route.steps.firstOrNull()?.let {
            if (loc != null) {
                computeDistance(loc.latitude, loc.longitude, it.latitude, it.longitude)
            } else {
                it.distanceMeters
            }
        } ?: route.totalDistanceMeters

        val now = System.currentTimeMillis()
        val eta = now + (route.totalDurationSeconds * 1000).toLong()

        _state.value = NavigationState.Active(
            route = route,
            currentStepIndex = 0,
            distanceToNextManeuverMeters = distanceToFirst,
            remainingDistanceMeters = route.totalDistanceMeters,
            remainingDurationSeconds = route.totalDurationSeconds,
            etaTimestampMillis = eta,
        )
    }

    fun onLocationChanged(location: Location) {
        lastKnownLocation = location
        val current = _state.value as? NavigationState.Active ?: return
        val route = current.route

        // 1. Check arrival at final destination
        val distanceToDest = computeDistance(
            location.latitude,
            location.longitude,
            route.destination.latitude,
            route.destination.longitude,
        )
        if (distanceToDest <= ARRIVAL_DISTANCE_METERS) {
            pendingTarget = null
            pendingLabel = ""
            _state.value = NavigationState.Arrived(route.destinationLabel)
            return
        }

        // 2. Check if we reached/passed the current maneuver step
        var stepIndex = current.currentStepIndex
        val currentStep = route.steps.getOrNull(stepIndex)
        var distanceToStep = if (currentStep != null) {
            computeDistance(
                location.latitude,
                location.longitude,
                currentStep.latitude,
                currentStep.longitude,
            )
        } else {
            0.0
        }

        // Advance maneuver step if within 35m, or if progressed past the maneuver towards next step along polyline
        if (stepIndex < route.steps.lastIndex) {
            val nextStep = route.steps[stepIndex + 1]
            val distToNext = computeDistance(
                location.latitude,
                location.longitude,
                nextStep.latitude,
                nextStep.longitude,
            )
            val stepSegmentDistance = if (currentStep != null) {
                computeDistance(
                    currentStep.latitude,
                    currentStep.longitude,
                    nextStep.latitude,
                    nextStep.longitude,
                )
            } else {
                Double.MAX_VALUE
            }

            val passedManeuver = if (currentStep != null && stepSegmentDistance > 10.0) {
                val distToSegment = distanceToSegmentMeters(
                    location.latitude,
                    location.longitude,
                    currentStep.latitude,
                    currentStep.longitude,
                    nextStep.latitude,
                    nextStep.longitude,
                )
                // Passed if vehicle is within 45m of the step segment and closer to next step than segment length
                distToSegment <= 45.0 && distToNext < stepSegmentDistance && distToNext < distanceToStep
            } else {
                false
            }

            if (distanceToStep <= STEP_ADVANCE_DISTANCE_METERS || passedManeuver) {
                stepIndex++
                distanceToStep = distToNext
            }
        }

        // 3. Off-route detection (point-to-segment distance to polyline)
        val minDistanceToRoute = minDistanceToPolyline(location, route.coordinates)

        val nowMs = SystemClock.elapsedRealtime()
        if (minDistanceToRoute > OFF_ROUTE_DISTANCE_METERS &&
            nowMs - lastRecalculateMs > RECALCULATE_COOLDOWN_MS
        ) {
            lastRecalculateMs = nowMs
            recalculate(location, route)
            return
        }

        // 4. Update progress and ETA (distance to upcoming maneuver + all remaining steps)
        val remainingStepsDistance = distanceToStep + route.steps
            .drop(stepIndex)
            .sumOf { it.distanceMeters }

        val remainingRatio = if (route.totalDistanceMeters > 0) {
            (remainingStepsDistance / route.totalDistanceMeters).coerceIn(0.0, 2.0)
        } else {
            0.0
        }
        val remainingDuration = route.totalDurationSeconds * remainingRatio
        val nowClock = System.currentTimeMillis()
        val eta = nowClock + (remainingDuration * 1000).toLong()

        _state.value = current.copy(
            currentStepIndex = stepIndex,
            distanceToNextManeuverMeters = distanceToStep,
            remainingDistanceMeters = remainingStepsDistance,
            remainingDurationSeconds = remainingDuration,
            etaTimestampMillis = eta,
        )
    }

    private fun recalculate(
        currentLocation: Location,
        route: NavigationRoute,
    ) {
        currentRouteJob?.cancel()
        currentRouteJob = scope.launch {
            try {
                val newRoute = routingApi.calculateRoute(
                    startLat = currentLocation.latitude,
                    startLon = currentLocation.longitude,
                    destLat = route.destination.latitude,
                    destLon = route.destination.longitude,
                    destinationLabel = route.destinationLabel,
                )
                if (newRoute != null) {
                    val distanceToFirst = newRoute.steps.firstOrNull()?.let {
                        computeDistance(
                            currentLocation.latitude,
                            currentLocation.longitude,
                            it.latitude,
                            it.longitude,
                        )
                    } ?: newRoute.totalDistanceMeters

                    val now = System.currentTimeMillis()
                    val eta = now + (newRoute.totalDurationSeconds * 1000).toLong()

                    _state.value = NavigationState.Active(
                        route = newRoute,
                        currentStepIndex = 0,
                        distanceToNextManeuverMeters = distanceToFirst,
                        remainingDistanceMeters = newRoute.totalDistanceMeters,
                        remainingDurationSeconds = newRoute.totalDurationSeconds,
                        etaTimestampMillis = eta,
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Recalculation error", e)
            }
        }
    }

    fun stopNavigation() {
        currentRouteJob?.cancel()
        currentRouteJob = null
        pendingTarget = null
        pendingLabel = ""
        _state.value = NavigationState.Idle
    }

    fun onNetworkAvailabilityChanged(isOnline: Boolean) {
        if (!isOnline) return
        val current = _state.value
        val target = pendingTarget
        if (current is NavigationState.Error && target != null) {
            Log.d(TAG, "Network restored, auto-resuming navigation to $pendingLabel")
            startNavigation(lastKnownLocation, target, pendingLabel)
        } else if (current is NavigationState.Active) {
            lastKnownLocation?.let { loc ->
                val minDistance = minDistanceToPolyline(loc, current.route.coordinates)
                if (minDistance > OFF_ROUTE_DISTANCE_METERS) {
                    Log.d(TAG, "Network restored while off-route, recalculating route")
                    recalculate(loc, current.route)
                }
            }
        }
    }

    private fun computeDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble()
    }

    private fun minDistanceToPolyline(
        location: Location,
        coordinates: List<Pair<Double, Double>>,
    ): Double {
        if (coordinates.isEmpty()) return Double.MAX_VALUE
        if (coordinates.size == 1) {
            return computeDistance(location.latitude, location.longitude, coordinates[0].first, coordinates[0].second)
        }
        var minDistance = Double.MAX_VALUE
        for (i in 0 until coordinates.size - 1) {
            val a = coordinates[i]
            val b = coordinates[i + 1]
            val dist = distanceToSegmentMeters(
                location.latitude,
                location.longitude,
                a.first,
                a.second,
                b.first,
                b.second,
            )
            if (dist < minDistance) {
                minDistance = dist
            }
        }
        return minDistance
    }

    private fun distanceToSegmentMeters(
        pLat: Double,
        pLon: Double,
        aLat: Double,
        aLon: Double,
        bLat: Double,
        bLon: Double,
    ): Double {
        val cosLat = Math.cos(Math.toRadians((aLat + bLat) / 2.0))
        val dx = (bLon - aLon) * cosLat
        val dy = bLat - aLat
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-12) {
            return computeDistance(pLat, pLon, aLat, aLon)
        }
        val t = (((pLon - aLon) * cosLat * dx + (pLat - aLat) * dy) / lenSq).coerceIn(0.0, 1.0)
        val projLat = aLat + t * (bLat - aLat)
        val projLon = aLon + t * (bLon - aLon)
        return computeDistance(pLat, pLon, projLat, projLon)
    }
}
