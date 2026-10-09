package io.github.passvarsling.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "passvarsling",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Serializable
data class ServiceCache(val fetchedAt: Long, val services: List<ServiceType>)

/** Live office list for one service. An empty list records a failed attempt. */
@Serializable
data class OfficeCache(val serviceId: String, val fetchedAt: Long, val offices: List<Office>)

/** Free dates per office id, valid for one service and appointment length ([key]). */
@Serializable
data class AvailabilityCache(
    val key: String = "",
    val fullRefreshAt: Long = 0,
    val entries: Map<String, Availability> = emptyMap(),
)

/** Everything the app remembers, stored as JSON in a preferences DataStore. */
class Store(context: Context, private val json: Json) {
    private val dataStore = context.applicationContext.dataStore

    private val prefs: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settings: Flow<AppSettings> = read(SETTINGS) { decode<AppSettings>(it) ?: AppSettings() }
    val watches: Flow<List<Watch>> = read(WATCHES) { decode<List<Watch>>(it) ?: emptyList() }
    val services: Flow<ServiceCache?> = read(SERVICES) { decode<ServiceCache>(it) }
    val offices: Flow<OfficeCache?> = read(OFFICES) { decode<OfficeCache>(it) }
    val availability: Flow<AvailabilityCache> = read(AVAILABILITY) { decode<AvailabilityCache>(it) ?: AvailabilityCache() }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings): AppSettings {
        var result = AppSettings()
        dataStore.edit { p ->
            result = transform(decode<AppSettings>(p[SETTINGS]) ?: AppSettings())
            p[SETTINGS] = json.encodeToString(result)
        }
        return result
    }

    suspend fun updateWatches(transform: (List<Watch>) -> List<Watch>) {
        dataStore.edit { p ->
            p[WATCHES] = json.encodeToString(transform(decode<List<Watch>>(p[WATCHES]) ?: emptyList()))
        }
    }

    suspend fun saveServices(cache: ServiceCache) {
        dataStore.edit { it[SERVICES] = json.encodeToString(cache) }
    }

    suspend fun saveOffices(cache: OfficeCache) {
        dataStore.edit { it[OFFICES] = json.encodeToString(cache) }
    }

    /** Adds [entries] to the cache, dropping old entries made for another [key]. */
    suspend fun mergeAvailability(key: String, entries: Map<String, Availability>, fullRefreshAt: Long? = null) {
        dataStore.edit { p ->
            val current = decode<AvailabilityCache>(p[AVAILABILITY])?.takeIf { it.key == key } ?: AvailabilityCache(key)
            val updated = current.copy(
                entries = current.entries + entries,
                fullRefreshAt = fullRefreshAt ?: current.fullRefreshAt,
            )
            p[AVAILABILITY] = json.encodeToString(updated)
        }
    }

    private fun <T> read(key: Preferences.Key<String>, decode: (String?) -> T): Flow<T> =
        prefs.map { it[key] }.distinctUntilChanged().map(decode)

    private inline fun <reified T> decode(text: String?): T? = text?.let {
        try {
            json.decodeFromString<T>(it)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        val SETTINGS = stringPreferencesKey("settings")
        val WATCHES = stringPreferencesKey("watches")
        val SERVICES = stringPreferencesKey("services")
        val OFFICES = stringPreferencesKey("offices")
        val AVAILABILITY = stringPreferencesKey("availability")
    }
}
