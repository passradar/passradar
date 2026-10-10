package io.github.passradar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.passradar.R
import io.github.passradar.ui.glass.GlassTitleBar
import io.github.passradar.data.AlertMode
import io.github.passradar.data.AppSettings
import io.github.passradar.data.Availability
import io.github.passradar.data.Watch
import io.github.passradar.domain.Dates
import java.time.LocalDate
import java.util.Locale

/** The offices the user follows, what counts as news, and whether checks can run. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchesScreen(vm: AppViewModel, bottomSpace: Dp) {
    val context = LocalContext.current
    val watches by vm.watches.collectAsStateWithLifecycle()
    val availability by vm.availability.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val checking by vm.checking.collectAsStateWithLifecycle()
    val nextCheck by vm.nextCheck.collectAsStateWithLifecycle()
    val offices by vm.offices.collectAsStateWithLifecycle()
    val offered = remember(offices) { offices.mapTo(HashSet()) { it.id } }
    val locale = currentLocale()
    val today = Dates.today()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.tab_watches),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            item { StatusCard(settings, watches, nextCheck, checking, locale, today, onCheck = vm::checkNow) }
            item { RuleCard(settings, today, locale, vm, onPickDate = { pickDate = true }) }

            if (watches.isNotEmpty()) {
                item { BackgroundRunCard(alwaysShow = false) }
            }

            item {
                SectionTitle(stringResource(R.string.followed_title, watches.size)) {
                    if (watches.isNotEmpty()) {
                        TextButton(onClick = { showPicker = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.add_offices_short))
                        }
                    }
                }
            }
            if (watches.isEmpty()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(stringResource(R.string.watches_empty_title), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.watches_empty_body), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.choose_offices))
                            }
                        }
                    }
                }
            }
            items(watches, key = { it.officeId }) { watch ->
                WatchRow(
                    watch = watch,
                    availability = availability[watch.officeId] ?: watch.snapshotAvailability(today),
                    offered = offered.isEmpty() || watch.officeId in offered,
                    today = today,
                    locale = locale,
                    onOpen = { vm.openOffice(watch.officeId) },
                    onRemove = { vm.removeWatch(watch.officeId) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (watches.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = { showPicker = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.add_more_offices))
                    }
                }
            }
        }
        GlassTitleBar(backdrop, stringResource(R.string.tab_watches), visible = scrolled)
    }

    if (showPicker) OfficePickerSheet(vm, onDismiss = { showPicker = false })
    if (pickDate) {
        DeadlinePickerDialog(
            initial = settings.alertBefore,
            onDismiss = { pickDate = false },
            onConfirm = { date ->
                vm.setAlertBeforeDate(date)
                pickDate = false
            },
        )
    }
}

@Composable
private fun StatusCard(
    settings: AppSettings,
    watches: List<Watch>,
    nextCheck: Long?,
    checking: Boolean,
    locale: Locale,
    today: LocalDate,
    onCheck: () -> Unit,
) {
    val active = settings.backgroundEnabled && watches.isNotEmpty() && !settings.alertExpired(today)
    val lastChecked = watches.maxOfOrNull { it.lastChecked }?.takeIf { it > 0 }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (active) R.string.alerts_active else R.string.alerts_paused),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val line = when {
                        !settings.backgroundEnabled -> stringResource(R.string.watches_status_off)
                        watches.isEmpty() -> stringResource(R.string.alerts_no_offices)
                        else -> stringResource(R.string.watches_status_on, intervalLabel(settings.intervalMinutes))
                    }
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
            val times = listOfNotNull(
                lastChecked?.let { stringResource(R.string.last_checked, Format.moment(it, locale)) },
                nextCheck?.takeIf { active }?.let { stringResource(R.string.next_check, Format.moment(it, locale)) },
            )
            if (times.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(times.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            if (watches.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                FilledTonalButton(onClick = onCheck, enabled = !checking, modifier = Modifier.height(44.dp)) {
                    if (checking) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.checking))
                    } else {
                        Text(stringResource(R.string.check_now))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleCard(settings: AppSettings, today: LocalDate, locale: Locale, vm: AppViewModel, onPickDate: () -> Unit) {
    val modes = listOf(
        AlertMode.Earlier to R.string.rule_earlier,
        AlertMode.Within to R.string.rule_within,
        AlertMode.Before to R.string.rule_before,
    )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(stringResource(R.string.rule_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = settings.alertMode == mode,
                        onClick = { if (mode == AlertMode.Before && settings.alertBefore == null) onPickDate() else vm.setAlertMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val description = when (settings.alertMode) {
                AlertMode.Earlier -> stringResource(R.string.rule_earlier_desc)
                AlertMode.Within -> stringResource(R.string.rule_within_desc, weeksText(settings.alertWithinDays))
                AlertMode.Before -> settings.alertBefore?.let { stringResource(R.string.rule_before_desc, Format.dayDate(it, locale)) }
                    ?: stringResource(R.string.rule_before_pick)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp))
            when (settings.alertMode) {
                AlertMode.Within -> Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (days in AppSettings.WithinChoices) {
                        FilterChip(
                            selected = settings.alertWithinDays == days,
                            onClick = { vm.setAlertWithinDays(days) },
                            label = { Text(weeksText(days)) },
                        )
                    }
                }
                AlertMode.Before -> {
                    OutlinedButton(onClick = onPickDate, modifier = Modifier.padding(start = 16.dp, top = 8.dp)) {
                        Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(settings.alertBefore?.let { Format.shortDate(it, locale) } ?: stringResource(R.string.rule_before_choose))
                    }
                    if (settings.alertExpired(today)) {
                        Text(
                            stringResource(R.string.rule_before_expired),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }
                }
                AlertMode.Earlier -> Unit
            }
        }
    }
}

@Composable
private fun weeksText(days: Int): String {
    val weeks = days / 7
    return pluralStringResource(R.plurals.weeks_count, weeks, weeks)
}

@Composable
private fun WatchRow(
    watch: Watch,
    availability: Availability?,
    /** False when the office doesn't offer the service chosen in the settings. */
    offered: Boolean,
    today: LocalDate,
    locale: Locale,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            WaitBadge(availability, today, notOffered = !offered)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(watch.officeName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!offered) {
                    Text(stringResource(R.string.watch_not_offered), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    return@Column
                }
                Text(earliestLine(availability), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val status = when {
                    watch.lastError != null -> stringResource(R.string.check_error, Format.moment(watch.lastChecked, locale))
                    watch.lastChecked > 0 -> stringResource(R.string.last_checked, Format.moment(watch.lastChecked, locale))
                    else -> stringResource(R.string.never_checked)
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (watch.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.watch_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun intervalLabel(minutes: Int): String = stringResource(
    when (minutes) {
        15 -> R.string.interval_15
        30 -> R.string.interval_30
        60 -> R.string.interval_60
        else -> R.string.interval_120
    },
)
