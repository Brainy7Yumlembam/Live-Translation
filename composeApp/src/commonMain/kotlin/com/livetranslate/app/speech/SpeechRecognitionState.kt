package com.livetranslate.app.speech

/**
 * Lifecycle states emitted by platform-specific speech recognizers.
 */
sealed interface SpeechRecognitionState {
    data object Idle : SpeechRecognitionState
    data object Listening : SpeechRecognitionState
    data class PartialResult(val text: String) : SpeechRecognitionState
    data class FinalResult(val text: String) : SpeechRecognitionState
    data class Error(val message: String, val isRecoverable: Boolean = true) : SpeechRecognitionState
    data object PermissionDenied : SpeechRecognitionState
    data object LanguageNotSupported : SpeechRecognitionState
}
