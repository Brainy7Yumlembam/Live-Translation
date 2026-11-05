package com.livetranslate.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livetranslate.app.data.ConversationRepository
import com.livetranslate.app.detection.DefaultLanguageDetector
import com.livetranslate.app.detection.HindiTransliterator
import com.livetranslate.app.detection.LanguageDetector
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
 * automatic language detection, and 1-week persistent table conversation history.
 */
class LiveTranslateViewModel(
    private val translationEngine: TranslationEngine,
    private val speechRecognizer: SpeechRecognizer? = null,
    private val conversationRepository: ConversationRepository? = null,
    private val languageDetector: LanguageDetector = DefaultLanguageDetector()
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
                        val currentSource = _uiState.value.selectedSourceLanguage
                        val langLabel = if (_uiState.value.isAutoDetect && currentSource == SupportedLanguage.AUTO) {
                            "Auto Detect"
                        } else {
                            currentSource.displayName
                        }
                        _uiState.update {
                            it.copy(
                                isListening = true,
                                statusMessage = "🎤 Listening ($langLabel)...",
                                errorMessage = null
                            )
                        }
                    }
                    is SpeechRecognitionState.PartialResult -> {
                        val text = state.text
                        var updatedSource = _uiState.value.selectedSourceLanguage
                        var updatedDetected = _uiState.value.detectedLanguage

                        // Eagerly detect language on partial speech if in auto mode and confident
                        if (_uiState.value.isAutoDetect && text.length >= 3) {
                            val detected = languageDetector.detectLanguage(text)
                            if (detected != null) {
                                updatedSource = detected
                                updatedDetected = detected
                            }
                        }

                        _uiState.update {
                            it.copy(
                                currentSpeechText = text,
                                selectedSourceLanguage = updatedSource,
                                detectedLanguage = updatedDetected
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
                        val currentSource = _uiState.value.selectedSourceLanguage
                        val langName = if (_uiState.value.isAutoDetect && currentSource == SupportedLanguage.AUTO) "Auto Detect" else currentSource.displayName
                        _uiState.update {
                            it.copy(
                                isListening = false,
                                currentSpeechText = "",
                                errorMessage = "Speech recognition is not supported for $langName.",
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
            val currentState = _uiState.value
            var sourceLang = currentState.selectedSourceLanguage

            // In Auto Detect mode, identify language from the recognized final speech text
            if (currentState.isAutoDetect) {
                val detected = languageDetector.detectLanguage(sourceText)
                if (detected != null) {
                    sourceLang = detected
                    _uiState.update {
                        it.copy(
                            selectedSourceLanguage = detected,
                            detectedLanguage = detected
                        )
                    }
                }
            }

            // Fallback if source language is still AUTO (unrecognizable speech)
            if (sourceLang == SupportedLanguage.AUTO) {
                sourceLang = SupportedLanguage.ENGLISH
            }

            val targetLang = _uiState.value.targetLanguage
            val now = currentTimeMillis()

            _uiState.update {
                it.copy(
                    isTranslating = true,
                    statusMessage = "Translating sentence (${sourceLang.displayName} → ${targetLang.displayName})..."
                )
            }

            val textToTranslate = if (sourceLang == SupportedLanguage.HINDI) {
                HindiTransliterator.toDevanagari(sourceText)
            } else {
                sourceText
            }

            when (val result = translationEngine.translate(textToTranslate, sourceLang.code, targetLang.code)) {
                is TranslationResult.Success -> {
                    segmentCounter++
                    val segment = TranslationSegment(
                        id = "seg_${now}_${segmentCounter}",
                        sourceText = sourceText,
                        translatedText = result.translatedText,
                        sourceLanguage = sourceLang.code,
                        targetLanguage = targetLang.code,
                        timestamp = now
                    )

                    conversationRepository?.saveSegment(segment)

                    _uiState.update {
                        val isListening = it.isListening
                        val currentLang = it.selectedSourceLanguage
                        val langLabel = if (it.isAutoDetect && currentLang == SupportedLanguage.AUTO) "Auto Detect" else currentLang.displayName
                        it.copy(
                            isTranslating = false,
                            segments = it.segments + segment,
                            statusMessage = if (isListening) "🎤 Listening ($langLabel)..." else "Translation ready"
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
                        val isListening = it.isListening
                        val currentLang = it.selectedSourceLanguage
                        val langLabel = if (it.isAutoDetect && currentLang == SupportedLanguage.AUTO) "Auto Detect" else currentLang.displayName
                        it.copy(
                            isTranslating = false,
                            segments = it.segments + errorSegment,
                            errorMessage = result.message,
                            statusMessage = if (isListening) "🎤 Listening ($langLabel)..." else "Translation error"
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
            val isAuto = _uiState.value.isAutoDetect
            val lang = _uiState.value.selectedSourceLanguage
            val codeToListen = if (isAuto) "auto" else lang.code
            val langLabel = if (isAuto) "Auto Detect" else lang.displayName

            _uiState.update {
                it.copy(
                    isListening = true,
                    currentSpeechText = "",
                    errorMessage = null,
                    statusMessage = "🎤 Starting to listen in $langLabel..."
                )
            }
            recognizer.startListening(codeToListen)
        }
    }

    /**
     * Updates the source language selection.
     * If [SupportedLanguage.AUTO] is passed, automatic detection is enabled.
     * If a specific language is passed, automatic detection is disabled and that language is used.
     */
    fun selectSourceLanguage(language: SupportedLanguage) {
        val wasListening = _uiState.value.isListening
        if (wasListening) {
            speechRecognizer?.stopListening()
        }

        val isAuto = (language == SupportedLanguage.AUTO)

        _uiState.update {
            it.copy(
                selectedSourceLanguage = language,
                isAutoDetect = isAuto,
                detectedLanguage = null,
                currentSpeechText = "",
                errorMessage = null,
                statusMessage = if (isAuto) {
                    if (wasListening) "Auto Detect enabled. Resuming listening..." else "Auto Detect enabled"
                } else {
                    if (wasListening) "Switched to ${language.displayName}. Resuming listening..." else "Selected ${language.displayName}"
                }
            )
        }

        if (wasListening) {
            val codeToListen = if (isAuto) "auto" else language.code
            speechRecognizer?.startListening(codeToListen)
        }
    }

    /**
     * Updates the target language selection.
     * Does not affect the source language selection or automatic detection state.
     */
    fun selectTargetLanguage(language: SupportedLanguage) {
        _uiState.update {
            it.copy(
                targetLanguage = language,
                errorMessage = null,
                statusMessage = "Target language set to ${language.displayName}"
            )
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
            val currentState = _uiState.value
            var sourceLang = currentState.selectedSourceLanguage

            if (currentState.isAutoDetect) {
                val detected = languageDetector.detectLanguage(text)
                if (detected != null) {
                    sourceLang = detected
                    _uiState.update {
                        it.copy(
                            selectedSourceLanguage = detected,
                            detectedLanguage = detected
                        )
                    }
                } else if (sourceLang == SupportedLanguage.AUTO) {
                    _uiState.update {
                        it.copy(
                            errorMessage = "Could not detect language. Please select a source language manually or type a complete phrase.",
                            statusMessage = "Language detection failed"
                        )
                    }
                    return@launch
                }
            }

            val targetLang = _uiState.value.targetLanguage
            val now = currentTimeMillis()

            _uiState.update {
                it.copy(
                    isTranslating = true,
                    errorMessage = null,
                    statusMessage = "Translating typed text (${sourceLang.displayName} → ${targetLang.displayName})..."
                )
            }

            val textToTranslate = if (sourceLang == SupportedLanguage.HINDI) {
                HindiTransliterator.toDevanagari(text)
            } else {
                text
            }

            when (val result = translationEngine.translate(textToTranslate, sourceLang.code, targetLang.code)) {
                is TranslationResult.Success -> {
                    segmentCounter++
                    val segment = TranslationSegment(
                        id = "seg_${now}_${segmentCounter}",
                        sourceText = text,
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
