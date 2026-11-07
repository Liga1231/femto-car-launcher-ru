package io.github.seijikohara.femto.ui.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.data.display.MapColorScheme
import io.github.seijikohara.femto.ui.settings.SettingsAction
import io.github.seijikohara.femto.ui.settings.SettingsUiState
import java.util.Locale

@Composable
internal fun ScenariosSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier) {
    val hourOptions = remember {
        (0..23).map { h -> h to String.format(Locale.US, "%02d:00", h) }
    }

    val switchSummary =
        if (uiState.themeScheduleEnabled) {
            stringResource(
                R.string.settings_scenario_theme_schedule_summary_on,
                uiState.themeScheduleLightHour,
                uiState.themeScheduleDarkHour,
            )
        } else {
            stringResource(R.string.settings_scenario_theme_schedule_summary_off)
        }

    SwitchRow(
        title = stringResource(R.string.settings_scenario_theme_schedule_title),
        checked = uiState.themeScheduleEnabled,
        onCheckedChange = { onAction(SettingsAction.SetThemeScheduleEnabled(it)) },
        summary = switchSummary,
    )

    AnimatedVisibility(visible = uiState.themeScheduleEnabled) {
        Column {
            SettingsSubheader(stringResource(R.string.settings_scenario_day_header))
            ChoiceRow(
                title = stringResource(R.string.settings_scenario_light_time_title),
                options = hourOptions,
                selected = uiState.themeScheduleLightHour,
                onSelect = { onAction(SettingsAction.SetThemeScheduleLightHour(it)) },
            )
            ChoiceRow(
                title = stringResource(R.string.settings_scenario_light_scheme_title),
                options =
                    listOf(
                        MapColorScheme.ACCENT to stringResource(R.string.settings_map_scheme_accent),
                        MapColorScheme.MUTED to stringResource(R.string.settings_map_scheme_muted),
                        MapColorScheme.WARM to stringResource(R.string.settings_map_scheme_warm),
                        MapColorScheme.SLATE to stringResource(R.string.settings_map_scheme_slate),
                        MapColorScheme.POSITRON to stringResource(R.string.settings_map_scheme_positron),
                        MapColorScheme.BRIGHT to stringResource(R.string.settings_map_scheme_bright),
                        MapColorScheme.LIBERTY to stringResource(R.string.settings_map_scheme_liberty),
                        MapColorScheme.CUSTOM to stringResource(R.string.settings_map_scheme_custom),
                    ),
                selected = uiState.mapSchemeLight,
                onSelect = { onAction(SettingsAction.SetMapSchemeLight(it)) },
            )

            SettingsSubheader(stringResource(R.string.settings_scenario_night_header))
            ChoiceRow(
                title = stringResource(R.string.settings_scenario_dark_time_title),
                options = hourOptions,
                selected = uiState.themeScheduleDarkHour,
                onSelect = { onAction(SettingsAction.SetThemeScheduleDarkHour(it)) },
            )
            ChoiceRow(
                title = stringResource(R.string.settings_scenario_dark_scheme_title),
                options =
                    listOf(
                        MapColorScheme.ACCENT to stringResource(R.string.settings_map_scheme_accent),
                        MapColorScheme.MUTED to stringResource(R.string.settings_map_scheme_muted),
                        MapColorScheme.WARM to stringResource(R.string.settings_map_scheme_warm),
                        MapColorScheme.SLATE to stringResource(R.string.settings_map_scheme_slate),
                        MapColorScheme.DARK_MATTER to stringResource(R.string.settings_map_scheme_dark_matter),
                        MapColorScheme.DARK to stringResource(R.string.settings_map_scheme_dark),
                        MapColorScheme.FIORD to stringResource(R.string.settings_map_scheme_fiord),
                        MapColorScheme.CUSTOM to stringResource(R.string.settings_map_scheme_custom),
                    ),
                selected = uiState.mapSchemeDark,
                onSelect = { onAction(SettingsAction.SetMapSchemeDark(it)) },
            )
        }
    }
}
