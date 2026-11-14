package io.github.seijikohara.femto.ui.home.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import io.github.seijikohara.femto.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Sun
import dev.chrisbanes.haze.HazeState
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.data.system.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun HorizontalBrightnessSlider(
    hazeState: HazeState,
    glassConfig: GlassConfig,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var brightness by remember {
        val initial =
            try {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
            } catch (_: Exception) {
                0.7f
            }
        mutableFloatStateOf(initial.coerceIn(0.05f, 1f))
    }

    var brightnessSyncJob by remember { androidx.compose.runtime.mutableStateOf<Job?>(null) }

    fun applyBrightness(newFraction: Float, debounce: Boolean = true) {
        val clamped = newFraction.coerceIn(0.05f, 1f)
        brightness = clamped

        context.findActivity()?.window?.let { window ->
            val lp = window.attributes
            lp.screenBrightness = clamped
            window.attributes = lp
        }

        brightnessSyncJob?.cancel()
        brightnessSyncJob = scope.launch(Dispatchers.IO) {
            if (debounce) {
                delay(150L)
            }
            val brightnessInt = (clamped * 255).roundToInt().coerceIn(10, 255)
            try {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                )
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightnessInt,
                )
            } catch (_: Exception) {
                RootShell.run("settings put system screen_brightness $brightnessInt")
            }
        }
    }

    var trackWidthPx by remember { mutableFloatStateOf(1f) }

    Surface(
        modifier =
            modifier
                .height(52.dp)
                .glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig),
        color = Color.Transparent,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable {
                            val next =
                                when {
                                    brightness < 0.35f -> 0.60f
                                    brightness < 0.80f -> 1.0f
                                    else -> 0.25f
                                }
                            applyBrightness(next, debounce = false)
                            onInteraction()
                        },
                contentAlignment = Alignment.Center,
            ) {
                FemtoIcon(
                    imageVector = Lucide.Sun,
                    contentDescription = stringResource(R.string.quick_settings_brightness),
                    tint =
                        if (brightness > 0.5f) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.size(20.dp),
                )
            }

            // Wide touch target area (38.dp height) hosting the 14.dp visual track
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .onSizeChanged { size: IntSize ->
                            trackWidthPx = size.width.toFloat().coerceAtLeast(1f)
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val fraction = (offset.x / trackWidthPx).coerceIn(0.05f, 1f)
                                applyBrightness(fraction)
                                onInteraction()
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val fraction = (offset.x / trackWidthPx).coerceIn(0.05f, 1f)
                                    applyBrightness(fraction)
                                    onInteraction()
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val fraction = (change.position.x / trackWidthPx).coerceIn(0.05f, 1f)
                                    applyBrightness(fraction)
                                    onInteraction()
                                },
                                onDragEnd = { onInteraction() },
                                onDragCancel = { onInteraction() },
                            )
                        },
                contentAlignment = Alignment.CenterStart,
            ) {
                // Background bar track
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)),
                )
                // Filled progress bar track
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(fraction = brightness)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.primary),
                )
            }

            Text(
                text = "${(brightness * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.width(42.dp),
            )
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
