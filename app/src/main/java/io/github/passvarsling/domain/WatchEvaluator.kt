package io.github.passvarsling.domain

import io.github.passvarsling.data.Watch
import java.time.LocalDate

/** Dates that just became free at a followed office. */
data class NewSlots(
    val officeId: String,
    val officeName: String,
    val newDates: List<LocalDate>,
    /** Earliest date that was already free before and still is, for "was N days" texts. */
    val previousEarliest: LocalDate?,
) {
    val earliest: LocalDate get() = newDates.first()
}

data class Evaluation(val watch: Watch, val newSlots: NewSlots?)

object WatchEvaluator {

    /**
     * Compares the free dates just fetched for a followed office with the ones seen
     * at the previous check.
     *
     * Without a [deadline] the user hears about it when the earliest free date is one
     * that wasn't free before: a cancellation, or the first free date after none.
     * With a deadline, any newly free date on or before it counts.
     */
    fun evaluate(watch: Watch, fetched: List<LocalDate>, today: LocalDate, now: Long, deadline: LocalDate?): Evaluation {
        val upcoming = fetched.filter { !it.isBefore(today) }.distinct().sorted()
        if (!watch.baselineDone) return Evaluation(watch.withSnapshot(upcoming, now), null)

        val known = watch.knownDates.mapNotNull(::parseDateOrNull).toSet()
        // One empty answer while dates were free a moment ago is more likely a hiccup
        // than everything being booked at once. Keep the old snapshot until it repeats,
        // so the dates don't look new when they come back.
        if (upcoming.isEmpty() && known.any { !it.isBefore(today) } && watch.emptyStreak == 0) {
            return Evaluation(watch.copy(emptyStreak = 1, lastChecked = now, lastError = null), null)
        }

        val inWindow = upcoming.filter { deadline == null || !it.isAfter(deadline) }
        val newDates = inWindow.filter { it !in known }
        val notify = when {
            newDates.isEmpty() -> false
            deadline != null -> true
            else -> inWindow.first() !in known
        }
        val slots = if (notify) {
            NewSlots(watch.officeId, watch.officeName, newDates, inWindow.filter { it in known }.minOrNull())
        } else {
            null
        }
        return Evaluation(watch.withSnapshot(upcoming, now), slots)
    }
}
