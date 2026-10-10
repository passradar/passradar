package io.github.passradar.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

object Dates {
    /** The booking system works in Norwegian time. */
    val Oslo: ZoneId = ZoneId.of("Europe/Oslo")

    fun today(): LocalDate = LocalDate.now(Oslo)
}

fun parseDateOrNull(text: String): LocalDate? = try {
    LocalDate.parse(text)
} catch (e: DateTimeParseException) {
    null
}
