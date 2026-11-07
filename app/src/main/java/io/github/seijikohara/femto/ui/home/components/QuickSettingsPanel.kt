package io.github.seijikohara.femto.ui.home.components

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Navigation
import com.composables.icons.lucide.Plane
import com.composables.icons.lucide.Power
import com.composables.icons.lucide.Radio
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Signal
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.VolumeX
import com.composables.icons.lucide.Wifi
import com.composables.icons.lucide.WifiOff
import com.composables.icons.lucide.Bluetooth
import com.composables.icons.lucide.BluetoothOff
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState
import io.github.seijikohara.femto.data.system.RootShell
import io.github.seijikohara.femto.data.system.SystemStatus
import io.github.seijikohara.femto.ui.home.HomeAction
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.FemtoIcon
import io.github.seijikohara.femto.ui.theme.LocalFemtoDarkTheme
import io.github.seijikohara.femto.ui.theme.eyebrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Control center / Quick settings panel, opened by tapping the dock's status cluster
 * (battery, Wi-Fi, Bluetooth).
 * Provides fast automotive toggles for Wi-Fi, Bluetooth, Airplane mode, Mobile Data,
 * GPS, Hotspot, plus volume and brightness sliders and system settings shortcuts.
 */
@Composable
internal fun QuickSettingsPanel(
    systemStatus: SystemStatus,
    onClose: () -> Unit,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    glassConfig: GlassConfig = GlassConfig(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDark = LocalFemtoDarkTheme.current

    // Wi-Fi state
    val wifiManager = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager }
    var wifiEnabled by remember { mutableStateOf(wifiManager?.isWifiEnabled == true) }

    // Bluetooth state
    var btEnabled by remember(systemStatus.bluetoothEnabled) { mutableStateOf(systemStatus.bluetoothEnabled) }

    // Airplane mode state
    var airplaneMode by remember {
        mutableStateOf(Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0)
    }

    // Mobile data state
    var mobileDataEnabled by remember(systemStatus.cellularConnected) {
        mutableStateOf(systemStatus.cellularConnected == true)
    }

    // GPS / Location state
    val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager }
    var locationEnabled by remember(systemStatus.locationEnabled) {
        mutableStateOf(systemStatus.locationEnabled)
    }

    // Audio volume
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var currentVolume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    // Brightness
    var currentBrightness by remember {
        val b = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(128)
        mutableFloatStateOf(b / 255f)
    }

    // Sync state periodically while panel is open
    LaunchedEffect(Unit) {
        while (true) {
            wifiEnabled = wifiManager?.isWifiEnabled == true
            airplaneMode = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
            if (locationManager != null) {
                locationEnabled = androidx.core.location.LocationManagerCompat.isLocationEnabled(locationManager)
            }
            currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            delay(1500)
        }
    }

    BackHandler(onBack = onClose)

    Surface(
        modifier = modifier.glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(FemtoDimens.CardPadding)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PanelIconButton(
                    icon = Lucide.ChevronDown,
                    description = "Закрыть",
                    onClick = onClose,
                )
                Text(
                    text = "БЫСТРЫЕ НАСТРОЙКИ",
                    style = MaterialTheme.typography.eyebrow(),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                PanelIconButton(
                    icon = Lucide.ExternalLink,
                    description = "Все настройки системы",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )
            }

            // Quick Toggles Grid (6 items in 2 columns)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Row 1: Wi-Fi + Bluetooth
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QuickToggleTile(
                        icon = if (wifiEnabled) Lucide.Wifi else Lucide.WifiOff,
                        title = "Wi-Fi",
                        subtitle = if (systemStatus.wifiConnected) "Подключено" else if (wifiEnabled) "Включён" else "Отключён",
                        active = wifiEnabled,
                        onToggle = {
                            val next = !wifiEnabled
                            wifiEnabled = next
                            coroutineScope.launch {
                                RootShell.setWifi(next)
                                delay(800)
                                wifiEnabled = wifiManager?.isWifiEnabled == true
                            }
                        },
                        onOpenSettings = {
                            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                        modifier = Modifier.weight(1f),
                    )
                    QuickToggleTile(
                        icon = if (btEnabled) Lucide.Bluetooth else Lucide.BluetoothOff,
                        title = "Bluetooth",
                        subtitle = if (systemStatus.bluetoothConnected) "Подключено" else if (btEnabled) "Включён" else "Отключён",
                        active = btEnabled,
                        onToggle = {
                            val next = !btEnabled
                            btEnabled = next
                            coroutineScope.launch {
                                RootShell.setBluetooth(next)
                            }
                        },
                        onOpenSettings = {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Row 2: Airplane Mode + Mobile Data
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QuickToggleTile(
                        icon = Lucide.Plane,
                        title = "Режим полёта",
                        subtitle = if (airplaneMode) "Включён" else "Отключён",
                        active = airplaneMode,
                        onToggle = {
                            val next = !airplaneMode
                            airplaneMode = next
                            coroutineScope.launch {
                                RootShell.setAirplaneMode(next)
                                delay(600)
                                airplaneMode = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
                            }
                        },
                        onOpenSettings = {
                            context.startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                        modifier = Modifier.weight(1f),
                    )
                    QuickToggleTile(
                        icon = Lucide.Signal,
                        title = "Мобильные данные",
                        subtitle = if (mobileDataEnabled) "Включены" else "Отключены",
                        active = mobileDataEnabled,
                        onToggle = {
                            val next = !mobileDataEnabled
                            mobileDataEnabled = next
                            coroutineScope.launch {
                                RootShell.setMobileData(next)
                            }
                        },
                        onOpenSettings = {
                            context.startActivity(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Row 3: Hotspot + Location (GPS)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QuickToggleTile(
                        icon = Lucide.Radio,
                        title = "Точка доступа",
                        subtitle = "Раздача Wi-Fi",
                        active = false,
                        onToggle = {
                            runCatching {
                                context.startActivity(
                                    Intent("android.settings.TETHER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }.onFailure {
                                context.startActivity(
                                    Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        onOpenSettings = {
                            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                        modifier = Modifier.weight(1f),
                    )
                    QuickToggleTile(
                        icon = Lucide.Navigation,
                        title = "Геолокация (GPS)",
                        subtitle = when {
                            !locationEnabled -> "Отключён"
                            systemStatus.gpsFixed -> "Включён • Фикс (${systemStatus.gpsSatelliteCount} спутн.)"
                            systemStatus.gpsSatelliteCount > 0 -> "Включён • Поиск (${systemStatus.gpsSatelliteCount} спутн.)"
                            else -> "Включён"
                        },
                        active = locationEnabled,
                        onToggle = {
                            val next = !locationEnabled
                            locationEnabled = next
                            coroutineScope.launch {
                                RootShell.setLocation(next)
                                delay(600)
                                if (locationManager != null) {
                                    locationEnabled = androidx.core.location.LocationManagerCompat.isLocationEnabled(locationManager)
                                }
                            }
                        },
                        onOpenSettings = {
                            context.startActivity(
                                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Sliders Section: Volume + Brightness
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.2f else 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Volume Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconButton(
                            onClick = {
                                val target = if (currentVolume > 0) 0 else maxVolume / 2
                                currentVolume = target
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                            },
                        ) {
                            FemtoIcon(
                                imageVector = if (currentVolume == 0) Lucide.VolumeX else Lucide.Volume2,
                                contentDescription = "Громкость",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Громкость медиа",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${((currentVolume.toFloat() / maxVolume) * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Slider(
                                value = currentVolume.toFloat(),
                                onValueChange = {
                                    val v = it.toInt()
                                    currentVolume = v
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                                },
                                valueRange = 0f..maxVolume.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }

                    // Brightness Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FemtoIcon(
                            imageVector = Lucide.Sun,
                            contentDescription = "Яркость",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp).padding(start = 12.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Яркость экрана",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${(currentBrightness * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Slider(
                                value = currentBrightness,
                                onValueChange = {
                                    currentBrightness = it
                                    val bInt = (it * 255).toInt().coerceIn(1, 255)
                                    runCatching {
                                        Settings.System.putInt(
                                            context.contentResolver,
                                            Settings.System.SCREEN_BRIGHTNESS,
                                            bInt,
                                        )
                                    }
                                },
                                valueRange = 0.05f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                }
            }

            // Bottom row of system shortcuts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickSystemButton(
                    icon = Lucide.Settings,
                    label = "Настройки",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    modifier = Modifier.weight(1f),
                )
                QuickSystemButton(
                    icon = Lucide.RefreshCw,
                    label = "Перезагрузка",
                    onClick = {
                        coroutineScope.launch {
                            RootShell.reboot()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                QuickSystemButton(
                    icon = Lucide.Power,
                    label = "Выключение",
                    onClick = {
                        coroutineScope.launch {
                            RootShell.powerOff()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun QuickToggleTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    onToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalFemtoDarkTheme.current
    val containerColor =
        if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerHigh
        }

    val contentColor =
        if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    val borderColor =
        if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.25f else 0.4f)
        }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier =
            modifier
                .heightIn(min = 68.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                FemtoIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(32.dp),
            ) {
                FemtoIcon(
                    imageVector = Lucide.ExternalLink,
                    contentDescription = "Настройки $title",
                    tint = contentColor.copy(alpha = 0.65f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickSystemButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalFemtoDarkTheme.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier =
            modifier
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            FemtoIcon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}
