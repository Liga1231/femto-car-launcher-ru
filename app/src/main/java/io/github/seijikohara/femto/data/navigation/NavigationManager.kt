package io.github.seijikohara.femto.data.navigation

import android.location.Location
import android.os.SystemClock
import android.util.Log
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.places.SearchHistoryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "NavigationManager"
private const val ARRIVAL_DISTANCE_METERS = 50.0
private const val STEP_ADVANCE_DISTANCE_METERS = 35.0
private const val OFF_ROUTE_DISTANCE_METERS = 75.0
private const val RECALCULATE_COOLDOWN_MS = 8000L

internal class NavigationManager(
    private val routingApi: OsrmRoutingApi,
    private val destinationGeocoder: DestinationGeocoder,
    private val historyStore: SearchHistoryStore? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _state = MutableStateFlow<NavigationState>(NavigationState.Idle)
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    private var currentRouteJob: Job? = null
    private var lastRecalculateMs: Long = 0L
    private var lastKnownLocation: Location? = null
    private var pendingTarget: PlaceTarget? = null
    private var pendingLabel: String = ""
    @Volatile
    private var lastPassedCoordIndex: Int = 0

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
        lastPassedCoordIndex = 0

        historyStore?.let { store ->
            scope.launch(ioDispatcher) {
                store.add(destLabel, target)
            }
        }

        currentRouteJob?.cancel()
        _state.value = NavigationState.Loading(destLabel)

        currentRouteJob = scope.launch(defaultDispatcher) {
            try {
                val destPoint = destinationGeocoder.resolveTarget(target)
                if (destPoint == null) {
                    _state.value = NavigationState.Error("Пункт назначения не найден", destLabel)
                    return@launch
                }

                val isOnline = NetworkMonitor.isOnline()
                val isValhalla = ValhallaNavigationEngine.isAvailable(destinationGeocoder.context)
                val canUseOffline = isValhalla || OfflineNavigationEngine.isOfflineRoutingAvailable()

                Log.d(
                    TAG,
                    "calculateRoutes: isOnline=$isOnline, isValhalla=$isValhalla, canUseOffline=$canUseOffline " +
                        "start=(${startLoc.latitude}, ${startLoc.longitude}) dest=(${destPoint.latitude}, ${destPoint.longitude}) label='$destLabel'"
                )

                val effectiveRoutes = if (isOnline) {
                    // 1. ONLINE FIRST: fast server routing (150-300ms) with multiple route choices
                    val onlineRoutes = runCatching {
                        routingApi.calculateRoutes(
                            startLat = startLoc.latitude,
                            startLon = startLoc.longitude,
                            destLat = destPoint.latitude,
                            destLon = destPoint.longitude,
                            destinationLabel = destLabel,
                        )
                    }.getOrDefault(emptyList())

                    if (onlineRoutes.isNotEmpty()) {
                        Log.d(TAG, "Successfully obtained ${onlineRoutes.size} online routes from server")
                        onlineRoutes
                    } else if (isValhalla) {
                        Log.i(TAG, "Online routes unavailable, falling back to native C++ Valhalla engine")
                        ValhallaNavigationEngine.calculateRoutes(
                            context = destinationGeocoder.context,
                            startLat = startLoc.latitude,
                            startLon = startLoc.longitude,
                            destLat = destPoint.latitude,
                            destLon = destPoint.longitude,
                            destinationLabel = destLabel,
                            numRoutes = 2,
                        )
                    } else if (canUseOffline) {
                        Log.d(TAG, "Online routes unavailable/empty, falling back to offline database")
                        OfflineNavigationEngine.calculateRoutesFromDatabase(
                            startLat = startLoc.latitude,
                            startLon = startLoc.longitude,
                            destLat = destPoint.latitude,
                            destLon = destPoint.longitude,
                            destinationLabel = destLabel,
                            numRoutes = 2,
                        )
                    } else {
                        emptyList()
                    }
                } else if (isValhalla) {
                    // 2. HIGH PERFORMANCE C++ VALHALLA ENGINE
                    Log.i(TAG, "Offline mode: calculating route via native C++ Valhalla engine")
                    val vRoutes = ValhallaNavigationEngine.calculateRoutes(
                        context = destinationGeocoder.context,
                        startLat = startLoc.latitude,
                        startLon = startLoc.longitude,
                        destLat = destPoint.latitude,
                        destLon = destPoint.longitude,
                        destinationLabel = destLabel,
                        numRoutes = 2,
                    )
                    if (vRoutes.isNotEmpty()) {
                        vRoutes
                    } else if (canUseOffline) {
                        Log.w(TAG, "Valhalla engine returned empty routes for ($destLabel), falling back to SQLite")
                        OfflineNavigationEngine.calculateRoutesFromDatabase(
                            startLat = startLoc.latitude,
                            startLon = startLoc.longitude,
                            destLat = destPoint.latitude,
                            destLon = destPoint.longitude,
                            destinationLabel = destLabel,
                            numRoutes = 2,
                        )
                    } else {
                        emptyList()
                    }
                } else if (canUseOffline) {
                    // 3. OFFLINE FALLBACK: calculate route locally via SQLite
                    Log.d(TAG, "Offline mode: calculating route via local SQLite engine")
                    OfflineNavigationEngine.calculateRoutesFromDatabase(
                        startLat = startLoc.latitude,
                        startLon = startLoc.longitude,
                        destLat = destPoint.latitude,
                        destLon = destPoint.longitude,
                        destinationLabel = destLabel,
                        numRoutes = 2,
                    )
                } else {
                    emptyList()
                }

                ensureActive()
                if (effectiveRoutes.isEmpty()) {
                    _state.value = NavigationState.Error("Маршрут не найден или нет подключения к сети", destLabel)
                    return@launch
                }

                ensureActive()
                // Show route preview with alternatives on map & HUD!
                _state.value = NavigationState.RouteSelection(
                    routes = effectiveRoutes,
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

        // 1. Check arrival at final destination or end of route polyline
        val distanceToDest = computeDistance(
            location.latitude,
            location.longitude,
            route.destination.latitude,
            route.destination.longitude,
        )
        val lastCoord = route.coordinates.lastOrNull()
        val distanceToLastCoord = if (lastCoord != null) {
            computeDistance(
                location.latitude,
                location.longitude,
                lastCoord.first,
                lastCoord.second,
            )
        } else {
            Double.MAX_VALUE
        }
        if (distanceToDest <= ARRIVAL_DISTANCE_METERS || distanceToLastCoord <= ARRIVAL_DISTANCE_METERS) {
            pendingTarget = null
            pendingLabel = ""
            lastPassedCoordIndex = 0
            _state.value = NavigationState.Arrived(route.destinationLabel)
            return
        }

        // 2. Track vehicle progress along route polyline and slice coordinates behind vehicle
        val coordinates = route.coordinates
        val remainingGeoJson = if (coordinates.size >= 2) {
            val searchStart = maxOf(0, lastPassedCoordIndex - 1)
            val searchEnd = minOf(coordinates.size - 2, lastPassedCoordIndex + 25)
            var bestIndex = lastPassedCoordIndex
            var minDistance = Double.MAX_VALUE
            var bestProjLat = location.latitude
            var bestProjLon = location.longitude

            for (i in searchStart..searchEnd) {
                val a = coordinates[i]
                val b = coordinates[i + 1]
                val proj = projectToSegment(
                    location.latitude,
                    location.longitude,
                    a.first,
                    a.second,
                    b.first,
                    b.second,
                )
                if (proj.distanceMeters < minDistance) {
                    minDistance = proj.distanceMeters
                    bestIndex = i
                    bestProjLat = proj.projLat
                    bestProjLon = proj.projLon
                }
            }

            if (bestIndex > lastPassedCoordIndex && minDistance <= OFF_ROUTE_DISTANCE_METERS) {
                lastPassedCoordIndex = bestIndex
            }

            val remainingCoords = ArrayList<Pair<Double, Double>>(coordinates.size - bestIndex + 1)
            remainingCoords.add(Pair(bestProjLat, bestProjLon))
            for (i in (bestIndex + 1) until coordinates.size) {
                remainingCoords.add(coordinates[i])
            }

            if (remainingCoords.size >= 2) {
                buildGeoJsonLineString(remainingCoords)
            } else {
                route.routeGeoJson
            }
        } else {
            route.routeGeoJson
        }

        // 3. Check if we reached/passed the current maneuver step
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

        // 4. Off-route detection (point-to-segment distance to remaining route)
        val remainingCoordsForCheck = if (lastPassedCoordIndex < route.coordinates.size) {
            route.coordinates.drop(lastPassedCoordIndex)
        } else {
            route.coordinates
        }
        val minDistanceToRoute = minDistanceToPolyline(location, remainingCoordsForCheck)

        val nowMs = SystemClock.elapsedRealtime()
        if (minDistanceToRoute > OFF_ROUTE_DISTANCE_METERS &&
            nowMs - lastRecalculateMs > RECALCULATE_COOLDOWN_MS
        ) {
            lastRecalculateMs = nowMs
            recalculate(location, route)
            return
        }

        // 5. Update progress and ETA (distance to upcoming maneuver + all remaining steps)
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
            remainingRouteGeoJson = remainingGeoJson,
        )
    }

    private fun recalculate(
        currentLocation: Location,
        route: NavigationRoute,
    ) {
        currentRouteJob?.cancel()
        currentRouteJob = scope.launch(defaultDispatcher) {
            try {
                val isOnline = NetworkMonitor.isOnline()
                val isValhalla = ValhallaNavigationEngine.isAvailable(destinationGeocoder.context)
                val canUseOffline = isValhalla || OfflineNavigationEngine.isOfflineRoutingAvailable()

                val newRoute = if (isOnline) {
                    val online = runCatching {
                        routingApi.calculateRoute(
                            startLat = currentLocation.latitude,
                            startLon = currentLocation.longitude,
                            destLat = route.destination.latitude,
                            destLon = route.destination.longitude,
                            destinationLabel = route.destinationLabel,
                        )
                    }.getOrNull()
                    online ?: if (isValhalla) {
                        ValhallaNavigationEngine.calculateRoutes(
                            context = destinationGeocoder.context,
                            startLat = currentLocation.latitude,
                            startLon = currentLocation.longitude,
                            destLat = route.destination.latitude,
                            destLon = route.destination.longitude,
                            destinationLabel = route.destinationLabel,
                            numRoutes = 1,
                        ).firstOrNull()
                    } else if (canUseOffline) {
                        OfflineNavigationEngine.calculateRouteFromDatabase(
                            startLat = currentLocation.latitude,
                            startLon = currentLocation.longitude,
                            destLat = route.destination.latitude,
                            destLon = route.destination.longitude,
                            destinationLabel = route.destinationLabel,
                        )
                    } else null
                } else if (isValhalla) {
                    ValhallaNavigationEngine.calculateRoutes(
                        context = destinationGeocoder.context,
                        startLat = currentLocation.latitude,
                        startLon = currentLocation.longitude,
                        destLat = route.destination.latitude,
                        destLon = route.destination.longitude,
                        destinationLabel = route.destinationLabel,
                        numRoutes = 1,
                    ).firstOrNull() ?: if (canUseOffline) {
                        OfflineNavigationEngine.calculateRouteFromDatabase(
                            startLat = currentLocation.latitude,
                            startLon = currentLocation.longitude,
                            destLat = route.destination.latitude,
                            destLon = route.destination.longitude,
                            destinationLabel = route.destinationLabel,
                        )
                    } else null
                } else if (canUseOffline) {
                    OfflineNavigationEngine.calculateRouteFromDatabase(
                        startLat = currentLocation.latitude,
                        startLon = currentLocation.longitude,
                        destLat = route.destination.latitude,
                        destLon = route.destination.longitude,
                        destinationLabel = route.destinationLabel,
                    )
                } else null
                ensureActive()
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
                    lastPassedCoordIndex = 0

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
        lastPassedCoordIndex = 0
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

    private data class ProjectionResult(
        val distanceMeters: Double,
        val projLat: Double,
        val projLon: Double,
        val t: Double,
    )

    private fun projectToSegment(
        pLat: Double,
        pLon: Double,
        aLat: Double,
        aLon: Double,
        bLat: Double,
        bLon: Double,
    ): ProjectionResult {
        val cosLat = Math.cos(Math.toRadians((aLat + bLat) / 2.0))
        val dx = (bLon - aLon) * cosLat
        val dy = bLat - aLat
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-12) {
            val dist = computeDistance(pLat, pLon, aLat, aLon)
            return ProjectionResult(dist, aLat, aLon, 0.0)
        }
        val t = (((pLon - aLon) * cosLat * dx + (pLat - aLat) * dy) / lenSq).coerceIn(0.0, 1.0)
        val projLat = aLat + t * (bLat - aLat)
        val projLon = aLon + t * (bLon - aLon)
        val dist = computeDistance(pLat, pLon, projLat, projLon)
        return ProjectionResult(dist, projLat, projLon, t)
    }

    private fun distanceToSegmentMeters(
        pLat: Double,
        pLon: Double,
        aLat: Double,
        aLon: Double,
        bLat: Double,
        bLon: Double,
    ): Double = projectToSegment(pLat, pLon, aLat, aLon, bLat, bLon).distanceMeters

    private fun buildGeoJsonLineString(coords: List<Pair<Double, Double>>): String {
        val sb = StringBuilder(coords.size * 32 + 128)
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
        for (i in coords.indices) {
            if (i > 0) sb.append(',')
            val pt = coords[i]
            sb.append('[').append(pt.second).append(',').append(pt.first).append(']')
        }
        sb.append("]},\"properties\":{\"selected\":true,\"routeIndex\":0}}]}")
        return sb.toString()
    }
}
