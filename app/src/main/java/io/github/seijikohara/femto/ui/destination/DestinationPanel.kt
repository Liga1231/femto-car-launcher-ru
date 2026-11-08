package io.github.seijikohara.femto.ui.destination

import android.Manifest
import android.text.format.DateUtils
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Navigation
import com.composables.icons.lucide.NavigationOff
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.common.hasRecordAudioPermission
import io.github.seijikohara.femto.data.location.VehicleMotion
import io.github.seijikohara.femto.data.navigation.AddressSuggestion
import io.github.seijikohara.femto.data.navigation.OfflineNavigationEngine
import io.github.seijikohara.femto.data.places.HistoryItem
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.data.places.SavedPlace
import io.github.seijikohara.femto.data.voice.VoiceState
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.MaximizePanel
import io.github.seijikohara.femto.ui.home.components.PanelIconButton
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.LocalFemtoDarkTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import io.github.seijikohara.femto.ui.theme.PreviewTextStress
import io.github.seijikohara.femto.ui.theme.drawerBody
import io.github.seijikohara.femto.ui.theme.eyebrow

internal const val DESTINATION_QUERY_TEST_TAG = "destination-query"

/**
 * The destination panel (issue #389 phase 1), opened by the dock's Navigation
 * button: one glass maximize panel in the calendar / weather family. The
 * header link opens the maps app exactly as the Navigation button used to
 * ([onOpenMaps]); the body sets a destination and hands it to whichever app
 * handles `geo:` ([onNavigate]).
 *
 * Text entry, saving and deleting are off while moving ([DestinationUiState.typingAllowed]):
 * while moving the field and those controls disable, and a line says typing
 * waits for a stop. The mic and the saved places work in every motion state,
 * so a driver can still set a destination without typing.
 *
 * A landscape panel puts the entry beside the saved places; a portrait panel
 * stacks them, the entry above, matching how [io.github.seijikohara.femto.ui.home.components.CalendarPanel]
 * reflows. Pure UI: [DestinationPanelHost] owns the ViewModel.
 */
@Composable
internal fun DestinationPanel(
    uiState: DestinationUiState,
    currentPoint: PlaceTarget.Point?,
    currentAddress: String,
    onAction: (DestinationAction) -> Unit,
    onMicTap: () -> Unit,
    onNavigate: (target: PlaceTarget, label: String) -> Unit,
    onOpenMaps: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    isOnline: Boolean = true,
    canUseOfflineNav: Boolean = false,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) = MaximizePanel(
    title = stringResource(R.string.destination_title),
    onClose = onClose,
    onOpenExternal = onOpenMaps,
    openExternalLabel = stringResource(R.string.destination_open_maps),
    modifier = modifier,
    hazeState = hazeState,
    glassConfig = glassConfig,
) {
    // Closing the panel (or the panel leaving composition any other way)
    // cancels a mic still listening: nothing on screen would show the
    // microphone open, and a late result must not refill the next open.
    val currentOnAction by rememberUpdatedState(onAction)
    DisposableEffect(Unit) {
        onDispose { currentOnAction(DestinationAction.CancelListening) }
    }
    // Every hand-off cancels recognition first: the launch can pause the
    // activity before the panel's exit animation disposes it.
    val handOff: (PlaceTarget, String) -> Unit = { target, label ->
        onAction(DestinationAction.CancelListening)
        onNavigate(target, label)
    }
    // Read off the BoxWithConstraints receiver here: inside the layouts below
    // the outer receiver is out of reach (see CalendarPanel).
    val portrait = maxHeight > maxWidth
    val isSearchEnabled = isOnline || canUseOfflineNav || OfflineNavigationEngine.isOfflineDataAvailable()
    val entry: @Composable (Modifier) -> Unit = { entryModifier ->
        DestinationEntry(
            uiState = uiState,
            currentPoint = currentPoint,
            currentAddress = currentAddress,
            isOnline = isOnline,
            canUseOfflineNav = canUseOfflineNav,
            onAction = onAction,
            onMicTap = onMicTap,
            onNavigate = handOff,
            modifier = entryModifier,
        )
    }
    val showSuggestions = isSearchEnabled && uiState.query.trim().isNotEmpty() &&
        (uiState.suggestions.isNotEmpty() || uiState.isSearchingSuggestions)
    val rightPane: @Composable (Modifier) -> Unit = { paneModifier ->
        if (!isSearchEnabled && uiState.query.trim().isNotEmpty()) {
            OfflineSearchBlocked(modifier = paneModifier)
        } else if (showSuggestions) {
            AddressSuggestions(
                suggestions = uiState.suggestions,
                isSearching = uiState.isSearchingSuggestions,
                onNavigate = handOff,
                modifier = paneModifier,
            )
        } else {
            SavedPlacesAndHistory(
                places = uiState.places,
                history = uiState.history,
                typingAllowed = uiState.typingAllowed,
                onNavigate = handOff,
                onDeletePlace = { onAction(DestinationAction.DeletePlace(it)) },
                onDeleteHistory = { onAction(DestinationAction.DeleteHistory(it)) },
                onClearHistory = { onAction(DestinationAction.ClearHistory) },
                modifier = paneModifier,
            )
        }
    }
    if (portrait) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(FemtoDimens.ScreenPadding),
        ) {
            entry(Modifier.fillMaxWidth())
            rightPane(Modifier.fillMaxWidth().weight(1f))
        }
    } else {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(FemtoDimens.ScreenPadding),
        ) {
            // The entry scrolls on its own: a short landscape panel (800x480)
            // cannot fit the field, the hint and both button rows at once.
            entry(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()))
            rightPane(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

// The query field with its mic, the motion or voice line, Navigate, and the
// two save buttons.
@Composable
private fun OfflineWarningBanner(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FemtoIcon(
                imageVector = Lucide.NavigationOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = "Нет подключения к интернету. Поиск адресов и навигация доступны только онлайн.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun OfflineSearchBlocked(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            FemtoIcon(
                imageVector = Lucide.NavigationOff,
                contentDescription = null,
                modifier = Modifier.size(54.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "Поиск заблокирован без интернета",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Поиск адресов и расчёт маршрутов работают в онлайн-режиме. Подключите интернет для поиска мест.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DestinationEntry(
    uiState: DestinationUiState,
    currentPoint: PlaceTarget.Point?,
    currentAddress: String,
    isOnline: Boolean,
    onAction: (DestinationAction) -> Unit,
    onMicTap: () -> Unit,
    onNavigate: (target: PlaceTarget, label: String) -> Unit,
    modifier: Modifier = Modifier,
    canUseOfflineNav: Boolean = false,
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(uiState.query, TextRange(uiState.query.length)))
    }

    LaunchedEffect(uiState.query) {
        if (textFieldValue.text != uiState.query) {
            textFieldValue = TextFieldValue(
                text = uiState.query,
                selection = TextRange(uiState.query.length),
            )
        }
    }

    val isSearchEnabled = isOnline || canUseOfflineNav || OfflineNavigationEngine.isOfflineDataAvailable()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
    ) {
        if (!isSearchEnabled) {
            OfflineWarningBanner()
        }
        val query = textFieldValue.text.trim()
        val navigate = { if (isSearchEnabled && query.isNotEmpty()) onNavigate(PlaceTarget.Query(query), "") }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
        ) {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    textFieldValue = newValue
                    onAction(DestinationAction.QueryChanged(newValue.text))
                },
                enabled = isSearchEnabled && uiState.typingAllowed,
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = FemtoDimens.MinTouchTarget)
                        .testTag(DESTINATION_QUERY_TEST_TAG),
                textStyle = MaterialTheme.typography.drawerBody(),
                placeholder = {
                    Text(
                        text = if (!isSearchEnabled) "Поиск заблокирован (нет интернета)" else stringResource(R.string.destination_query_hint),
                        style = MaterialTheme.typography.drawerBody(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    FemtoIcon(
                        imageVector = if (!isSearchEnabled) Lucide.NavigationOff else Lucide.Search,
                        contentDescription = null,
                        tint = if (!isSearchEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                },
                trailingIcon = {
                    if (textFieldValue.text.isNotEmpty() && uiState.typingAllowed) {
                        IconButton(
                            onClick = {
                                textFieldValue = TextFieldValue("", TextRange.Zero)
                                onAction(DestinationAction.QueryChanged(""))
                            },
                        ) {
                            FemtoIcon(
                                imageVector = Lucide.X,
                                contentDescription = "Очистить",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { navigate() }),
            )
            // Hidden where the device has no speech recognizer or offline:
            if (isOnline && uiState.voice != VoiceState.Unavailable) {
                MicButton(listening = uiState.voice is VoiceState.Listening, onClick = onMicTap)
            }
        }
        if (isSearchEnabled) {
            QuickStreetSuggestionsBar(
                query = textFieldValue.text,
                suggestions = uiState.suggestions,
                onSelectStreet = { street ->
                    val newText = "$street, "
                    textFieldValue = TextFieldValue(newText, TextRange(newText.length))
                    onAction(DestinationAction.QueryChanged(newText))
                },
            )
            StatusLine(voice = uiState.voice, typingAllowed = uiState.typingAllowed)
        }
        Button(
            onClick = navigate,
            enabled = isSearchEnabled && query.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = FemtoDimens.MinTouchTarget),
        ) {
            FemtoIcon(
                imageVector = if (!isSearchEnabled) Lucide.NavigationOff else Lucide.Navigation,
                contentDescription = null,
                modifier = Modifier.size(FemtoDimens.InlineIconSize),
            )
            Text(
                text = if (!isSearchEnabled) "Требуется интернет" else stringResource(R.string.destination_navigate),
                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
        ) {
            FilledTonalButton(
                onClick = { onAction(DestinationAction.SaveQuery) },
                enabled = isSearchEnabled && uiState.typingAllowed && query.isNotEmpty(),
                modifier = Modifier.weight(1f).heightIn(min = FemtoDimens.MinTouchTarget),
            ) {
                Text(text = stringResource(R.string.destination_save_query))
            }
            FilledTonalButton(
                onClick = {
                    currentPoint?.let { onAction(DestinationAction.SaveCurrentLocation(it, currentAddress)) }
                },
                enabled = isSearchEnabled && uiState.typingAllowed && currentPoint != null,
                modifier = Modifier.weight(2f).heightIn(min = FemtoDimens.MinTouchTarget),
            ) {
                Text(text = stringResource(R.string.destination_save_location))
            }
        }
    }
}

// One line under the field: the motion gate's explanation while moving, else
// the voice step (live transcript or failure); nothing when idle.
@Composable
private fun StatusLine(
    voice: VoiceState,
    typingAllowed: Boolean,
    modifier: Modifier = Modifier,
) {
    val text =
        when {
            voice is VoiceState.Listening -> {
                listOf(stringResource(R.string.assistant_voice_listening), voice.partial)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            }

            voice is VoiceState.Failed -> {
                stringResource(voice.messageRes)
            }

            !typingAllowed -> {
                stringResource(R.string.destination_moving_hint)
            }

            else -> {
                null
            }
        }
    text?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}

@Composable
private fun MicButton(
    listening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = PanelIconButton(
    icon = Lucide.Mic,
    description = stringResource(R.string.assistant_voice_mic),
    onClick = onClick,
    modifier =
        modifier
            .clip(CircleShape)
            .background(
                if (listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
            ),
    tint = if (listening) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
)

// The saved places in insertion order (newest last). A row tap hands the
// place off in any motion state; the trailing delete shows only while stopped.
@Composable
private fun SavedPlacesAndHistory(
    places: List<SavedPlace>,
    history: List<HistoryItem>,
    typingAllowed: Boolean,
    onNavigate: (target: PlaceTarget, label: String) -> Unit,
    onDeletePlace: (Long) -> Unit,
    onDeleteHistory: (Long) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
    ) {
        if (places.isNotEmpty()) {
            item(key = "header_saved") {
                Text(
                    text = stringResource(R.string.destination_saved_places).uppercase(),
                    style = MaterialTheme.typography.eyebrow(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(places, key = { "place_${it.id}" }) { place ->
                SavedPlaceRow(
                    place = place,
                    showDelete = typingAllowed,
                    onNavigate = { onNavigate(place.target, place.label) },
                    onDelete = { onDeletePlace(place.id) },
                )
            }
            item(key = "divider_history") {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        item(key = "header_history") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.destination_history_title).uppercase(),
                    style = MaterialTheme.typography.eyebrow(),
                    color = MaterialTheme.colorScheme.primary,
                )
                if (history.isNotEmpty() && typingAllowed) {
                    Row(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onClearHistory)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FemtoIcon(
                            imageVector = Lucide.Trash2,
                            contentDescription = stringResource(R.string.destination_clear_history),
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(R.string.destination_clear_history),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }

        if (history.isEmpty()) {
            item(key = "empty_history") {
                Text(
                    text =
                        if (places.isEmpty()) {
                            stringResource(R.string.destination_no_places_or_history)
                        } else {
                            stringResource(R.string.destination_history_empty)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(history, key = { "hist_${it.id}" }) { item ->
                HistoryItemRow(
                    item = item,
                    showDelete = typingAllowed,
                    onNavigate = { onNavigate(item.target, item.label) },
                    onDelete = { onDeleteHistory(item.id) },
                )
            }
        }
    }
}

@Composable
private fun HistoryItemRow(
    item: HistoryItem,
    showDelete: Boolean,
    onNavigate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
) {
    Row(
        modifier =
            Modifier
                .weight(1f)
                .heightIn(min = FemtoDimens.MinTouchTarget)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onNavigate)
                .padding(horizontal = FemtoDimens.CardSectionGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            FemtoIcon(
                imageVector = Lucide.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.drawerBody(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val queryText = (item.target as? PlaceTarget.Query)?.text?.takeIf { it != item.label }
            val timeText =
                runCatching {
                    DateUtils.getRelativeTimeSpanString(
                        item.timestampMs,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString()
                }.getOrNull() ?: "Недавний маршрут"

            Text(
                text = queryText ?: timeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (showDelete) {
        PanelIconButton(
            icon = Lucide.X,
            description = stringResource(R.string.destination_delete_history_item),
            onClick = onDelete,
        )
    }
}

@Composable
private fun SavedPlaceRow(
    place: SavedPlace,
    showDelete: Boolean,
    onNavigate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
) {
    Row(
        modifier =
            Modifier
                .weight(1f)
                .heightIn(min = FemtoDimens.MinTouchTarget)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onNavigate)
                .padding(horizontal = FemtoDimens.CardSectionGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
    ) {
        FemtoIcon(
            imageVector = Lucide.MapPin,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(FemtoDimens.InlineIconSize),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = place.label,
                style = MaterialTheme.typography.drawerBody(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // A query place whose label differs from its query shows the
            // query, so "Office" still says where it goes.
            (place.target as? PlaceTarget.Query)?.text?.takeIf { it != place.label }?.let { query ->
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (showDelete) {
        PanelIconButton(
            icon = Lucide.Trash2,
            description = stringResource(R.string.destination_delete_place, place.label),
            onClick = onDelete,
        )
    }
}

@Composable
private fun AddressSuggestions(
    suggestions: List<AddressSuggestion>,
    isSearching: Boolean,
    onNavigate: (target: PlaceTarget, label: String) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(FemtoDimens.CardSectionGap),
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "ПРЕДЛОЖЕНИЯ АДРЕСОВ",
            style = MaterialTheme.typography.eyebrow(),
            color = MaterialTheme.colorScheme.primary,
        )
        if (isSearching) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    if (suggestions.isEmpty() && !isSearching) {
        Text(
            text = "Адреса не найдены",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(suggestions, key = { index, item -> "${item.point.latitude}_${item.point.longitude}_$index" }) { _, suggestion ->
                SuggestionRow(
                    suggestion = suggestion,
                    onClick = { onNavigate(suggestion.point, suggestion.title) },
                )
            }
        }
    }
}

@Composable
private fun QuickStreetSuggestionsBar(
    query: String,
    suggestions: List<AddressSuggestion>,
    onSelectStreet: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cleanQuery = query.trim().lowercase(Locale.ROOT)
    if (cleanQuery.length < 2) return

    val matchingStreets = remember(suggestions, cleanQuery) {
        val list = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (s in suggestions) {
            val street = s.title.substringBefore(',').trim()
            val streetL = street.lowercase(Locale.ROOT)
            if (streetL != cleanQuery && seen.add(streetL)) {
                list.add(street)
                if (list.size >= 6) break
            }
        }
        list
    }

    if (matchingStreets.isNotEmpty()) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Быстрый выбор улицы:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(matchingStreets) { street ->
                    Surface(
                        onClick = { onSelectStreet(street) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.heightIn(min = 34.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FemtoIcon(
                                imageVector = Lucide.MapPin,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = street,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: AddressSuggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalFemtoDarkTheme.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isDark) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                )
                .then(
                    if (!isDark) {
                        Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            RoundedCornerShape(12.dp),
                        )
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        FemtoIcon(
            imageVector = Lucide.MapPin,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp),
        )
    }

    Spacer(modifier = Modifier.width(12.dp))

    Column(modifier = Modifier.weight(1f)) {
        Text(
            text = suggestion.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (suggestion.subtitle.isNotBlank()) {
            Text(
                text = suggestion.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    Spacer(modifier = Modifier.width(8.dp))

    FemtoIcon(
        imageVector = Lucide.Navigation,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
    )
    }
}

/**
 * Binds [DestinationViewModel] to [DestinationPanel] and owns the
 * RECORD_AUDIO request, the same way the assistant sheet does: the mic asks
 * for the permission on its first tap, never at startup, and a denial leaves
 * the typed and saved paths in place. A hand-off (recognition already
 * cancelled by the panel) clears the query and closes the panel, as the apps
 * panel closes on a launch.
 */
@Composable
internal fun DestinationPanelHost(
    currentPoint: PlaceTarget.Point?,
    currentAddress: String,
    onNavigate: (target: PlaceTarget, label: String) -> Unit,
    onOpenMaps: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    isOnline: Boolean = true,
    canUseOfflineNav: Boolean = false,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) {
    val context = LocalContext.current
    val viewModel: DestinationViewModel = viewModel(factory = DestinationViewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.onAction(DestinationAction.StartListening)
        }
    DestinationPanel(
        uiState = uiState,
        currentPoint = currentPoint,
        currentAddress = currentAddress,
        isOnline = isOnline,
        canUseOfflineNav = canUseOfflineNav,
        onAction = viewModel::onAction,
        onMicTap = {
            when {
                uiState.voice is VoiceState.Listening -> viewModel.onAction(DestinationAction.StopListening)
                context.hasRecordAudioPermission() -> viewModel.onAction(DestinationAction.StartListening)
                else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onNavigate = { target, label ->
            val resolvedLabel =
                label.ifBlank {
                    when (target) {
                        is PlaceTarget.Query -> target.text
                        is PlaceTarget.Point -> "Пункт назначения"
                    }
                }
            viewModel.recordDestination(resolvedLabel, target)
            onNavigate(target, label)
            viewModel.onAction(DestinationAction.ClearQuery)
            onClose()
        },
        onOpenMaps = onOpenMaps,
        onClose = onClose,
        modifier = modifier,
        hazeState = hazeState,
        glassConfig = glassConfig,
    )
}

@PreviewLightDark
@PreviewTextStress
@Composable
private fun DestinationPanelStoppedPreview() {
    FemtoTheme {
        DestinationPanel(
            uiState = PreviewState.copy(motion = VehicleMotion.PARKED, query = "Central Station"),
            currentPoint = PlaceTarget.Point(35.681236, 139.767125),
            currentAddress = "1-9-1 Marunouchi",
            onAction = {},
            onMicTap = {},
            onNavigate = { _, _ -> },
            onOpenMaps = {},
            onClose = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@PreviewLightDark
@Composable
private fun DestinationPanelMovingPreview() {
    FemtoTheme {
        DestinationPanel(
            uiState = PreviewState,
            currentPoint = PlaceTarget.Point(35.681236, 139.767125),
            currentAddress = "1-9-1 Marunouchi",
            onAction = {},
            onMicTap = {},
            onNavigate = { _, _ -> },
            onOpenMaps = {},
            onClose = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private val PreviewState =
    DestinationUiState(
        query = "",
        voice = VoiceState.Idle,
        places =
            listOf(
                SavedPlace(1L, "Office", PlaceTarget.Query("1st & Pike, Seattle")),
                SavedPlace(2L, "Home", PlaceTarget.Point(35.681236, 139.767125)),
            ),
        history =
            listOf(
                HistoryItem(1L, "Central Mall", PlaceTarget.Query("Central Mall")),
            ),
        motion = VehicleMotion.MOVING,
    )
