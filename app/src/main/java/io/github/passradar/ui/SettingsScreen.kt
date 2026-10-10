package io.github.passradar.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.passradar.BuildConfig
import io.github.passradar.R
import io.github.passradar.data.AppSettings
import io.github.passradar.data.PolitietApi
import io.github.passradar.data.ServiceType
import io.github.passradar.data.ThemeMode
import io.github.passradar.ui.glass.GlassTitleBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, bottomSpace: Dp) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val services by vm.services.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

    val backdrop = rememberLayerBackdrop()
    val listState = rememberLazyListState()
    val side = readableSidePadding()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 24 } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().layerBackdrop(backdrop).background(screenBrush()),
            state = listState,
            contentPadding = PaddingValues(
                start = side,
                end = side,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                bottom = bottomSpace + 16.dp,
            ),
        ) {
            item {
                Text(
                    stringResource(R.string.tab_settings),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            item { SectionTitle(stringResource(R.string.settings_appearance)) }
            item {
                GroupCard {
                    val modes = listOf(ThemeMode.System to R.string.theme_system, ThemeMode.Light to R.string.theme_light, ThemeMode.Dark to R.string.theme_dark)
                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        modes.forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { vm.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 31) {
                        ListItem(
                            colors = itemColors,
                            modifier = Modifier.toggleable(value = settings.dynamicColor, role = Role.Switch) { vm.setDynamicColor(it) },
                            headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                            supportingContent = { Text(stringResource(R.string.settings_dynamic_color_hint)) },
                            trailingContent = { Switch(checked = settings.dynamicColor, onCheckedChange = null) },
                        )
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.settings_service)) }
            item {
                GroupCard {
                    // Most people need a passport, so that one goes first.
                    for (service in services.sortedBy { it.id != ServiceType.Passport.id }) {
                        val selected = service.id == settings.serviceId
                        ListItem(
                            colors = itemColors,
                            modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) {
                                vm.updateSettings { it.copy(serviceId = service.id) }
                            },
                            leadingContent = { RadioButton(selected = selected, onClick = null) },
                            headlineContent = { Text(service.name(locale)) },
                        )
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.settings_persons)) }
            item {
                GroupCard {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(
                            onClick = { vm.updateSettings { it.copy(persons = (it.persons - 1).coerceAtLeast(1)) } },
                            enabled = settings.persons > 1,
                        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                        Text(
                            pluralStringResource(R.plurals.persons, settings.persons, settings.persons),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        FilledTonalIconButton(
                            onClick = { vm.updateSettings { it.copy(persons = (it.persons + 1).coerceAtMost(AppSettings.MAX_PERSONS)) } },
                            enabled = settings.persons < AppSettings.MAX_PERSONS,
                        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                    }
                    Hint(stringResource(R.string.settings_persons_hint), Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
            }

            item { SectionTitle(stringResource(R.string.settings_background_title)) }
            item {
                GroupCard {
                    ListItem(
                        colors = itemColors,
                        modifier = Modifier.toggleable(value = settings.backgroundEnabled, role = Role.Switch) { on ->
                            vm.updateSettings { it.copy(backgroundEnabled = on) }
                        },
                        headlineContent = { Text(stringResource(R.string.settings_background)) },
                        trailingContent = { Switch(checked = settings.backgroundEnabled, onCheckedChange = null) },
                    )
                    for (minutes in AppSettings.Intervals) {
                        val selected = settings.intervalMinutes == minutes
                        ListItem(
                            colors = itemColors,
                            modifier = Modifier.selectable(selected = selected, enabled = settings.backgroundEnabled, role = Role.RadioButton) {
                                vm.updateSettings { it.copy(intervalMinutes = minutes) }
                            },
                            leadingContent = { RadioButton(selected = selected, onClick = null, enabled = settings.backgroundEnabled) },
                            headlineContent = { Text(intervalLabel(minutes).replaceFirstChar { it.titlecase(locale) }) },
                        )
                    }
                    Hint(stringResource(R.string.settings_interval_hint), Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
            }

            item { SectionTitle(stringResource(R.string.settings_background_running)) }
            item { BackgroundRunCard(alwaysShow = true) }

            item { SectionTitle(stringResource(R.string.settings_privacy)) }
            item {
                GroupCard {
                    ListItem(
                        colors = itemColors,
                        headlineContent = { Text(stringResource(R.string.settings_location)) },
                        supportingContent = {
                            Text(stringResource(if (home != null) R.string.settings_location_saved else R.string.settings_location_none))
                        },
                        trailingContent = {
                            if (home != null) TextButton(onClick = vm::clearHome) { Text(stringResource(R.string.settings_location_forget)) }
                        },
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.settings_about)) }
            item {
                GroupCard {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.about_text, stringResource(R.string.app_name)), style = MaterialTheme.typography.bodyMedium)
                        Hint(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
                        Hint(stringResource(R.string.about_map))
                    }
                    TextButton(onClick = { context.openUrl(PolitietApi.BOOKING_URL) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(stringResource(R.string.open_booking_site))
                    }
                    if (BuildConfig.SOURCE_URL.isNotBlank()) {
                        TextButton(onClick = { context.openUrl(BuildConfig.SOURCE_URL) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(stringResource(R.string.about_source))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
        GlassTitleBar(backdrop, stringResource(R.string.tab_settings), visible = scrolled)
    }
}
