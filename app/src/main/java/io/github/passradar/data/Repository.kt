package io.github.passradar.data

import android.util.Log
import io.github.passradar.domain.Dates
import io.github.passradar.domain.NewSlots
import io.github.passradar.domain.WatchEvaluator
import io.github.passradar.notify.Notifier
import io.github.passradar.work.Scheduler
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** What to search for: one service for a number of persons. */
data class Query(val service: ServiceType, val persons: Int) {
    val slotLength: Int get() = service.slotLength(persons)
    val cacheKey: String get() = "${service.id}:$slotLength"
}

data class CheckResult(val checked: Int, val failed: Int, val withNewSlots: Int)

data class RefreshResult(val fetched: Int, val failed: Int, val stoppedEarly: Boolean)

class Repository(
    private val api: PolitietApi,
    private val store: Store,
    private val bundled: BundledOffices,
    private val scheduler: Scheduler,
    private val notifier: Notifier,
) {
    val settings: Flow<AppSettings> = store.settings
    val watches: Flow<List<Watch>> = store.watches
    val availability: Flow<AvailabilityCache> = store.availability

    /** When the next background check is planned, if any. */
    val nextCheck: Flow<Long?> = scheduler.nextRun

    val services: Flow<List<ServiceType>> = store.services.map { cache ->
        cache?.services?.takeIf { it.isNotEmpty() } ?: listOf(ServiceType.Passport)
    }

    fun serviceFor(settings: AppSettings, services: List<ServiceType>): ServiceType =
        services.firstOrNull { it.id == settings.serviceId }
            ?: services.firstOrNull { it.id == ServiceType.Passport.id }
            ?: ServiceType.Passport

    suspend fun currentQuery(): Query {
        val settings = store.settings.first()
        return Query(serviceFor(settings, services.first()), settings.persons)
    }

    /** Offices for a service: the live list when we have one, else the bundled one. */
    fun offices(serviceId: String): Flow<List<Office>> = store.offices.map { cache ->
        if (cache != null && cache.serviceId == serviceId && cache.offices.isNotEmpty()) {
            bundled.withPositions(cache.offices)
        } else {
            bundled.forService(serviceId)
        }
    }

    fun findOffice(id: String): Office? = bundled.find(id)

    suspend fun refreshServices() {
        val cache = store.services.first()
        if (cache != null && now() - cache.fetchedAt < SERVICES_MAX_AGE) return
        val services = api.services()
        if (services.isNotEmpty()) store.saveServices(ServiceCache(now(), services))
    }

    /**
     * Fetches the office list at most once a week. The list is big, and the firewall
     * in front of the booking system sometimes cuts such answers off, so a failed
     * attempt counts too and the bundled list fills in meanwhile.
     */
    suspend fun refreshOffices(serviceId: String) {
        val cache = store.offices.first()
        if (cache != null && cache.serviceId == serviceId && now() - cache.fetchedAt < OFFICES_MAX_AGE) return
        val offices = try {
            api.offices(serviceId)
        } catch (e: IOException) {
            Log.i(TAG, "Using bundled office list: ${e.message}")
            emptyList()
        }
        store.saveOffices(OfficeCache(serviceId, now(), offices))
    }

    suspend fun fetchDates(officeId: String): List<LocalDate> {
        val query = currentQuery()
        val dates = api.dates(officeId, query.service.id, query.slotLength)
        saveAvailability(query, mapOf(officeId to Availability.from(dates, Dates.today(), now())))
        return dates
    }

    suspend fun fetchTimes(officeId: String, date: LocalDate): List<String> {
        val query = currentQuery()
        return api.times(officeId, date, query.service.id, query.slotLength)
    }

    /**
     * Fetches free dates for many offices, a couple at a time with a pause between
     * requests, so we don't hammer the booking system. Gives up when it keeps failing.
     */
    suspend fun refreshAvailability(officeIds: List<String>, onResult: (String, Availability?) -> Unit): RefreshResult {
        val query = currentQuery()
        val today = Dates.today()
        val results = ConcurrentHashMap<String, Availability>()
        val failuresInRow = AtomicInteger(0)
        val permits = Semaphore(PARALLEL_REQUESTS)
        coroutineScope {
            for (id in officeIds) {
                launch {
                    permits.withPermit {
                        if (failuresInRow.get() >= MAX_FAILURES_IN_ROW) return@withPermit
                        try {
                            val dates = api.dates(id, query.service.id, query.slotLength)
                            failuresInRow.set(0)
                            val availability = Availability.from(dates, today, now())
                            results[id] = availability
                            onResult(id, availability)
                        } catch (e: IOException) {
                            failuresInRow.incrementAndGet()
                            onResult(id, null)
                        }
                        delay(REQUEST_PAUSE_MS)
                    }
                }
            }
        }
        val stoppedEarly = failuresInRow.get() >= MAX_FAILURES_IN_ROW
        saveAvailability(query, results, fullRefresh = !stoppedEarly && results.isNotEmpty())
        return RefreshResult(results.size, officeIds.size - results.size, stoppedEarly)
    }

    suspend fun saveAvailability(query: Query, entries: Map<String, Availability>, fullRefresh: Boolean = false) {
        if (entries.isEmpty() && !fullRefresh) return
        store.mergeAvailability(query.cacheKey, entries, if (fullRefresh) now() else null)
    }

    suspend fun addWatch(office: Office) = setFollowed(addOffices = listOf(office), removeIds = emptySet())

    suspend fun removeWatch(officeId: String) = setFollowed(addOffices = emptyList(), removeIds = setOf(officeId))

    /**
     * Starts following [addOffices] and stops following [removeIds]. New offices get a
     * first snapshot right away, so a slot that opens before the next background check
     * is reported as new.
     */
    suspend fun setFollowed(addOffices: List<Office>, removeIds: Set<String>) {
        val added = mutableListOf<String>()
        store.updateWatches { list ->
            val kept = list.filterNot { it.officeId in removeIds }
            val existing = kept.mapTo(HashSet()) { it.officeId }
            val fresh = addOffices.filter { it.id !in existing && it.id !in removeIds }
                .map { Watch(officeId = it.id, officeName = it.name, addedAt = now()) }
            added += fresh.map { it.officeId }
            kept + fresh
        }
        reschedule()
        for ((index, officeId) in added.withIndex()) {
            if (index > 0) delay(REQUEST_PAUSE_MS)
            try {
                takeBaseline(officeId)
            } catch (e: IOException) {
                Log.i(TAG, "First check of $officeId failed: ${e.message}")
            }
        }
    }

    private suspend fun takeBaseline(officeId: String) {
        val dates = fetchDates(officeId)
        val today = Dates.today()
        store.updateWatches { list ->
            list.map {
                if (it.officeId == officeId && !it.baselineDone) WatchEvaluator.evaluate(it, dates, today, now(), null).watch else it
            }
        }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings): AppSettings {
        val before = store.settings.first()
        val after = store.updateSettings(transform)
        if (before.serviceId != after.serviceId || before.persons != after.persons) {
            // Another service or appointment length: the old snapshots don't apply.
            store.updateWatches { list ->
                list.map { it.copy(knownDates = emptyList(), baselineDone = false, emptyStreak = 0) }
            }
        }
        reschedule()
        return after
    }

    /** For testing notifications: the next check will see each watch's earliest date as new. */
    suspend fun forgetEarliestKnownDates() {
        store.updateWatches { list -> list.map { it.copy(knownDates = it.knownDates.sorted().drop(1)) } }
    }

    suspend fun reschedule() {
        val settings = store.settings.first()
        val active = store.watches.first().isNotEmpty() && !settings.alertExpired(Dates.today())
        scheduler.apply(settings, active)
    }

    /** Checks every followed office once and notifies about new free dates. */
    suspend fun checkWatches(): CheckResult {
        val query = currentQuery()
        val settings = store.settings.first()
        val today = Dates.today()
        val deadline = settings.alertDeadline(today)
        // Offices that don't offer the chosen service have nothing to find.
        val offered = offices(query.service.id).first().mapTo(HashSet()) { it.id }
        val watches = store.watches.first().filter { offered.isEmpty() || it.officeId in offered }
        if (watches.isEmpty()) return CheckResult(0, 0, 0)

        val fetched = LinkedHashMap<String, List<LocalDate>>()
        val errors = HashMap<String, String>()
        watches.forEachIndexed { index, watch ->
            if (index > 0) delay(REQUEST_PAUSE_MS)
            try {
                fetched[watch.officeId] = api.dates(watch.officeId, query.service.id, query.slotLength)
            } catch (e: IOException) {
                errors[watch.officeId] = e.message ?: e.javaClass.simpleName
            }
        }

        val now = now()
        val found = mutableListOf<NewSlots>()
        store.updateWatches { list ->
            list.map { watch ->
                val dates = fetched[watch.officeId]
                val error = errors[watch.officeId]
                when {
                    dates != null -> {
                        val evaluation = WatchEvaluator.evaluate(watch, dates, today, now, deadline)
                        evaluation.newSlots?.let(found::add)
                        evaluation.watch
                    }
                    error != null -> watch.copy(lastChecked = now, lastError = error)
                    else -> watch
                }
            }
        }
        saveAvailability(query, fetched.mapValues { (_, dates) -> Availability.from(dates, today, now) })

        val withTimes = found.map { slots ->
            val times = try {
                api.times(slots.officeId, slots.earliest, query.service.id, query.slotLength)
            } catch (e: IOException) {
                emptyList()
            }
            slots to times
        }
        notifier.showNewSlots(withTimes, query.service.id, today)
        return CheckResult(checked = fetched.size, failed = errors.size, withNewSlots = found.size)
    }

    private fun now() = System.currentTimeMillis()

    private companion object {
        const val TAG = "Passradar"
        const val SERVICES_MAX_AGE = 24 * 60 * 60 * 1000L
        const val OFFICES_MAX_AGE = 7 * 24 * 60 * 60 * 1000L
        const val PARALLEL_REQUESTS = 2
        const val REQUEST_PAUSE_MS = 300L
        const val MAX_FAILURES_IN_ROW = 6
    }
}
