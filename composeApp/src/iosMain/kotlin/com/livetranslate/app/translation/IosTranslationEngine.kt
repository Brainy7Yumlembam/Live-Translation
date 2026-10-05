package com.livetranslate.app.translation

import com.livetranslate.app.model.SupportedLanguage

/**
 * iOS Translation Engine placeholder.
 *
 * Designed to integrate with iOS ML Kit on-device translation via Swift/CocoaPods or local translation provider.
 */
class IosTranslationEngine : TranslationEngine {

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): TranslationResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return TranslationResult.Success(
                originalText = text,
                translatedText = "",
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage
            )
        }

        val sourceLang = SupportedLanguage.fromCode(sourceLanguage)
        return TranslationResult.Success(
            originalText = text,
            translatedText = "[iOS On-Device Placeholder]: $trimmed",
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage
        )
    }

    override fun close() {}
}
