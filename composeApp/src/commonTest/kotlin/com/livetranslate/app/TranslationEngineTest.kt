package com.livetranslate.app

import com.livetranslate.app.model.SupportedLanguage
import com.livetranslate.app.translation.TranslationEngine
import com.livetranslate.app.translation.TranslationResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslationEngineTest {

    private class MockLocalTranslationEngine : TranslationEngine {
        override suspend fun translate(
            text: String,
            sourceLanguage: String,
            targetLanguage: String
        ): TranslationResult {
            if (text.isBlank()) {
                return TranslationResult.Success(
                    originalText = text,
                    translatedText = "",
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage
                )
            }
            return TranslationResult.Success(
                originalText = text,
                translatedText = "Translated to $targetLanguage: $text",
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage
            )
        }
    }

    @Test
    fun testTranslateConvenienceMethod() = runTest {
        val engine = MockLocalTranslationEngine()
        val result = engine.translate("今日はいい天気ですね。", SupportedLanguage.JAPANESE, SupportedLanguage.ENGLISH)

        assertTrue(result is TranslationResult.Success)
        assertEquals("今日はいい天気ですね。", result.originalText)
        assertEquals("Translated to en: 今日はいい天気ですね。", result.translatedText)
        assertEquals("ja", result.sourceLanguage)
        assertEquals("en", result.targetLanguage)
    }

    @Test
    fun testEmptyInputTranslation() = runTest {
        val engine = MockLocalTranslationEngine()
        val result = engine.translate("   ", "ja", "en")

        assertTrue(result is TranslationResult.Success)
        assertEquals("", result.translatedText)
    }
}
