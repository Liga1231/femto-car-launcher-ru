package io.github.seijikohara.femto.ui.destination

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
import io.github.seijikohara.femto.data.common.femtoUserAgent
import io.github.seijikohara.femto.data.location.LocationGraph
import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.navigation.AddressSuggestion
import io.github.seijikohara.femto.data.navigation.DestinationGeocoder
import io.github.seijikohara.femto.data.navigation.NetworkMonitor
import io.github.seijikohara.femto.data.navigation.OfflineNavigationEngine
import io.github.seijikohara.femto.data.places.HistoryItem
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.places.SavedPlacesPreferences
import io.github.seijikohara.femto.data.places.SavedPlacesStore
import io.github.seijikohara.femto.data.places.SearchHistoryPreferences
import io.github.seijikohara.femto.data.places.SearchHistoryStore
import io.github.seijikohara.femto.data.voice.SpeechInput
import io.github.seijikohara.femto.data.voice.VoiceRecognizer
import io.github.seijikohara.femto.data.voice.VoiceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.util.Locale

private object NoOpSearchHistoryStore : SearchHistoryStore {
    override val history: Flow<List<HistoryItem>> = emptyFlow()
    override suspend fun add(label: String, target: PlaceTarget) {}
    override suspend fun delete(id: Long) {}
    override suspend fun clear() {}
}

/**
 * Drives the destination panel: the query (typed, or dictated through
 * [speech]), the saved places in [placesStore], search history in [historyStore],
 * and the motion gate from [motionFlow] (see [DestinationUiState.typingAllowed]).
 *
 * The gate lives here rather than only in the UI, so a stale tap or a
 * keyboard that stays up as the vehicle pulls away cannot type, save or
 * delete: every edit that involves typing is dropped while moving. A voice
 * result fills the query in any motion state but never hands off by itself;
 * the user still taps Navigate. The hand-off is not an action here: the
 * panel raises it to the dashboard, whose ViewModel owns the launch events.
 */
internal class DestinationViewModel(
    private val placesStore: SavedPlacesStore,
    motionFlow: Flow<VehicleMotion>,
    private val speech: SpeechInput,
    private val destinationGeocoder: DestinationGeocoder? = null,
    locationFlow: Flow<Location?> = emptyFlow(),
    private val historyStore: SearchHistoryStore = NoOpSearchHistoryStore,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val suggestions = MutableStateFlow<List<AddressSuggestion>>(emptyList())
    private val isSearchingSuggestions = MutableStateFlow(false)

    private data class SearchState(
        val query: String,
        val suggestions: List<AddressSuggestion>,
        val isSearching: Boolean,
    )

    private val searchFlow: Flow<SearchState> =
        combine(query, suggestions, isSearchingSuggestions) { q, s, searching ->
            SearchState(q, s, searching)
        }

    val uiState: StateFlow<DestinationUiState> =
        combine(
            searchFlow,
            speech.state,
            placesStore.places,
            historyStore.history,
            // Seeded so the panel never waits on a location stack that has not
            // spoken; UNKNOWN fails open (see DestinationUiState.typingAllowed).
            motionFlow.onStart { emit(VehicleMotion.UNKNOWN) }.distinctUntilChanged(),
        ) { search, voice, places, history, motion ->
            DestinationUiState(
                query = search.query,
                voice = voice,
                places = places,
                motion = motion,
                suggestions = search.suggestions,
                isSearchingSuggestions = search.isSearching,
                history = history,
            )
        }.stateIn(viewModelScope, WhileUiSubscribed, DestinationUiState.Initial)

    private var lastKnownLocation: Location? = null

    init {
        // Continuously cache last known location fix for instant nearest-store searches
        viewModelScope.launch {
            locationFlow.collect { loc ->
                if (loc != null) {
                    lastKnownLocation = loc
                    OfflineNavigationEngine.updateUserLocation(loc.latitude, loc.longitude)
                }
            }
        }

        // A final transcript becomes the query, and the recognizer goes back to
        // Idle so the same result is not applied twice and the mic is ready
        // for another try. Partial transcripts only show in the voice line.
        viewModelScope.launch {
            speech.state.collect { state ->
                if (state is VoiceState.Result) {
                    query.value = state.text
                    speech.reset()
                }
            }
        }

        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            query
                .debounce(400)
                .distinctUntilChanged()
                .collectLatest { text ->
                    val trimmed = text.trim()
                    if (trimmed.length < 2 || destinationGeocoder == null) {
                        suggestions.value = emptyList()
                        isSearchingSuggestions.value = false
                    } else {
                        isSearchingSuggestions.value = true
                        try {
                            val lastLocation = lastKnownLocation ?: withTimeoutOrNull(100) { locationFlow.firstOrNull() }
                            val results = destinationGeocoder.searchSuggestions(trimmed, lastLocation)
                            suggestions.value = results
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            suggestions.value = emptyList()
                        } finally {
                            isSearchingSuggestions.value = false
                        }
                    }
                }
        }
    }

    fun onAction(action: DestinationAction) {
        val typingAllowed = uiState.value.typingAllowed
        when (action) {
            is DestinationAction.QueryChanged -> {
                if (typingAllowed) query.value = action.text
            }

            DestinationAction.StartListening -> {
                // Some engines silently ignore the next start after an error
                // unless the recognizer is cancelled first (VoiceRecognizer.reset).
                if (speech.state.value is VoiceState.Failed) speech.reset()
                speech.start()
            }

            DestinationAction.StopListening -> {
                speech.stop()
            }

            DestinationAction.CancelListening -> {
                // reset() cancels the engine; stop() would still deliver a
                // final result that could refill the query after a hand-off.
                speech.reset()
            }

            DestinationAction.SaveQuery -> {
                query.value
                    .trim()
                    .takeIf { typingAllowed && it.isNotEmpty() }
                    ?.let { text -> save(label = text, target = PlaceTarget.Query(text)) }
            }

            is DestinationAction.SaveCurrentLocation -> {
                if (typingAllowed) {
                    val label = action.address.trim().ifEmpty { action.point.coordinateLabel() }
                    save(label = label, target = action.point)
                }
            }

            is DestinationAction.DeletePlace -> {
                if (typingAllowed) viewModelScope.launch { placesStore.delete(action.id) }
            }

            is DestinationAction.DeleteHistory -> {
                if (typingAllowed) viewModelScope.launch { historyStore.delete(action.id) }
            }

            DestinationAction.ClearHistory -> {
                if (typingAllowed) viewModelScope.launch { historyStore.clear() }
            }

            DestinationAction.ClearQuery -> {
                query.value = ""
                suggestions.value = emptyList()
                isSearchingSuggestions.value = false
            }
        }
    }

    fun recordDestination(
        label: String,
        target: PlaceTarget,
    ) {
        viewModelScope.launch {
            historyStore.add(label, target)
        }
    }

    // viewModelScope outlives a panel collapsed right after the tap, so the
    // write always lands.
    private fun save(
        label: String,
        target: PlaceTarget,
    ) {
        viewModelScope.launch { placesStore.add(label, target) }
    }

    override fun onCleared() {
        speech.destroy()
    }
}

// Five decimals (about 1 m) names a place without an address; Locale.ROOT
// keeps the separator a dot in every locale, matching the hand-off URI.
private fun PlaceTarget.Point.coordinateLabel(): String = String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude)

/**
 * Wires the production store, the shared vehicle-motion verdict (the same
 * [LocationGraph] pipeline the dashboard and the updater read), and
 * the platform recognizer.
 */
internal val DestinationViewModelFactory =
    viewModelFactory {
        initializer {
            val application =
                checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            val locationGraph = LocationGraph.get(application)
            val httpClient =
                OkHttpClient
                    .Builder()
                    .build()
            val geocoder =
                DestinationGeocoder(
                    context = application,
                    client = httpClient,
                    userAgent = femtoUserAgent,
                )
            DestinationViewModel(
                placesStore = SavedPlacesPreferences(application),
                motionFlow = locationGraph.vehicleMotion(),
                speech = VoiceRecognizer(application),
                destinationGeocoder = geocoder,
                locationFlow = locationGraph.locationFlow(),
                historyStore = SearchHistoryPreferences(application),
            )
        }
    }
