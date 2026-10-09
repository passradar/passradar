package io.github.passvarsling.domain

import io.github.passvarsling.data.AlertMode
import io.github.passvarsling.data.AppSettings
import io.github.passvarsling.data.Watch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WatchEvaluatorTest {
    private val today = LocalDate.of(2026, 10, 9)
    private fun day(offset: Long) = today.plusDays(offset)
    private fun watch(known: List<LocalDate>? = null, emptyStreak: Int = 0) = Watch(
        officeId = "office",
        officeName = "Bærum politistasjon",
        knownDates = known.orEmpty().map(LocalDate::toString),
        baselineDone = known != null,
        emptyStreak = emptyStreak,
    )

    private fun evaluate(watch: Watch, fetched: List<LocalDate>, deadline: LocalDate? = null) =
        WatchEvaluator.evaluate(watch, fetched, today, now = 1000L, deadline = deadline)

    @Test
    fun `first check only takes a snapshot`() {
        val result = evaluate(watch(known = null), listOf(day(3), day(40)))
        assertNull(result.newSlots)
        assertTrue(result.watch.baselineDone)
        assertEquals(listOf(day(3), day(40)).map { it.toString() }, result.watch.knownDates)
    }

    @Test
    fun `earlier date than before is reported with the previous first date`() {
        val result = evaluate(watch(known = listOf(day(90), day(91))), listOf(day(20), day(90), day(91)))
        assertEquals(listOf(day(20)), result.newSlots?.newDates)
        assertEquals(day(90), result.newSlots?.previousEarliest)
    }

    @Test
    fun `later new dates are not reported without a deadline`() {
        val result = evaluate(watch(known = listOf(day(90))), listOf(day(90), day(120), day(121)))
        assertNull(result.newSlots)
        assertEquals(3, result.watch.knownDates.size)
    }

    @Test
    fun `a cancellation is reported even if an earlier date was booked meanwhile`() {
        // Day 14 was free, someone booked it, and day 16 opened up.
        val result = evaluate(watch(known = listOf(day(14), day(18))), listOf(day(16), day(18)))
        assertEquals(listOf(day(16)), result.newSlots?.newDates)
        assertEquals(day(18), result.newSlots?.previousEarliest)
    }

    @Test
    fun `dates appearing after none were free are reported`() {
        val result = evaluate(watch(known = emptyList()), listOf(day(60), day(61)))
        assertEquals(day(60), result.newSlots?.earliest)
        assertNull(result.newSlots?.previousEarliest)
    }

    @Test
    fun `with a deadline any new date before it counts`() {
        val result = evaluate(watch(known = listOf(day(5))), listOf(day(5), day(20), day(45)), deadline = day(30))
        assertEquals(listOf(day(20)), result.newSlots?.newDates)
    }

    @Test
    fun `dates after the deadline are ignored`() {
        val result = evaluate(watch(known = emptyList()), listOf(day(31), day(60)), deadline = day(30))
        assertNull(result.newSlots)
    }

    @Test
    fun `moving the deadline later does not make known dates look new`() {
        // knownDates holds every free date, not just those inside the old window.
        val result = evaluate(watch(known = listOf(day(40))), listOf(day(40)), deadline = day(50))
        assertNull(result.newSlots)
    }

    @Test
    fun `a single empty answer keeps the snapshot`() {
        val known = listOf(day(10), day(11))
        val first = evaluate(watch(known = known), emptyList())
        assertNull(first.newSlots)
        assertEquals(1, first.watch.emptyStreak)
        assertEquals(known.map { it.toString() }, first.watch.knownDates)

        // The dates coming back right after a hiccup are not news.
        val second = evaluate(first.watch, known)
        assertNull(second.newSlots)
        assertEquals(0, second.watch.emptyStreak)
    }

    @Test
    fun `two empty answers in a row are accepted`() {
        val result = evaluate(watch(known = listOf(day(10)), emptyStreak = 1), emptyList())
        assertTrue(result.watch.knownDates.isEmpty())
        val later = evaluate(result.watch, listOf(day(10)))
        assertNotNull(later.newSlots)
    }

    @Test
    fun `past dates are ignored`() {
        val result = evaluate(watch(known = listOf(day(30))), listOf(day(-2), day(30)))
        assertNull(result.newSlots)
        assertFalse(result.watch.knownDates.contains(day(-2).toString()))
    }

    @Test
    fun `alert rule turns into a deadline`() {
        val settings = AppSettings()
        assertNull(settings.alertDeadline(today))
        assertEquals(day(14), settings.copy(alertMode = AlertMode.Within, alertWithinDays = 14).alertDeadline(today))
        val before = settings.copy(alertMode = AlertMode.Before, alertBeforeDate = day(30).toString())
        assertEquals(day(30), before.alertDeadline(today))
        assertFalse(before.alertExpired(today))
        assertTrue(before.copy(alertBeforeDate = day(-1).toString()).alertExpired(today))
    }

    @Test
    fun `waiting time is shown in days, weeks or months`() {
        assertEquals(CompactWait(3, WaitUnit.Days), WaitTime.compact(3))
        assertEquals(CompactWait(13, WaitUnit.Days), WaitTime.compact(13))
        assertEquals(CompactWait(4, WaitUnit.Weeks), WaitTime.compact(31))
        assertEquals(CompactWait(3, WaitUnit.Months), WaitTime.compact(102))
    }
}
