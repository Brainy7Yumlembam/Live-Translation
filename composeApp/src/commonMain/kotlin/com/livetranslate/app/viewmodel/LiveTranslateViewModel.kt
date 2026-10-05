package com.livetranslate.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livetranslate.app.data.ConversationRepository
import com.livetranslate.app.model.LiveTranslateUiState
import com.livetranslate.app.model.SupportedLanguage
import com.livetranslate.app.model.TranslationSegment
import com.livetranslate.app.speech.SpeechRecognitionState
import com.livetranslate.app.speech.SpeechRecognizer
import com.livetranslate.app.translation.TranslationEngine
import com.livetranslate.app.translation.TranslationResult
import com.livetranslate.app.util.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel driving continuous voice recognition, real-time chunk translation,
 * and 1-week persistent table conversation history.
 */
class LiveTranslateViewModel(
    private val translationEngine: TranslationEngine,
    private val speechRecognizer: SpeechRecognizer? = null,
    private val conversationRepository: ConversationRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTranslateUiState())
    val uiState: StateFlow<LiveTranslateUiState> = _uiState.asStateFlow()

    private var segmentCounter = 0

    init {
        speechRecognizer?.let { observeSpeechRecognition(it) }
        loadPersistedConversations()
    }

    private fun loadPersistedConversations() {
        conversationRepository?.let { repo ->
            viewModelScope.launch {
                val activeSegments = repo.getActiveSegments()
                segmentCounter = activeSegments.size
                if (activeSegments.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            segments = activeSegments,
                            statusMessage = "Loaded ${activeSegments.size} conversation segment(s) (1-week retention)"
                        )
                    }
                }
            }
        }
    }

    private fun observeSpeechRecognition(recognizer: SpeechRecognizer) {
        viewModelScope.launch {
            recognizer.state.collect { state ->
                when (state) {
                    is SpeechRecognitionState.Idle -> {
                        _uiState.update {
                            it.copy(
                                isListening = false,
                                currentSpeechText = "",
                                statusMessage = "Tap microphone to start live listening"
                            )
                        }
                    }
                    is SpeechRecognitionState.Listening -> {
                        _uiState.update {
                            it.copy(
                                isListening = true,
                                statusMessage = "🎤 Listening (${it.selectedSourceLanguage.displayName})...",
                                errorMessage = null
                            )
                        }
                    }
                    is SpeechRecognitionState.PartialResult -> {
                        _uiState.update {
                            it.copy(
                                currentSpeechText = state.text
                            )
                        }
                    }
                    is SpeechRecognitionState.FinalResult -> {
                        val text = state.text.trim()
                        _uiState.update { it.copy(currentSpeechText = "") }

                        if (text.isNotEmpty()) {
                            translateSpokenPhrase(text)
                        }
                    }
                    is SpeechRecognitionState.Error -> {
                        _uiState.update {
                            it.copy(
                                errorMessage = state.message,
                                statusMessage = if (it.isListening) "Speech recognition issue" else "Speech error"
                            )
                        }
                    }
                    is SpeechRecognitionState.PermissionDenied -> {
                        _uiState.update {
                            it.copy(
                                isListening = false,
                                currentSpeechText = "",
                                errorMessage = "Microphone permission is required for voice translation.",
                                statusMessage = "Microphone permission denied"
                            )
                        }
                    }
                    is SpeechRecognitionState.LanguageNotSupported -> {
                        _uiState.update {
                            it.copy(
                                isListening = false,
                                currentSpeechText = "",
                                errorMessage = "Speech recognition is not supported for ${it.selectedSourceLanguage.displayName}.",
                                statusMessage = "Language not supported"
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Translates final recognized speech without stopping continuous listening.
     */
    private fun translateSpokenPhrase(sourceText: String) {
        viewModelScope.launch {
            val sourceLang = _uiState.value.selectedSourceLanguage
            val targetLang = _uiState.value.targetLanguage
            val now = currentTimeMillis()

            _uiState.update {
                it.copy(
                    isTranslating = true,
                    statusMessage = "Translating sentence on-device..."
                )
            }

            when (val result = translationEngine.translate(sourceText, sourceLang.code, targetLang.code)) {
                is TranslationResult.Success -> {
                    segmentCounter++
                    val segment = TranslationSegment(
                        id = "seg_${now}_${segmentCounter}",
                        sourceText = result.originalText,
                        translatedText = result.translatedText,
                        sourceLanguage = sourceLang.code,
                        targetLanguage = targetLang.code,
                        timestamp = now
                    )

                    conversationRepository?.saveSegment(segment)

                    _uiState.update {
                        it.copy(
                            isTranslating = false,
                            segments = it.segments + segment,
                            statusMessage = if (it.isListening) "🎤 Listening (${sourceLang.displayName})..." else "Translation ready"
                        )
                    }
                }
                is TranslationResult.Error -> {
                    // Preserve recognized source text even if translation encountered an error
                    segmentCounter++
                    val errorSegment = TranslationSegment(
                        id = "seg_${now}_${segmentCounter}",
                        sourceText = sourceText,
                        translatedText = "[Translation error: ${result.message}]",
                        sourceLanguage = sourceLang.code,
                        targetLanguage = targetLang.code,
                        timestamp = now
                    )

                    conversationRepository?.saveSegment(errorSegment)

                    _uiState.update {
                        it.copy(
                            isTranslating = false,
                            segments = it.segments + errorSegment,
                            errorMessage = result.message,
                            statusMessage = if (it.isListening) "🎤 Listening (${sourceLang.displayName})..." else "Translation error"
                        )
                    }
                }
            }
        }
    }

    fun toggleListening() {
        val recognizer = speechRecognizer ?: return
        if (_uiState.value.isListening) {
            recognizer.stopListening()
            _uiState.update {
                it.copy(
                    isListening = false,
                    currentSpeechText = "",
                    statusMessage = "Listening stopped"
                )
            }
        } else {
            val lang = _uiState.value.selectedSourceLanguage
            _uiState.update {
                it.copy(
                    isListening = true,
                    currentSpeechText = "",
                    errorMessage = null,
                    statusMessage = "🎤 Starting to listen in ${lang.displayName}..."
                )
            }
            recognizer.startListening(lang.code)
        }
    }

    fun selectSourceLanguage(language: SupportedLanguage) {
        val wasListening = _uiState.value.isListening
        if (wasListening) {
            speechRecognizer?.stopListening()
        }

        _uiState.update {
            it.copy(
                selectedSourceLanguage = language,
                currentSpeechText = "",
                errorMessage = null,
                statusMessage = if (wasListening) "Switched to ${language.displayName}. Resuming listening..." else "Selected ${language.displayName}"
            )
        }

        if (wasListening) {
            speechRecognizer?.startListening(language.code)
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text, errorMessage = null) }
    }

    fun toggleManualInputExpanded() {
        _uiState.update { it.copy(isManualInputExpanded = !it.isManualInputExpanded) }
    }

    fun translateManualText() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter text to translate") }
            return
        }

        viewModelScope.launch {
            val sourceLang = _uiState.value.selectedSourceLanguage
            val targetLang = _uiState.value.targetLanguage
            val now = currentTimeMillis()

            _uiState.update {
                it.copy(
                    isTranslating = true,
                    errorMessage = null,
                    statusMessage = "Translating typed text..."
                )
            }

            when (val result = translationEngine.translate(text, sourceLang.code, targetLang.code)) {
                is TranslationResult.Success -> {
                    segmentCounter++
                    val segment = TranslationSegment(
                        id = "seg_${now}_${segmentCounter}",
                        sourceText = result.originalText,
                        translatedText = result.translatedText,
                        sourceLanguage = sourceLang.code,
                        targetLanguage = targetLang.code,
                        timestamp = now
                    )

                    conversationRepository?.saveSegment(segment)

                    _uiState.update {
                        it.copy(
                            isTranslating = false,
                            inputText = "",
                            segments = it.segments + segment,
                            statusMessage = "Translation complete"
                        )
                    }
                }
                is TranslationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isTranslating = false,
                            errorMessage = result.message,
                            statusMessage = "Translation failed"
                        )
                    }
                }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            conversationRepository?.clearHistory()
            _uiState.update {
                it.copy(
                    segments = emptyList(),
                    currentSpeechText = "",
                    errorMessage = null,
                    statusMessage = "History cleared"
                )
            }
        }
    }

    fun purgeExpiredConversations() {
        conversationRepository?.let { repo ->
            viewModelScope.launch {
                val deletedCount = repo.autoDeleteExpired()
                if (deletedCount > 0) {
                    val active = repo.getActiveSegments()
                    _uiState.update {
                        it.copy(segments = active)
                    }
                }
            }
        }
    }

    fun onPause() {
        if (_uiState.value.isListening) {
            speechRecognizer?.stopListening()
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.release()
        translationEngine.close()
    }
}
