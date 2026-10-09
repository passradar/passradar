package io.github.passvarsling.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.passvarsling.PassvarslingApp
import io.github.passvarsling.data.AlertMode
import io.github.passvarsling.data.AppSettings
import io.github.passvarsling.data.Availability
import io.github.passvarsling.data.AvailabilityCache
import io.github.passvarsling.data.CheckResult
import io.github.passvarsling.data.Office
import io.github.passvarsling.data.Query
import io.github.passvarsling.data.RefreshResult
import io.github.passvarsling.data.Repository
import io.github.passvarsling.data.ServiceType
import io.github.passvarsling.data.ThemeMode
import io.github.passvarsling.data.Watch
import io.github.passvarsling.domain.Dates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.Collator
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

enum class Tab { Overview, Map, Watches, Settings }

data class RefreshProgress(val done: Int, val total: Int)

/** The office screen: free dates, and times for the chosen date. */
data class DetailState(
    val officeId: String,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val dates: List<LocalDate> = emptyList(),
    val fetchedAt: Long = 0,
    val selectedDate: LocalDate? = null,
    val times: List<String>? = null,
    val timesFailed: Boolean = false,
)

data class MapCamera(val lat: Double, val lon: Double, val zoom: Double)

data class Position(val lat: Double, val lon: Double)

sealed interface UiMessage {
    data class CheckDone(val result: CheckResult?) : UiMessage
    data class RefreshDone(val result: RefreshResult) : UiMessage
    data object LocationUnavailable : UiMessage
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val repo: Repository) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val services: StateFlow<List<ServiceType>> =
        repo.services.stateIn(viewModelScope, SharingStarted.Eagerly, listOf(ServiceType.Passport))

    val query: StateFlow<Query> = combine(settings, services) { s, list -> Query(repo.serviceFor(s, list), s.persons) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Query(ServiceType.Passport, 1))

    private val collator = Collator.getInstance(Locale.forLanguageTag("nb-NO"))

    val offices: StateFlow<List<Office>> = query.map { it.service.id }.distinctUntilChanged()
        .flatMapLatest { repo.offices(it) }
        .map { list -> list.sortedWith(compareBy(collator) { it.name }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val watches: StateFlow<List<Watch>> =
        repo.watches.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val nextCheck: StateFlow<Long?> =
        repo.nextCheck.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Where the user is, roughly, if they asked for "near me". */
    val home: StateFlow<Position?> = settings.map { s ->
        if (s.homeLat != null && s.homeLon != null) Position(s.homeLat, s.homeLon) else null
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val cache: StateFlow<AvailabilityCache> =
        repo.availability.stateIn(viewModelScope, SharingStarted.Eagerly, AvailabilityCache())

    /** Results of a running refresh, shown as they arrive (cache key to entries). */
    private val live = MutableStateFlow<Pair<String, Map<String, Availability>>>("" to emptyMap())

    val availability: StateFlow<Map<String, Availability>> = combine(cache, query, live) { cache, query, live ->
        val saved = if (cache.key == query.cacheKey) cache.entries else emptyMap()
        if (live.first == query.cacheKey) saved + live.second else saved
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val lastFullRefresh: StateFlow<Long> = combine(cache, query) { cache, query ->
        if (cache.key == query.cacheKey) cache.fullRefreshAt else 0L
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val _refresh = MutableStateFlow<RefreshProgress?>(null)
    val refresh: StateFlow<RefreshProgress?> = _refresh.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private val _tab = MutableStateFlow(Tab.Overview)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _openOffice = MutableStateFlow<String?>(null)
    val openOffice: StateFlow<String?> = _openOffice.asStateFlow()

    private val _detail = MutableStateFlow<DetailState?>(null)
    val detail: StateFlow<DetailState?> = _detail.asStateFlow()

    /** Where the map was looking, so switching tabs doesn't reset it. */
    var mapCamera: MapCamera? = null

    private var refreshJob: Job? = null
    private var detailJob: Job? = null
    private var timesJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                repo.refreshServices()
            } catch (e: IOException) {
                // Keep the cached or built-in service list.
            }
            repo.refreshOffices(repo.currentQuery().service.id)
            refreshAll(onlyIfStale = true)
        }
    }

    fun selectTab(tab: Tab) {
        _tab.value = tab
    }

    /** Fetches the first free date at every office (followed ones first). */
    fun refreshAll(onlyIfStale: Boolean = false) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val query = repo.currentQuery()
            if (onlyIfStale) {
                val cache = repo.availability.first()
                val age = System.currentTimeMillis() - cache.fullRefreshAt
                if (cache.key == query.cacheKey && age < AUTO_REFRESH_AFTER_MS) return@launch
            }
            val offices = repo.offices(query.service.id).first()
            if (offices.isEmpty()) return@launch
            val watched = repo.watches.first().mapTo(HashSet()) { it.officeId }
            // Followed offices first, then the nearest, so what you look at fills in first.
            val position = repo.settings.first().let { s -> if (s.homeLat != null && s.homeLon != null) Position(s.homeLat, s.homeLon) else null }
            val ids = offices
                .sortedWith(compareBy({ it.id !in watched }, { o -> position?.let { o.distanceFrom(it) } ?: 0.0 }))
                .map { it.id }
            val job = coroutineContext[Job]
            live.value = query.cacheKey to emptyMap()
            _refresh.value = RefreshProgress(0, ids.size)
            try {
                val result = repo.refreshAvailability(ids) { id, availability ->
                    if (availability != null) live.update { (key, entries) -> key to (entries + (id to availability)) }
                    _refresh.update { it?.copy(done = it.done + 1) }
                }
                if (result.failed > 0) _messages.trySend(UiMessage.RefreshDone(result))
            } finally {
                if (refreshJob == job) _refresh.value = null
            }
        }
    }

    fun openOffice(id: String) {
        _openOffice.value = id
        loadDetail(id)
    }

    fun closeOffice() {
        detailJob?.cancel()
        timesJob?.cancel()
        // The detail state stays until the next office opens, so the page can slide out intact.
        _openOffice.value = null
    }

    fun loadDetail(id: String) {
        detailJob?.cancel()
        timesJob?.cancel()
        _detail.value = DetailState(id)
        detailJob = viewModelScope.launch {
            try {
                val today = Dates.today()
                val dates = repo.fetchDates(id).filter { !it.isBefore(today) }
                _detail.value = DetailState(id, loading = false, dates = dates, fetchedAt = System.currentTimeMillis())
                dates.firstOrNull()?.let(::selectDate)
            } catch (e: IOException) {
                _detail.value = DetailState(id, loading = false, failed = true)
            }
        }
    }

    fun selectDate(date: LocalDate) {
        val current = _detail.value ?: return
        _detail.value = current.copy(selectedDate = date, times = null, timesFailed = false)
        timesJob?.cancel()
        timesJob = viewModelScope.launch {
            val times = try {
                repo.fetchTimes(current.officeId, date)
            } catch (e: IOException) {
                null
            }
            _detail.update { d ->
                if (d == null || d.officeId != current.officeId || d.selectedDate != date) d
                else d.copy(times = times.orEmpty(), timesFailed = times == null)
            }
        }
    }

    fun findOffice(id: String): Office? = offices.value.firstOrNull { it.id == id } ?: repo.findOffice(id)

    fun toggleWatch(office: Office) {
        val watched = watches.value.any { it.officeId == office.id }
        viewModelScope.launch {
            if (watched) repo.removeWatch(office.id) else repo.addWatch(office)
        }
    }

    fun removeWatch(officeId: String) {
        viewModelScope.launch { repo.removeWatch(officeId) }
    }

    /** Makes [selected] the full set of followed offices. */
    fun setFollowed(selected: Set<String>) {
        val current = watches.value.mapTo(HashSet()) { it.officeId }
        val add = (selected - current).mapNotNull(::findOffice)
        val remove = current - selected
        if (add.isEmpty() && remove.isEmpty()) return
        viewModelScope.launch { repo.setFollowed(add, remove) }
    }

    fun setAlertMode(mode: AlertMode) = updateSettings { it.copy(alertMode = mode) }

    fun setAlertWithinDays(days: Int) = updateSettings { it.copy(alertMode = AlertMode.Within, alertWithinDays = days) }

    fun setAlertBeforeDate(date: LocalDate) = updateSettings { it.copy(alertMode = AlertMode.Before, alertBeforeDate = date.toString()) }

    fun setThemeMode(mode: ThemeMode) = updateSettings { it.copy(themeMode = mode) }

    fun setDynamicColor(on: Boolean) = updateSettings { it.copy(dynamicColor = on) }

    /** Remembers the position rounded to about a kilometre; it is only used for distances. */
    fun setHome(lat: Double, lon: Double) = updateSettings {
        it.copy(homeLat = (lat * 100).roundToInt() / 100.0, homeLon = (lon * 100).roundToInt() / 100.0)
    }

    fun clearHome() = updateSettings { it.copy(homeLat = null, homeLon = null) }

    fun setSortByDistance(on: Boolean) = updateSettings { it.copy(sortByDistance = on) }

    /** The stored settings, waiting for them to load (the [settings] flow starts with defaults). */
    suspend fun storedSettings(): AppSettings = repo.settings.first()

    fun markLocationAsked() = updateSettings { it.copy(locationAsked = true) }

    fun markBackgroundPromptShown() = updateSettings { it.copy(backgroundPromptShown = true) }

    fun locationUnavailable() {
        _messages.trySend(UiMessage.LocationUnavailable)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val before = settings.value
            val after = repo.updateSettings(transform)
            if (before.serviceId != after.serviceId) repo.refreshOffices(after.serviceId)
            if (before.serviceId != after.serviceId || before.persons != after.persons) {
                refreshJob?.cancel()
                refreshAll(onlyIfStale = true)
                _openOffice.value?.let(::loadDetail)
            }
        }
    }

    fun checkNow() {
        if (_checking.value) return
        _checking.value = true
        viewModelScope.launch {
            val result = try {
                repo.checkWatches()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } finally {
                _checking.value = false
            }
            _messages.trySend(UiMessage.CheckDone(result))
        }
    }

    companion object {
        private const val AUTO_REFRESH_AFTER_MS = 60 * 60 * 1000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PassvarslingApp
                AppViewModel(app.container.repository)
            }
        }
    }
}
