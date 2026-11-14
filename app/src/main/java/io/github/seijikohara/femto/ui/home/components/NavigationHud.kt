package io.github.seijikohara.femto.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import io.github.seijikohara.femto.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.ArrowUpLeft
import com.composables.icons.lucide.ArrowUpRight
import com.composables.icons.lucide.CornerUpLeft
import com.composables.icons.lucide.CornerUpRight
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Navigation
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.X
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.data.navigation.NavigationRoute
import io.github.seijikohara.femto.data.navigation.NavigationState
import io.github.seijikohara.femto.data.navigation.NavigationStep
import io.github.seijikohara.femto.data.places.PlaceTarget
import io.github.seijikohara.femto.ui.theme.FemtoTheme
import io.github.seijikohara.femto.ui.theme.PreviewLightDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compact navigation HUD designed specifically for phone screens (e.g. Realme Narzo 30).
 * Displays ONLY the turn direction arrow and the distance to the next maneuver in a sleek,
 * frosted-glass pill that does not obscure the map. An intuitive stop button allows one-tap cancellation.
 */
@Composable
internal fun NavigationHud(
    navigationState: NavigationState,
    is24Hour: Boolean,
    hazeState: HazeState,
    glassConfig: GlassConfig,
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectRoute: (Int) -> Unit = {},
    onConfirmRoute: () -> Unit = {},
) {
    AnimatedVisibility(
        visible = navigationState !is NavigationState.Idle,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier,
    ) {
        when (navigationState) {
            is NavigationState.Loading -> {
                CompactLoadingPill(
                    hazeState = hazeState,
                    glassConfig = glassConfig,
                    onCancel = onStopNavigation,
                )
            }

            is NavigationState.RouteSelection -> {
                CompactRouteSelectionCard(
                    selection = navigationState,
                    hazeState = hazeState,
                    glassConfig = glassConfig,
                    onSelectRoute = onSelectRoute,
                    onConfirmRoute = onConfirmRoute,
                    onCancel = onStopNavigation,
                )
            }

            is NavigationState.Active -> {
                CompactNavigationPill(
                    active = navigationState,
                    is24Hour = is24Hour,
                    hazeState = hazeState,
                    glassConfig = glassConfig,
                    onStop = onStopNavigation,
                )
            }

            is NavigationState.Arrived -> {
                CompactArrivedPill(
                    hazeState = hazeState,
                    glassConfig = glassConfig,
                    onDismiss = onStopNavigation,
                )
            }

            is NavigationState.Error -> {
                CompactErrorPill(
                    message = navigationState.message,
                    hazeState = hazeState,
                    glassConfig = glassConfig,
                    onDismiss = onStopNavigation,
                )
            }

            is NavigationState.Idle -> {
                Unit
            }
        }
    }
}

@Composable
private fun CompactNavigationPill(
    active: NavigationState.Active,
    is24Hour: Boolean,
    hazeState: HazeState,
    glassConfig: GlassConfig,
    onStop: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val step = active.currentStep
    val icon = maneuverIcon(step?.maneuverType, step?.maneuverModifier)
    val distanceStr = formatDistance(active.distanceToNextManeuverMeters)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Main compact pill: ONLY turn arrow + distance + stop button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .glassChrome(
                        shape = RoundedCornerShape(24.dp),
                        hazeState = hazeState,
                        glassConfig = glassConfig,
                    ).clickable(role = Role.Button, onClick = { expanded = !expanded })
                    .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        ) {
            // Maneuver icon circle
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Distance to turn: bold, prominent, easy to read at a glance
            Text(
                text = distanceStr,
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Vertical divider
            Box(
                modifier =
                    Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Convenient cancel button (large hit area, safe to tap while driving)
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.nav_hud_end_route),
                            onClick = onStop,
                        ),
            ) {
                Icon(
                    imageVector = Lucide.X,
                    contentDescription = stringResource(R.string.nav_hud_cancel_navigation),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Expandable mini trip summary: drops down directly below the pill on tap
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .padding(top = 4.dp)
                        .glassChrome(
                            shape = RoundedCornerShape(16.dp),
                            hazeState = hazeState,
                            glassConfig = glassConfig,
                        ).padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.nav_hud_remaining_prefix, formatDistance(active.remainingDistanceMeters)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = formatDuration(active.remainingDurationSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = stringResource(R.string.nav_hud_eta_prefix, formatEta(active.etaTimestampMillis, is24Hour)),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CompactLoadingPill(
    hazeState: HazeState,
    glassConfig: GlassConfig,
    onCancel: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .glassChrome(
                    shape = RoundedCornerShape(24.dp),
                    hazeState = hazeState,
                    glassConfig = glassConfig,
                ).padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.nav_hud_calculating),
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier =
                Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f))
                    .clickable(role = Role.Button, onClick = onCancel),
        ) {
            Icon(
                imageVector = Lucide.X,
                contentDescription = stringResource(R.string.nav_hud_cancel_navigation),
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CompactArrivedPill(
    hazeState: HazeState,
    glassConfig: GlassConfig,
    onDismiss: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .glassChrome(
                    shape = RoundedCornerShape(24.dp),
                    hazeState = hazeState,
                    glassConfig = glassConfig,
                ).padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Icon(
            imageVector = Lucide.Flag,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.nav_hud_arrived),
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier =
                Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(role = Role.Button, onClick = onDismiss),
        ) {
            Icon(
                imageVector = Lucide.X,
                contentDescription = stringResource(R.string.quick_settings_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CompactErrorPill(
    message: String,
    hazeState: HazeState,
    glassConfig: GlassConfig,
    onDismiss: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .glassChrome(
                    shape = RoundedCornerShape(24.dp),
                    hazeState = hazeState,
                    glassConfig = glassConfig,
                ).padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Icon(
            imageVector = Lucide.MapPin,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message.ifBlank { stringResource(R.string.nav_hud_route_error) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable(role = Role.Button, onClick = onDismiss),
        ) {
            Icon(
                imageVector = Lucide.X,
                contentDescription = stringResource(R.string.quick_settings_close),
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun maneuverIcon(
    type: String?,
    modifier: String?,
): ImageVector {
    val cleanType = type?.lowercase(Locale.ROOT).orEmpty()
    val cleanMod = modifier?.lowercase(Locale.ROOT).orEmpty()

    if (cleanType == "arrive") return Lucide.MapPin
    if (cleanType in listOf("roundabout", "rotary")) return Lucide.RotateCw
    if (cleanMod == "uturn") return Lucide.RotateCcw

    return when (cleanMod) {
        "left" -> {
            Lucide.CornerUpLeft
        }

        "right" -> {
            Lucide.CornerUpRight
        }

        "slight left" -> {
            Lucide.ArrowUpLeft
        }

        "slight right" -> {
            Lucide.ArrowUpRight
        }

        "sharp left" -> {
            Lucide.ArrowLeft
        }

        "sharp right" -> {
            Lucide.ArrowRight
        }

        "straight" -> {
            Lucide.ArrowUp
        }

        else -> {
            when (cleanType) {
                "turn" -> Lucide.CornerUpRight
                "depart" -> Lucide.ArrowUp
                "continue" -> Lucide.ArrowUp
                "end of road" -> Lucide.CornerUpRight
                "fork" -> Lucide.ArrowUpRight
                else -> Lucide.ArrowUp
            }
        }
    }
}

@Composable
private fun formatDistance(meters: Double): String =
    when {
        meters < 50 -> stringResource(R.string.nav_hud_distance_now)
        meters < 995 -> stringResource(R.string.nav_hud_distance_meters, ((meters + 5) / 10).toInt() * 10)
        meters < 10000 -> stringResource(R.string.nav_hud_distance_kilometers, meters / 1000.0)
        else -> stringResource(R.string.nav_hud_distance_kilometers_int, (meters / 1000.0).toInt())
    }

@Composable
private fun formatDuration(seconds: Double): String {
    val totalMinutes = (seconds / 60.0).toInt()
    if (totalMinutes < 1) return stringResource(R.string.nav_hud_duration_less_minute)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.nav_hud_duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.nav_hud_duration_minutes, minutes)
    }
}

private fun formatEta(
    timestampMillis: Long,
    is24Hour: Boolean,
): String {
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    val formatter = SimpleDateFormat(pattern, Locale.getDefault())
    return formatter.format(Date(timestampMillis))
}

@Composable
private fun CompactRouteSelectionCard(
    selection: NavigationState.RouteSelection,
    hazeState: HazeState,
    glassConfig: GlassConfig,
    onSelectRoute: (Int) -> Unit,
    onConfirmRoute: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        modifier = modifier.glassChrome(RoundedCornerShape(20.dp), hazeState, glassConfig),
        shape = RoundedCornerShape(20.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(14.dp).widthIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Lucide.Navigation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.nav_hud_select_route),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = selection.destinationLabel,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Lucide.X,
                        contentDescription = stringResource(R.string.nav_hud_cancel_navigation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Route options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val fastestDuration = selection.routes.minOf { it.totalDurationSeconds }
                selection.routes.forEachIndexed { index, route ->
                    val isSelected = index == selection.selectedIndex
                    val durationMin = (route.totalDurationSeconds / 60).toInt().coerceAtLeast(1)
                    val distanceKm = route.totalDistanceMeters / 1000.0
                    val diffMin = ((route.totalDurationSeconds - fastestDuration) / 60).toInt()

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.40f)
                                },
                            ).border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
                                },
                                shape = RoundedCornerShape(14.dp),
                            ).clickable { onSelectRoute(index) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.nav_hud_duration_minutes, durationMin),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color =
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                )
                                if (diffMin > 0) {
                                    Text(
                                        text = "+$diffMin",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.nav_hud_distance_kilometers, distanceKm),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (route.summary.isNotBlank()) {
                                Text(
                                    text = route.summary,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                }
            }

            // Confirm Button
            Button(
                onClick = onConfirmRoute,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Lucide.Navigation,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.nav_hud_start),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun NavigationHudCompactPreview() {
    FemtoTheme {
        val sampleRoute =
            NavigationRoute(
                totalDistanceMeters = 3400.0,
                totalDurationSeconds = 480.0,
                routeGeoJson = "",
                coordinates = listOf(Pair(55.75, 37.61), Pair(55.76, 37.62)),
                steps =
                    listOf(
                        NavigationStep(
                            instruction = "Поверните направо на Тверскую ул.",
                            streetName = "Тверская ул.",
                            maneuverType = "turn",
                            maneuverModifier = "right",
                            distanceMeters = 350.0,
                            durationSeconds = 45.0,
                            latitude = 55.755,
                            longitude = 37.615,
                        ),
                    ),
                destination = PlaceTarget.Point(55.76, 37.62),
                destinationLabel = "Красная Площадь",
            )
        val activeState =
            NavigationState.Active(
                route = sampleRoute,
                currentStepIndex = 0,
                distanceToNextManeuverMeters = 350.0,
                remainingDistanceMeters = 3400.0,
                remainingDurationSeconds = 480.0,
                etaTimestampMillis = System.currentTimeMillis() + 480_000L,
            )

        NavigationHud(
            navigationState = activeState,
            is24Hour = true,
            hazeState = rememberHazeState(),
            glassConfig = GlassConfig(),
            onStopNavigation = {},
        )
    }
}
