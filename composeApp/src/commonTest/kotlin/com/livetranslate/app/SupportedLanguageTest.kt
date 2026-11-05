package com.livetranslate.app

import com.livetranslate.app.model.SupportedLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SupportedLanguageTest {

    @Test
    fun testSupportedSourceLanguagesContainsExpectedLanguages() {
        val languages = SupportedLanguage.SUPPORTED_SOURCE_LANGUAGES
        val codes = languages.map { it.code }.toSet()

        assertTrue("ja" in codes, "Japanese must be supported")
        assertTrue("zh" in codes, "Chinese must be supported")
        assertTrue("ko" in codes, "Korean must be supported")
        assertTrue("ru" in codes, "Russian must be supported")
        assertTrue("hi" in codes, "Hindi must be supported")
        assertTrue("fr" in codes, "French must be supported")
        assertTrue("de" in codes, "German must be supported")
        assertTrue("es" in codes, "Spanish must be supported")
        assertTrue("en" in codes, "English must also be selectable as a source language")
    }

    @Test
    fun testTargetLanguageIsEnglish() {
        assertEquals("en", SupportedLanguage.ENGLISH.code)
    }

    @Test
    fun testAutoDetectLanguageConstant() {
        assertEquals("auto", SupportedLanguage.AUTO.code)
        assertEquals("Auto Detect", SupportedLanguage.AUTO.displayName)
        assertTrue(SupportedLanguage.SOURCE_DROPDOWN_LANGUAGES.contains(SupportedLanguage.AUTO))
        assertTrue(!SupportedLanguage.TARGET_DROPDOWN_LANGUAGES.contains(SupportedLanguage.AUTO), "Target dropdown should only contain translatable target languages")
    }

    @Test
    fun testFromCodeResolution() {
        val ja = SupportedLanguage.fromCode("ja")
        assertNotNull(ja)
        assertEquals("Japanese", ja.displayName)

        val hi = SupportedLanguage.fromCode("HI")
        assertNotNull(hi)
        assertEquals("Hindi", hi.displayName)

        val auto = SupportedLanguage.fromCode("auto")
        assertNotNull(auto)
        assertEquals(SupportedLanguage.AUTO, auto)
    }
}
