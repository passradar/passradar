package io.github.passvarsling.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.passvarsling.R
import io.github.passvarsling.data.Availability
import io.github.passvarsling.data.Office
import io.github.passvarsling.data.PolitietApi
import io.github.passvarsling.data.Query
import io.github.passvarsling.domain.Dates
import io.github.passvarsling.domain.WaitTime
import io.github.passvarsling.ui.glass.GlassTopPanel
import io.github.passvarsling.ui.glass.PillShape
import io.github.passvarsling.ui.glass.glass
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** One office: how long the wait is, free dates and times, booking and following. */
@Composable
fun OfficeDetailScreen(vm: AppViewModel, officeId: String, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val context = LocalContext.current
    val detail by vm.detail.collectAsStateWithLifecycle()
    val watches by vm.watches.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val offices by vm.offices.collectAsStateWithLifecycle()
    val availability by vm.availability.collectAsStateWithLifecycle()
    val office = remember(offices, officeId) { vm.findOffice(officeId) }
    val followed = watches.any { it.officeId == officeId }
    val locale = currentLocale()
    val today = Dates.today()
    val requestNotifications = rememberNotificationPermission()
    val backdrop = rememberLayerBackdrop()
    val listState = rememberLazyListState()
    // The name moves into the top bar once the big one has scrolled away.
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 300 } }
    val bookingUrl = PolitietApi.bookingUrl(officeId, query.service.id)
    val state = detail?.takeIf { it.officeId == officeId }
    // Fresh dates from this page win over the overview's cached first date.
    val current = state?.takeIf { !it.loading && !it.failed }
        ?.let { Availability(it.dates.firstOrNull()?.toString(), it.dates.size, it.fetchedAt) }
        ?: availability[officeId]

    val side = readableSidePadding()

    // The surface keeps taps from reaching the screen underneath.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().layerBackdrop(backdrop).background(screenBrush()),
                contentPadding = PaddingValues(
                    start = 16.dp + side,
                    end = 16.dp + side,
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 76.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Hero(office, current, query, today, locale) { office?.let(context::openInMaps) } }
                item {
                    FreeDates(
                        state = state,
                        today = today,
                        locale = locale,
                        onSelect = vm::selectDate,
                        onRetry = { vm.loadDetail(officeId) },
                        onTimeClick = { context.openUrl(bookingUrl) },
                    )
                }
                item { Hint(stringResource(R.string.book_hint), Modifier.padding(horizontal = 4.dp)) }
            }

            GlassTopPanel(backdrop) {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp + side), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                    Text(
                        if (scrolled) office?.name.orEmpty() else "",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.loadDetail(officeId) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            }

            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .glass(backdrop, PillShape, lensHeight = 16.dp)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { context.openUrl(bookingUrl) }, modifier = Modifier.weight(1f).height(52.dp), shape = PillShape) {
                    Text(stringResource(R.string.book_button), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                FollowButton(
                    followed = followed,
                    onToggle = {
                        office?.let {
                            if (!followed) requestNotifications()
                            vm.toggleWatch(it)
                        }
                    },
                    modifier = Modifier.size(52.dp),
                )
            }

            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp))
        }
    }
}

@Composable
private fun Hero(office: Office?, availability: Availability?, query: Query, today: LocalDate, locale: Locale, onShowInMaps: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WaitBadge(availability, today, size = 88.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.detail_wait_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val earliest = availability?.earliestDate
                Text(
                    when {
                        availability == null -> "…"
                        earliest == null -> stringResource(R.string.earliest_none)
                        else -> waitText(WaitTime.days(today, earliest)).replaceFirstChar { it.titlecase(locale) }
                    },
                    style = MaterialTheme.typography.headlineMedium,
                )
                if (earliest != null) {
                    Text(stringResource(R.string.detail_first_free_line, Format.dayDate(earliest, locale)), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(office?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall)
        if (office != null && office.addressLine.isNotBlank()) {
            Row(
                Modifier.padding(top = 6.dp).clip(PillShape).clickable(onClick = onShowInMaps).padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Place, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text(office.addressLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(4.dp))
        Hint("${query.service.name(locale)} · ${pluralStringResource(R.plurals.persons, query.persons, query.persons)}")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FreeDates(
    state: DetailState?,
    today: LocalDate,
    locale: Locale,
    onSelect: (LocalDate) -> Unit,
    onRetry: () -> Unit,
    onTimeClick: () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.detail_free_dates), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            when {
                state == null || state.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.failed -> {
                    Text(stringResource(if (LocalContext.current.isOnline()) R.string.detail_load_error else R.string.offline), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                state.dates.isEmpty() -> Text(stringResource(R.string.detail_no_dates), style = MaterialTheme.typography.bodyMedium)
                else -> {
                    Hint(pluralStringResource(R.plurals.dates_count, state.dates.size, state.dates.size))
                    Spacer(Modifier.height(8.dp))
                    MonthCalendar(state.dates, state.selectedDate, locale, onSelect)

                    val selected = state.selectedDate
                    if (selected != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.detail_times_for, Format.dayDate(selected, locale)) +
                                " · " + waitText(WaitTime.days(today, selected)),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        val times = state.times
                        when {
                            times == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                            state.timesFailed -> Text(stringResource(if (LocalContext.current.isOnline()) R.string.detail_times_error else R.string.offline), style = MaterialTheme.typography.bodyMedium)
                            times.isEmpty() -> Text(stringResource(R.string.detail_no_times), style = MaterialTheme.typography.bodyMedium)
                            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (time in times) {
                                    SuggestionChip(onClick = onTimeClick, label = { Text(time) }, shape = PillShape)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Hint(stringResource(R.string.fetched_at, Format.moment(state.fetchedAt, locale)))
                }
            }
        }
    }
}

/** A month grid where free dates can be tapped. */
@Composable
private fun MonthCalendar(dates: List<LocalDate>, selected: LocalDate?, locale: Locale, onSelect: (LocalDate) -> Unit) {
    val free = remember(dates) { dates.toHashSet() }
    val firstMonth = YearMonth.from(dates.first())
    val lastMonth = YearMonth.from(dates.last())
    var month by remember(firstMonth, lastMonth) { mutableStateOf(selected?.let(YearMonth::from) ?: firstMonth) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }, enabled = month > firstMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_month))
            }
            Text(
                Format.monthYear(month, locale),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < lastMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_month))
            }
        }
        Row {
            for (i in 0 until 7) {
                Text(
                    DayOfWeek.MONDAY.plus(i.toLong()).getDisplayName(TextStyle.SHORT, locale).removeSuffix("."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val offset = month.atDay(1).dayOfWeek.value - 1
        val days = month.lengthOfMonth()
        val weeks = (offset + days + 6) / 7
        for (week in 0 until weeks) {
            Row {
                for (column in 0 until 7) {
                    val day = week * 7 + column - offset + 1
                    if (day in 1..days) {
                        val date = month.atDay(day)
                        DayCell(date, isFree = date in free, isSelected = date == selected, onSelect = onSelect, modifier = Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, isFree: Boolean, isSelected: Boolean, onSelect: (LocalDate) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        isSelected -> colors.primary
        isFree -> colors.primaryContainer
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> colors.onPrimary
        isFree -> colors.onPrimaryContainer
        else -> colors.onSurface.copy(alpha = 0.38f)
    }
    Box(
        modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(background)
            .then(if (isFree) Modifier.clickable { onSelect(date) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = textColor,
            fontWeight = if (isFree) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
