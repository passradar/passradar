package io.github.passradar.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * The office list shipped with the app (assets/offices.json, made by
 * tools/update_offices.py). Gives every office a map position and is used
 * whenever the live list can't be fetched. The service list (services.json)
 * comes along, so every service can be picked before the live one is fetched.
 */
class BundledOffices(private val context: Context, private val json: Json) {

    val services: List<ServiceType> by lazy {
        try {
            context.assets.open("services.json").use { QmaticParser.services(it.readBytes().toString(Charsets.UTF_8)) }
        } catch (e: Exception) {
            Log.e("Passradar", "Could not read bundled service list", e)
            emptyList()
        }
    }

    val all: List<Office> by lazy {
        try {
            context.assets.open("offices.json").use { json.decodeFromString<List<Office>>(it.readBytes().toString(Charsets.UTF_8)) }
        } catch (e: Exception) {
            Log.e("Passradar", "Could not read bundled office list", e)
            emptyList()
        }
    }

    private val byId by lazy { all.associateBy { it.id } }

    fun find(id: String): Office? = byId[id]

    fun forService(serviceId: String): List<Office> =
        all.filter { it.services.isEmpty() || serviceId in it.services }

    /** Adds positions to offices from the live list, which has none. */
    fun withPositions(offices: List<Office>): List<Office> = offices.map { office ->
        val bundled = byId[office.id]
        if (office.lat == null && bundled != null) office.copy(lat = bundled.lat, lon = bundled.lon) else office
    }
}
