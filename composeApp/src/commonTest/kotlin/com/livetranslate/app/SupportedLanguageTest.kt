package com.livetranslate.app

import com.livetranslate.app.model.SupportedLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SupportedLanguageTest {

    @Test
    fun testSupportedSourceLanguagesContainsAllMLKitLanguages() {
        val languages = SupportedLanguage.SUPPORTED_SOURCE_LANGUAGES
        val codes = languages.map { it.code }.toSet()

        assertTrue("ja" in codes, "Japanese must be supported in ML Kit")
        assertTrue("zh" in codes, "Chinese must be supported in ML Kit")
        assertTrue("ko" in codes, "Korean must be supported in ML Kit")
        assertTrue("ru" in codes, "Russian must be supported in ML Kit")
        assertTrue("hi" in codes, "Hindi must be supported in ML Kit")
        assertTrue("fr" in codes, "French must be supported in ML Kit")
        assertTrue("de" in codes, "German must be supported in ML Kit")
        assertTrue("es" in codes, "Spanish must be supported in ML Kit")
    }

    @Test
    fun testTargetLanguageIsEnglish() {
        assertEquals("en", SupportedLanguage.ENGLISH.code)
    }

    @Test
    fun testFromCodeResolution() {
        val ja = SupportedLanguage.fromCode("ja")
        assertNotNull(ja)
        assertEquals("Japanese", ja.displayName)

        val hi = SupportedLanguage.fromCode("HI")
        assertNotNull(hi)
        assertEquals("Hindi", hi.displayName)
    }
}
