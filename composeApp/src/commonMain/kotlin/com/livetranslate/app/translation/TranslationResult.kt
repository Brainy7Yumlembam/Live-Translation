package com.livetranslate.app.translation

/**
 * Result model returned by a [TranslationEngine].
 */
sealed interface TranslationResult {
    data class Success(
        val originalText: String,
        val translatedText: String,
        val sourceLanguage: String,
        val targetLanguage: String
    ) : TranslationResult

    data class Error(
        val originalText: String,
        val message: String,
        val cause: Throwable? = null
    ) : TranslationResult
}
