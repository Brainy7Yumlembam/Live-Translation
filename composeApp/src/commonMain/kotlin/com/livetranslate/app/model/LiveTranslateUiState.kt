package com.livetranslate.app.model

/**
 * State for the LiveTranslate conversation user interface.
 *
 * @property selectedSourceLanguage Selected source language for speaking/typing (defaults to Auto Detect)
 * @property targetLanguage Target language for translation (defaults to English)
 * @property isAutoDetect Whether automatic spoken-language detection is active for source speech
 * @property detectedLanguage The most recently detected source language (if in Auto Detect mode)
 * @property isListening Whether continuous voice listening is active
 * @property currentSpeechText Currently recognized partial speech text (live while speaking)
 * @property inputText Text in the manual input field
 * @property isTranslating Whether a translation request is currently in flight
 * @property segments List of completed conversation translation segments in reverse chronological order
 * @property statusMessage Operational status text (e.g. "🎤 Listening...", "Ready")
 * @property errorMessage Any error or warning message
 * @property isManualInputExpanded Whether the manual text typing panel is expanded
 */
data class LiveTranslateUiState(
    val selectedSourceLanguage: SupportedLanguage = SupportedLanguage.AUTO,
    val targetLanguage: SupportedLanguage = SupportedLanguage.ENGLISH,
    val isAutoDetect: Boolean = true,
    val detectedLanguage: SupportedLanguage? = null,
    val isListening: Boolean = false,
    val currentSpeechText: String = "",
    val inputText: String = "",
    val isTranslating: Boolean = false,
    val segments: List<TranslationSegment> = emptyList(),
    val statusMessage: String = "Ready to listen or translate",
    val errorMessage: String? = null,
    val isManualInputExpanded: Boolean = false
)
