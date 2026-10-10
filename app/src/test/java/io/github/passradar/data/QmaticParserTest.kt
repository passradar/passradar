package io.github.passradar.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Samples are shaped like real answers from pass-og-id.politiet.no. */
class QmaticParserTest {

    @Test
    fun `reads services with Norwegian and English names`() {
        val body = """
            [{"additionalDuration":5,"custom":"{\"cost\":{},\"names\":{\"nb\":\"    Pass og/eller ID-kort til norske statsborgere\",\"en\":\"    Passport and/or ID card for Norwegian citizens\"}}","duration":10,"internalId":3,"name":"Pass og/eller ID-kort til norske statsborgere","publicId":"9c9f","qpId":"5"},
             {"additionalDuration":0,"duration":5,"name":"Hente ferdig pass og/eller ID-kort","publicId":"af8d"}]
        """.trimIndent()
        val services = QmaticParser.services(body)
        assertEquals(2, services.size)
        assertEquals("Pass og/eller ID-kort til norske statsborgere", services[0].nameNb)
        assertEquals("Passport and/or ID card for Norwegian citizens", services[0].nameEn)
        assertEquals(15, services[0].slotLength(persons = 2))
        assertEquals("Hente ferdig pass og/eller ID-kort", services[1].nameEn)
    }

    @Test
    fun `reads offices from a wrapped list`() {
        val body = """
            {"value":[
              {"addressCity":"Sandvika","addressLine1":"Kjørboveien 33","addressZip":"1337","id":"e8cf","name":"Bærum politistasjon","timeZone":"Europe/Berlin"},
              {"addressCity":"SANDEFJORD","addressLine1":"Torpveien 125","addressZip":"3241","id":"t0rp","name":"Torp politistasjon"},
              {"addressCity":" Kautokeino","addressLine1":"Ajastealli 2","addressZip":"9520 ","id":"k4u","name":"Kautokeino politistasjon "}
            ]}
        """.trimIndent()
        val offices = QmaticParser.offices(body)
        assertEquals(listOf("e8cf", "t0rp", "k4u"), offices.map { it.id })
        assertEquals("Kjørboveien 33, 1337 Sandvika", offices[0].addressLine)
        assertEquals("Sandefjord", offices[1].city)
        assertEquals("Kautokeino politistasjon", offices[2].name)
        assertEquals("9520", offices[2].zip)
    }

    @Test
    fun `reads dates sorted and without duplicates`() {
        val body = """[{"date":"2027-01-18"},{"date":"2027-01-14"},{"date":"2027-01-18"},{"date":"nonsense"}]"""
        assertEquals(
            listOf(LocalDate.of(2027, 1, 14), LocalDate.of(2027, 1, 18)),
            QmaticParser.dates(body),
        )
    }

    @Test
    fun `reads times`() {
        val body = """[{"date":"2027-01-14","time":"13:20"},{"date":"2027-01-14","time":"13:10"}]"""
        assertEquals(listOf("13:10", "13:20"), QmaticParser.times(body))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects an HTML page`() {
        QmaticParser.dates("<html><body>Request rejected</body></html>")
    }
}
