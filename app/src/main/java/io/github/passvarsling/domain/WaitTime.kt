package io.github.passvarsling.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class WaitUnit { Days, Weeks, Months }

/** A waiting time short enough for a badge: "3 dager", "4 uker", "3 mnd". */
data class CompactWait(val value: Long, val unit: WaitUnit)

object WaitTime {
    fun days(today: LocalDate, date: LocalDate): Long = ChronoUnit.DAYS.between(today, date).coerceAtLeast(0)

    fun compact(days: Long): CompactWait = when {
        days < 14 -> CompactWait(days, WaitUnit.Days)
        days < 63 -> CompactWait(days / 7, WaitUnit.Weeks)
        else -> CompactWait(days / 30, WaitUnit.Months)
    }
}

/** Distance in kilometres between two points on Earth. */
fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * 6371.0 * asin(sqrt(a))
}
