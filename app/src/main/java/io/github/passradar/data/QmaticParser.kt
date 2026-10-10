package io.github.passradar.data

import io.github.passradar.domain.parseDateOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate

/**
 * Reads the JSON from the police's appointment booking (Qmatic web booking).
 * Throws IllegalArgumentException when the body doesn't look like we expect.
 */
object QmaticParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun services(body: String): List<ServiceType> = objects(body).mapNotNull { o ->
        val id = o.string("publicId") ?: return@mapNotNull null
        val name = clean(o.string("name"))
        val names = o.string("custom")?.let(::localizedNames)
        ServiceType(
            id = id,
            nameNb = names?.first ?: name,
            nameEn = names?.second ?: names?.first ?: name,
            duration = o.int("duration") ?: 10,
            additionalDuration = o.int("additionalDuration") ?: 0,
        )
    }

    fun offices(body: String): List<Office> = objects(body).mapNotNull { o ->
        val id = o.string("id") ?: return@mapNotNull null
        val name = clean(o.string("name")).ifEmpty { return@mapNotNull null }
        Office(
            id = id,
            name = name,
            street = clean(o.string("addressLine1")).trim(',', ' '),
            zip = clean(o.string("addressZip")),
            city = cityName(o.string("addressCity")),
        )
    }

    fun dates(body: String): List<LocalDate> =
        objects(body).mapNotNull { o -> o.string("date")?.let(::parseDateOrNull) }.distinct().sorted()

    fun times(body: String): List<String> =
        objects(body).mapNotNull { o -> o.string("time") }.distinct().sorted()

    /** Lists come either bare or wrapped as {"value": [...]}. */
    private fun objects(body: String): List<JsonObject> {
        val list = when (val root = json.parseToJsonElement(body)) {
            is JsonArray -> root
            is JsonObject -> root["value"] as? JsonArray ?: throw IllegalArgumentException("No list in response")
            else -> throw IllegalArgumentException("No list in response")
        }
        return list.filterIsInstance<JsonObject>()
    }

    /** The "custom" field holds JSON in a string: {"names":{"nb":"…","en":"…"}}. */
    private fun localizedNames(custom: String): Pair<String?, String?>? = try {
        val names = (json.parseToJsonElement(custom) as? JsonObject)?.get("names") as? JsonObject
        names?.let { clean(it.string("nb")).ifEmpty { null } to clean(it.string("en")).ifEmpty { null } }
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun clean(text: String?): String = text.orEmpty().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

    private fun cityName(text: String?): String {
        val city = clean(text)
        return if (city.isNotEmpty() && city == city.uppercase()) {
            city.lowercase().split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
        } else {
            city
        }
    }
}
