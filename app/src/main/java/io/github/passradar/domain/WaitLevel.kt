package io.github.passradar.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How long you'd wait for the first free appointment; drives the colours in list and map. */
enum class WaitLevel {
    Short, Medium, Long, VeryLong, NoneFree, Unknown;

    companion object {
        fun of(checked: Boolean, earliest: LocalDate?, today: LocalDate): WaitLevel {
            if (!checked) return Unknown
            if (earliest == null) return NoneFree
            val days = ChronoUnit.DAYS.between(today, earliest)
            return when {
                days <= 14 -> Short
                days <= 42 -> Medium
                days <= 90 -> Long
                else -> VeryLong
            }
        }
    }
}
