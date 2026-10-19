package io.github.passradar.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The lists shipped in assets/, made by tools/update_offices.py. */
class BundledDataTest {

    private fun asset(name: String) = File("src/main/assets/$name").readText()

    private val services = QmaticParser.services(asset("services.json"))

    @Test
    fun `bundled services include the passport service`() {
        val passport = services.single { it.id == ServiceType.Passport.id }
        assertEquals(ServiceType.Passport, passport)
        assertTrue(services.size > 1)
    }

    @Test
    fun `bundled services cover every service the bundled offices offer`() {
        val offices = Json { ignoreUnknownKeys = true }.decodeFromString<List<Office>>(asset("offices.json"))
        val offered = offices.flatMap { it.services }.toSet()
        assertEquals(offered, services.map { it.id }.toSet())
    }
}
