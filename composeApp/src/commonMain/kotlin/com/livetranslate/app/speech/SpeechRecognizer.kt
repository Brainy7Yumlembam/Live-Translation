package com.livetranslate.app.speech

import com.livetranslate.app.model.SupportedLanguage
import kotlinx.coroutines.flow.StateFlow

/**
 * Common abstraction for platform-specific speech recognizers (Android / iOS).
 */
interface SpeechRecognizer {
    /**
     * Flow of continuous speech recognition states:
     * [SpeechRecognitionState.Idle], [SpeechRecognitionState.Listening],
     * [SpeechRecognitionState.PartialResult], [SpeechRecognitionState.FinalResult],
     * [SpeechRecognitionState.Error], [SpeechRecognitionState.PermissionDenied],
     * [SpeechRecognitionState.LanguageNotSupported]
     */
    val state: StateFlow<SpeechRecognitionState>

    /**
     * Starts listening for speech in the specified [languageCode] (e.g., "ja", "zh", "es", "hi").
     */
    fun startListening(languageCode: String)

    /**
     * Convenience method to start listening using [SupportedLanguage].
     */
    fun startListening(language: SupportedLanguage) = startListening(language.code)

    /**
     * Stops listening and stops the active recognition session.
     */
    fun stopListening()

    /**
     * Releases recognizer resources completely.
     */
    fun release() {}
}
