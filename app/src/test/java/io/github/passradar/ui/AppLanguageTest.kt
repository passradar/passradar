package io.github.passradar.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class AppLanguageTest {

    @Test
    fun recognisesTheLanguagesTheAppHas() {
        assertEquals(AppLanguage.System, AppLanguage.of(null))
        assertEquals(AppLanguage.English, AppLanguage.of(Locale.forLanguageTag("en-GB")))
        assertEquals(AppLanguage.English, AppLanguage.of(Locale.forLanguageTag("en")))
        assertEquals(AppLanguage.Norwegian, AppLanguage.of(Locale.forLanguageTag("nb-NO")))
        assertEquals(AppLanguage.Norwegian, AppLanguage.of(Locale.forLanguageTag("no")))
        assertEquals(AppLanguage.Norwegian, AppLanguage.of(Locale.forLanguageTag("nn-NO")))
        assertEquals(AppLanguage.System, AppLanguage.of(Locale.forLanguageTag("de-DE")))
    }

    @Test
    fun englishKeepsThePhonesRegion() {
        assertEquals("en-NO", AppLanguage.English.locale(Locale.forLanguageTag("nb-NO"))?.toLanguageTag())
        assertEquals("en-US", AppLanguage.English.locale(Locale.US)?.toLanguageTag())
        assertEquals("en", AppLanguage.English.locale(Locale.forLanguageTag("nb"))?.toLanguageTag())
        assertEquals("en", AppLanguage.English.locale(null)?.toLanguageTag())
    }

    @Test
    fun norwegianIsAlwaysBokmalInNorway() {
        assertEquals("nb-NO", AppLanguage.Norwegian.locale(Locale.US)?.toLanguageTag())
        assertEquals("nb-NO", AppLanguage.Norwegian.locale(null)?.toLanguageTag())
    }

    @Test
    fun systemFollowsThePhone() {
        assertNull(AppLanguage.System.locale(Locale.forLanguageTag("nb-NO")))
    }

    @Test
    fun pickedLanguageIsRecognisedAgain() {
        for (language in listOf(AppLanguage.Norwegian, AppLanguage.English)) {
            assertEquals(language, AppLanguage.of(language.locale(Locale.forLanguageTag("nb-NO"))))
            assertEquals(language, AppLanguage.of(language.locale(Locale.US)))
        }
    }
}
