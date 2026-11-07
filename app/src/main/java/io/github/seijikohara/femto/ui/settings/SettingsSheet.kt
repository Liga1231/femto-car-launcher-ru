package io.github.seijikohara.femto.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.seijikohara.femto.data.fonts.FontSlot

/**
 * Settings presented as an in-window overlay over the dashboard: the
 * dashboard stays composed underneath, and settings render directly in the
 * Activity window without spawning a separate Dialog / popup window.
 * This completely prevents the Android system status bar (battery, wifi, time)
 * from appearing and causing window re-layout stutter and freezes.
 */
@Composable
internal fun SettingsSheet(
    onOpenNotificationAccess: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenFontPicker: (FontSlot) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenDocument: (SettingsDocument) -> Unit,
    onDismiss: () -> Unit,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
    startUpdate: Boolean = false,
) = SettingsSheetFrame(
    onDismiss = onDismiss,
    fullscreen = fullscreen,
    modifier = modifier,
    openExpanded = startUpdate,
) {
    SettingsRoute(
        onBack = onDismiss,
        onOpenNotificationAccess = onOpenNotificationAccess,
        onOpenSystemSettings = onOpenSystemSettings,
        onOpenFontPicker = onOpenFontPicker,
        onOpenDiagnostics = onOpenDiagnostics,
        onOpenLicenses = onOpenLicenses,
        onOpenDocument = onOpenDocument,
        startUpdate = startUpdate,
    )
}

/**
 * The in-window frame for settings. Fills the screen with the standard sheet
 * surface container color and handles back gestures.
 */
@Composable
internal fun SettingsSheetFrame(
    onDismiss: () -> Unit,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
    openExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}
