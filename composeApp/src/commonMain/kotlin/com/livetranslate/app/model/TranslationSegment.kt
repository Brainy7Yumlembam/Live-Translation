package com.livetranslate.app.model

import com.livetranslate.app.util.RETENTION_PERIOD_MILLIS
import kotlinx.serialization.Serializable

/**
 * Represents a single translated conversation chunk in memory and persistent storage table.
 *
 * @property id Unique identifier for UI list keying and table primary key
 * @property sourceText Recognized or input source text
 * @property translatedText Translated English text
 * @property sourceLanguage ISO 639-1 code of source language (e.g., "ja")
 * @property targetLanguage ISO 639-1 code of target language (default "en")
 * @property timestamp Epoch timestamp in milliseconds when segment was created
 */
@Serializable
data class TranslationSegment(
    val id: String,
    val sourceText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String = "en",
    val timestamp: Long = 0L
) {
    val sourceLanguageModel: SupportedLanguage? get() = SupportedLanguage.fromCode(sourceLanguage)
    val targetLanguageModel: SupportedLanguage? get() = SupportedLanguage.fromCode(targetLanguage)

    /**
     * Checks if this conversation segment has exceeded the 1-week retention period.
     */
    fun isExpired(currentEpochMillis: Long, retentionMillis: Long = RETENTION_PERIOD_MILLIS): Boolean {
        if (timestamp <= 0L) return false
        return (currentEpochMillis - timestamp) > retentionMillis
    }
}
