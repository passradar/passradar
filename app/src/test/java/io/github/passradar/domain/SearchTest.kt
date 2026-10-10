package io.github.passradar.domain

import io.github.passradar.data.Office
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private val tromso = Office(id = "1", name = "Tromsø politistasjon", city = "Tromsø")
    private val baerum = Office(id = "2", name = "Bærum politistasjon", city = "Sandvika")
    private val alesund = Office(id = "3", name = "Ålesund politistasjon", city = "Ålesund")

    @Test
    fun `finds offices typed without Norwegian letters`() {
        assertTrue(matchesSearch(tromso, "tromso"))
        assertTrue(matchesSearch(tromso, "Tromsø"))
        assertTrue(matchesSearch(baerum, "barum"))
        assertTrue(matchesSearch(baerum, "baerum"))
        assertTrue(matchesSearch(baerum, "sandvika"))
        assertTrue(matchesSearch(alesund, "aalesund"))
        assertTrue(matchesSearch(alesund, "alesund"))
    }

    @Test
    fun `empty search matches everything and unrelated text nothing`() {
        assertTrue(matchesSearch(tromso, "  "))
        assertFalse(matchesSearch(tromso, "bergen"))
    }
}
