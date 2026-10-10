package io.github.passradar.data

import io.github.passradar.net.BadResponseException
import io.github.passradar.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * The public API behind the police's appointment booking page. These are the
 * same requests the page itself makes when you pick a service, an office and a
 * day. Every call throws IOException on network or server trouble.
 */
class PolitietApi {

    suspend fun services(): List<ServiceType> =
        get("services;validate=true", QmaticParser::services)

    suspend fun offices(serviceId: String): List<Office> =
        get("branches/available;servicePublicId=$serviceId", QmaticParser::offices)

    suspend fun dates(officeId: String, serviceId: String, slotLength: Int): List<LocalDate> =
        get("branches/$officeId/dates;servicePublicId=$serviceId;customSlotLength=$slotLength", QmaticParser::dates)

    suspend fun times(officeId: String, date: LocalDate, serviceId: String, slotLength: Int): List<String> =
        get("branches/$officeId/dates/$date/times;servicePublicId=$serviceId;customSlotLength=$slotLength", QmaticParser::times)

    private suspend fun <T> get(path: String, parse: (String) -> T): T = withContext(Dispatchers.IO) {
        val body = Http.getJson(BASE_URL + path)
        try {
            parse(body)
        } catch (e: IllegalArgumentException) {
            throw BadResponseException("Could not read response", e)
        }
    }

    companion object {
        private const val BASE_URL = "https://pass-og-id.politiet.no/qmaticwebbooking/rest/schedule/"
        const val BOOKING_URL = "https://pass-og-id.politiet.no/timebestilling/index.html"

        /** The booking page with office and service already chosen. */
        fun bookingUrl(officeId: String, serviceId: String): String =
            "$BOOKING_URL#/preselect/branch/$officeId/services/$serviceId"
    }
}
