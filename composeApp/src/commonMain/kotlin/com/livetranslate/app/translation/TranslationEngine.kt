package com.livetranslate.app.translation

import com.livetranslate.app.model.SupportedLanguage

/**
 * Common abstraction for pluggable translation engines (e.g., Google ML Kit on-device translation).
 */
interface TranslationEngine {
    /**
     * Translates [text] from [sourceLanguage] code (e.g., "ja") to [targetLanguage] code (e.g., "en").
     */
    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): TranslationResult

    /**
     * Convenience method to translate [text] using [SupportedLanguage] instances.
     */
    suspend fun translate(
        text: String,
        sourceLanguage: SupportedLanguage,
        targetLanguage: SupportedLanguage = SupportedLanguage.ENGLISH
    ): TranslationResult = translate(text, sourceLanguage.code, targetLanguage.code)

    /**
     * Releases any cached translator instances and frees native resources.
     */
    fun close() {}
}
