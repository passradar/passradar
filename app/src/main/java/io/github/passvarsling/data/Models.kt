package io.github.passvarsling.data

import io.github.passvarsling.domain.parseDateOrNull
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.Locale

@Serializable
data class Office(
    val id: String,
    val name: String,
    val street: String = "",
    val zip: String = "",
    val city: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    /** Ids of the services this office offers. Empty when unknown. */
    val services: List<String> = emptyList(),
) {
    val addressLine: String
        get() = listOf(street, "$zip $city".trim()).filter { it.isNotBlank() }.joinToString(", ")
}

@Serializable
data class ServiceType(
    val id: String,
    val nameNb: String,
    val nameEn: String,
    val duration: Int,
    val additionalDuration: Int,
) {
    /** Appointment length the booking system looks for. Each extra person adds time. */
    fun slotLength(persons: Int): Int = duration + additionalDuration * (persons.coerceAtLeast(1) - 1)

    fun name(locale: Locale): String = if (locale.language == "en") nameEn else nameNb

    companion object {
        val Passport = ServiceType(
            id = "9c9f4070899d6d3f85e2df162aae8e981fb79a81bc7b3f6bf6fb575542a3c4a4",
            nameNb = "Pass og/eller ID-kort til norske statsborgere",
            nameEn = "Passport and/or ID card for Norwegian citizens",
            duration = 10,
            additionalDuration = 5,
        )
    }
}

/** What we know about free dates at one office. */
@Serializable
data class Availability(
    /** First free date (ISO), or null when nothing is free. */
    val earliest: String? = null,
    val count: Int = 0,
    val fetchedAt: Long = 0,
) {
    val earliestDate: LocalDate? get() = earliest?.let(::parseDateOrNull)

    companion object {
        fun from(dates: List<LocalDate>, today: LocalDate, now: Long): Availability {
            val upcoming = dates.filter { !it.isBefore(today) }
            return Availability(upcoming.minOrNull()?.toString(), upcoming.size, now)
        }
    }
}

/** An office the user follows. */
@Serializable
data class Watch(
    val officeId: String,
    val officeName: String,
    /** Free dates seen at the last successful check. */
    val knownDates: List<String> = emptyList(),
    val baselineDone: Boolean = false,
    val emptyStreak: Int = 0,
    val lastChecked: Long = 0,
    val lastError: String? = null,
    val addedAt: Long = 0,
) {
    fun withSnapshot(dates: List<LocalDate>, now: Long): Watch = copy(
        knownDates = dates.map(LocalDate::toString),
        baselineDone = true,
        emptyStreak = 0,
        lastChecked = now,
        lastError = null,
    )
}

/** Which new appointments are worth a notification. Applies to every followed office. */
@Serializable
enum class AlertMode {
    /** Any appointment earlier than the earliest one free right now. */
    Earlier,

    /** Any new appointment within [AppSettings.alertWithinDays] from today. */
    Within,

    /** Any new appointment on or before [AppSettings.alertBeforeDate]. */
    Before,
}

@Serializable
enum class ThemeMode { System, Light, Dark }

@Serializable
data class AppSettings(
    val serviceId: String = ServiceType.Passport.id,
    val persons: Int = 1,
    val intervalMinutes: Int = 15,
    val backgroundEnabled: Boolean = true,
    val alertMode: AlertMode = AlertMode.Earlier,
    val alertWithinDays: Int = 14,
    val alertBeforeDate: String? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    /** Rounded position used for "near me" sorting. Never leaves the phone. */
    val homeLat: Double? = null,
    val homeLon: Double? = null,
    /** Sort offices by distance when a position is known. */
    val sortByDistance: Boolean = true,
    /** Whether the app has asked for the location permission on its own once. */
    val locationAsked: Boolean = false,
    /** Whether the user has been asked once to let the app run in the background. */
    val backgroundPromptShown: Boolean = false,
) {
    val alertBefore: LocalDate? get() = alertBeforeDate?.let(::parseDateOrNull)

    /** Last date a new appointment may have to be worth a notification; null means "earlier than now". */
    fun alertDeadline(today: LocalDate): LocalDate? = when (alertMode) {
        AlertMode.Earlier -> null
        AlertMode.Within -> today.plusDays(alertWithinDays.toLong())
        AlertMode.Before -> alertBefore ?: today.plusYears(10)
    }

    /** True when the chosen date has passed, so no appointment can qualify. */
    fun alertExpired(today: LocalDate): Boolean = alertMode == AlertMode.Before && alertBefore?.isBefore(today) == true

    companion object {
        val Intervals = listOf(15, 30, 60, 120)
        val WithinChoices = listOf(7, 14, 21, 28, 42, 56, 84)
        const val MAX_PERSONS = 9
    }
}
