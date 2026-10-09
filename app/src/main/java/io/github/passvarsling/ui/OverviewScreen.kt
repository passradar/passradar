package io.github.passvarsling.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.passvarsling.R
import io.github.passvarsling.data.Availability
import io.github.passvarsling.data.Office
import io.github.passvarsling.data.ServiceType
import io.github.passvarsling.domain.Dates
import io.github.passvarsling.domain.WaitLevel
import io.github.passvarsling.domain.WaitTime
import io.github.passvarsling.domain.distanceKm
import io.github.passvarsling.domain.matchesSearch
import io.github.passvarsling.ui.glass.GlassTopPanel
import io.github.passvarsling.ui.glass.PillChip
import io.github.passvarsling.ui.glass.SearchPill
import java.time.LocalDate

/** "Near you" means within this distance for the key numbers. */
private const val NEAR_KM = 75.0

/** Key numbers shown above the list. */
private data class OverviewStats(
    val shortest: Pair<Office, Long>?,
    val shortestNear: Triple<Office, Long, Double>?,
    val underTwoWeeks: Int,
    val typicalDays: Long?,
    val followedBest: Long?,
    val followedCount: Int,
)

private fun stats(
    offices: List<Office>,
    availability: Map<String, Availability>,
    watched: Set<String>,
    position: Position?,
    today: LocalDate,
): OverviewStats {
    val waits = offices.mapNotNull { office -> availability[office.id]?.earliestDate?.let { office to WaitTime.days(today, it) } }
    val sorted = waits.map { it.second }.sorted()
    val near = position?.let { p ->
        waits.mapNotNull { (office, days) ->
            val km = office.distanceFrom(p) ?: return@mapNotNull null
            if (km <= NEAR_KM) Triple(office, days, km) else null
        }.minWithOrNull(compareBy<Triple<Office, Long, Double>>({ it.second }, { it.third }))
    }
    return OverviewStats(
        shortest = waits.minByOrNull { it.second },
        shortestNear = near,
        underTwoWeeks = waits.count { it.second <= 14 },
        typicalDays = sorted.getOrNull(sorted.size / 2),
        followedBest = waits.filter { it.first.id in watched }.minOfOrNull { it.second },
        // Only the followed offices that offer the chosen service.
        followedCount = offices.count { it.id in watched },
    )
}

fun Office.distanceFrom(position: Position): Double? {
    val lat = lat ?: return null
    val lon = lon ?: return null
    return distanceKm(position.lat, position.lon, lat, lon)
}

/**
 * Every office: the ones you follow first, then the nearest (or all of them grouped
 * by how long the wait is when your position isn't known).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(vm: AppViewModel, bottomSpace: Dp) {
    val context = LocalContext.current
    val offices by vm.offices.collectAsStateWithLifecycle()
    val availability by vm.availability.collectAsStateWithLifecycle()
    val watches by vm.watches.collectAsStateWithLifecycle()
    val refresh by vm.refresh.collectAsStateWithLifecycle()
    val lastRefresh by vm.lastFullRefresh.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    val side = readableSidePadding()
    val locale = currentLocale()
    val today = Dates.today()
    val density = LocalDensity.current
    val requestNotifications = rememberNotificationPermission()
    val backdrop = rememberLayerBackdrop()
    val listState = rememberLazyListState()

    var search by rememberSaveable { mutableStateOf("") }
    var onlyFollowed by rememberSaveable { mutableStateOf(false) }
    var headerHeight by remember { mutableStateOf(0.dp) }
    var askedByTap by remember { mutableStateOf(false) }
    val requestLocation = rememberLocationRequest { location ->
        if (location != null) {
            vm.setHome(location.latitude, location.longitude)
            vm.setSortByDistance(true)
        } else if (askedByTap) {
            vm.locationUnavailable()
        }
    }

    // "Near me" is the default: ask once on first start, and refresh the position quietly afterwards.
    LaunchedEffect(Unit) {
        val stored = vm.storedSettings()
        if (!stored.sortByDistance) return@LaunchedEffect
        if (hasLocationPermission(context)) {
            currentLocation(context)?.let { vm.setHome(it.latitude, it.longitude) }
        } else if (!stored.locationAsked) {
            vm.markLocationAsked()
            requestLocation()
        }
    }

    val position = home
    val nearMe = settings.sortByDistance && position != null
    val watched = remember(watches) { watches.mapTo(HashSet()) { it.officeId } }
    // Keep the order while a refresh fills in dates, so rows don't jump around under your finger.
    val sortBasis = if (refresh == null) availability else null
    val matching = remember(offices, search) { offices.filter { matchesSearch(it, search) } }
    val byWait = compareBy<Office> { availability[it.id]?.earliest ?: "9999" }
    val byDistance = compareBy<Office> { o -> position?.let { o.distanceFrom(it) } ?: Double.MAX_VALUE }
    val followed = remember(matching, watched, sortBasis, position, nearMe) {
        matching.filter { it.id in watched }.sortedWith(if (nearMe) byDistance else byWait)
    }
    val others = remember(matching, watched, sortBasis, position, nearMe, onlyFollowed) {
        if (onlyFollowed) emptyList() else matching.filter { it.id !in watched }.sortedWith(if (nearMe) byDistance else byWait)
    }
    val groups = remember(others, nearMe) {
        if (nearMe) emptyList()
        else others.groupBy { waitLevel(availability[it.id], today) }.toSortedMap(compareBy<WaitLevel> { it.ordinal }).toList()
    }
    val numbers = remember(offices, sortBasis, watched, position) { stats(offices, availability, watched, position, today) }

    LaunchedEffect(search, onlyFollowed, nearMe) { listState.scrollToItem(0) }

    @Composable
    fun row(office: Office, modifier: Modifier) = OfficeRow(
        office = office,
        availability = availability[office.id],
        today = today,
        followed = office.id in watched,
        distance = position?.let { office.distanceFrom(it) },
        onOpen = { vm.openOffice(office.id) },
        onToggle = {
            if (office.id !in watched) requestNotifications()
            vm.toggleWatch(office)
        },
        modifier = modifier,
    )

    Box(Modifier.fillMaxSize()) {
        val pullState = rememberPullToRefreshState()
        // The progress bar under the title shows a running refresh; the pull spinner only
        // acknowledges the gesture, so it doesn't sit on top of the title for half a minute.
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = { vm.refreshAll() },
            state = pullState,
            modifier = Modifier.fillMaxSize().layerBackdrop(backdrop).background(screenBrush()),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = false,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = headerHeight),
                )
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = side, end = side, top = headerHeight + 8.dp, bottom = bottomSpace + 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "title") {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        val title = if (settings.serviceId == ServiceType.Passport.id) R.string.overview_title else R.string.overview_title_other
                        Text(stringResource(title), style = MaterialTheme.typography.headlineLarge)
                        val progress = refresh
                        val subtitle = when {
                            progress != null -> stringResource(R.string.refreshing_progress, progress.done, progress.total)
                            lastRefresh > 0 -> pluralStringResource(R.plurals.overview_subtitle, offices.size, offices.size, Format.moment(lastRefresh, locale))
                            else -> pluralStringResource(R.plurals.offices_count, offices.size, offices.size)
                        }
                        Hint(subtitle)
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { if (progress.total == 0) 0f else progress.done / progress.total.toFloat() },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        }
                    }
                }
                if (search.isBlank() && !onlyFollowed) {
                    item(key = "stats") { StatsRow(numbers, vm) }
                }
                if (followed.isEmpty() && others.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(if (onlyFollowed && search.isBlank()) R.string.no_followed else R.string.no_offices),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
                if (followed.isNotEmpty()) {
                    item(key = "header-followed") {
                        SectionHeader(stringResource(R.string.section_followed), followed.size, Modifier.animateItem(), icon = Icons.Filled.Notifications)
                    }
                    items(followed, key = { it.id }) { office -> row(office, Modifier.animateItem()) }
                }
                if (nearMe) {
                    if (others.isNotEmpty()) {
                        item(key = "header-near") {
                            SectionHeader(stringResource(R.string.section_nearest), others.size, Modifier.animateItem(), icon = Icons.Filled.LocationOn)
                        }
                    }
                    items(others, key = { it.id }) { office -> row(office, Modifier.animateItem()) }
                } else {
                    for ((level, list) in groups) {
                        item(key = "group-$level") { SectionHeader(levelLabel(level), list.size, Modifier.animateItem(), level = level) }
                        items(list, key = { it.id }) { office -> row(office, Modifier.animateItem()) }
                    }
                }
            }
        }

        GlassTopPanel(backdrop, Modifier.onSizeChanged { headerHeight = with(density) { it.height.toDp() } }) {
            SearchPill(
                value = search,
                onValueChange = { search = it },
                placeholder = pluralStringResource(R.plurals.search_hint_count, offices.size, offices.size),
                clearDescription = stringResource(R.string.clear_search),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp + side, end = 16.dp + side, top = 8.dp),
                trailing = {
                    IconButton(onClick = { vm.refreshAll() }, enabled = refresh == null) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp + side, end = 16.dp + side, top = 10.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillChip(stringResource(R.string.filter_all), selected = !onlyFollowed, onClick = { onlyFollowed = false })
                PillChip(
                    stringResource(R.string.filter_followed, numbers.followedCount),
                    selected = onlyFollowed,
                    onClick = { onlyFollowed = true },
                    icon = Icons.Filled.Notifications,
                )
                PillChip(
                    stringResource(R.string.filter_near),
                    selected = nearMe,
                    onClick = {
                        when {
                            nearMe -> vm.setSortByDistance(false)
                            position != null -> vm.setSortByDistance(true)
                            else -> {
                                askedByTap = true
                                requestLocation()
                            }
                        }
                    },
                    icon = Icons.Filled.LocationOn,
                )
            }
        }
    }
}

@Composable
private fun levelLabel(level: WaitLevel): String = stringResource(
    when (level) {
        WaitLevel.Short -> R.string.group_short
        WaitLevel.Medium -> R.string.group_medium
        WaitLevel.Long -> R.string.group_long
        WaitLevel.VeryLong -> R.string.group_very_long
        WaitLevel.NoneFree -> R.string.group_none
        WaitLevel.Unknown -> R.string.group_unknown
    },
)

@Composable
private fun StatsRow(stats: OverviewStats, vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val near = stats.shortestNear
        if (near != null) {
            StatTile(
                label = stringResource(R.string.stats_shortest_near),
                value = waitText(near.second),
                sub = "${near.first.name} · ${distanceLabel(near.third)}",
                onClick = { vm.openOffice(near.first.id) },
            )
        }
        val shortest = stats.shortest
        StatTile(
            label = stringResource(R.string.stats_shortest),
            value = shortest?.let { waitText(it.second) } ?: "–",
            sub = shortest?.first?.name ?: "",
            onClick = shortest?.let { { vm.openOffice(it.first.id) } },
        )
        if (stats.followedCount > 0) {
            StatTile(
                label = stringResource(R.string.stats_followed),
                value = stats.followedBest?.let { waitText(it) } ?: "–",
                sub = pluralStringResource(R.plurals.offices_count, stats.followedCount, stats.followedCount),
                onClick = { vm.selectTab(Tab.Watches) },
            )
        }
        StatTile(
            label = stringResource(R.string.stats_under_two_weeks),
            value = stats.underTwoWeeks.toString(),
            sub = pluralStringResource(R.plurals.offices_word, stats.underTwoWeeks),
        )
        StatTile(
            label = stringResource(R.string.stats_typical),
            value = stats.typicalDays?.let { waitText(it) } ?: "–",
            sub = stringResource(R.string.stats_typical_sub),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, sub: String, onClick: (() -> Unit)? = null) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.width(160.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionHeader(label: String, count: Int, modifier: Modifier = Modifier, level: WaitLevel? = null, icon: ImageVector? = null) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            icon != null -> Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            level != null -> WaitDot(level, size = 10.dp)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(6.dp))
        Text(count.toString(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OfficeRow(
    office: Office,
    availability: Availability?,
    today: LocalDate,
    followed: Boolean,
    distance: Double?,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            WaitBadge(availability, today)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(office.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val details = listOfNotNull(earliestLine(availability), distance?.let { distanceLabel(it) }).joinToString(" · ")
                Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FollowButton(followed, onToggle)
        }
    }
}
