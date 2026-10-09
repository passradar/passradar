package io.github.passvarsling.ui

import io.github.passvarsling.domain.Dates
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private fun isEnglish(locale: Locale) = locale.language == "en"

    /** "tor. 14. jan. 2027" / "Thu 14 Jan 2027" */
    fun dayDate(date: LocalDate, locale: Locale): String =
        DateTimeFormatter.ofPattern(if (isEnglish(locale)) "EEE d MMM yyyy" else "EEE d. MMM yyyy", locale).format(date)

    /** "14. jan." / "14 Jan" */
    fun shortDate(date: LocalDate, locale: Locale): String =
        DateTimeFormatter.ofPattern(if (isEnglish(locale)) "d MMM" else "d. MMM", locale).format(date)

    /** "Januar 2027" */
    fun monthYear(month: YearMonth, locale: Locale): String =
        DateTimeFormatter.ofPattern("MMMM yyyy", locale).format(month).replaceFirstChar { it.titlecase(locale) }

    /** "14:32" today, otherwise "9. okt. 14:32". */
    fun moment(millis: Long, locale: Locale): String {
        val time = Instant.ofEpochMilli(millis).atZone(Dates.Oslo)
        val clock = DateTimeFormatter.ofPattern("HH:mm", locale).format(time)
        val day = time.toLocalDate()
        return if (day == Dates.today()) clock else "${shortDate(day, locale)} $clock"
    }
}
