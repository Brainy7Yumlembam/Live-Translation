package com.livetranslate.app.speech

import com.livetranslate.app.model.SupportedLanguage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.setActive
import platform.Foundation.NSLocale
import platform.Foundation.localeWithLocaleIdentifier
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus

/**
 * iOS implementation of [SpeechRecognizer] using Apple's Speech framework and AVAudioEngine.
 */
@OptIn(ExperimentalForeignApi::class)
class IOSSpeechRecognizer : SpeechRecognizer {

    private val _state = MutableStateFlow<SpeechRecognitionState>(SpeechRecognitionState.Idle)
    override val state: StateFlow<SpeechRecognitionState> = _state.asStateFlow()

    private var audioEngine: AVAudioEngine? = null
    private var speechRecognizer: SFSpeechRecognizer? = null
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest? = null
    private var recognitionTask: SFSpeechRecognitionTask? = null

    override fun startListening(languageCode: String) {
        val localeTag = when (languageCode.lowercase()) {
            "ja" -> "ja-JP"
            "zh" -> "zh-CN"
            "ko" -> "ko-KR"
            "ru" -> "ru-RU"
            "hi" -> "hi-IN"
            "fr" -> "fr-FR"
            "de" -> "de-DE"
            "es" -> "es-ES"
            "en" -> "en-US"
            else -> languageCode
        }

        SFSpeechRecognizer.requestAuthorization { authStatus ->
            when (authStatus) {
                SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized -> {
                    startAudioEngineAndRecognition(localeTag)
                }
                SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusDenied,
                SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusRestricted -> {
                    _state.value = SpeechRecognitionState.PermissionDenied
                }
                else -> {
                    _state.value = SpeechRecognitionState.Error("Speech recognition authorization status: $authStatus")
                }
            }
        }
    }

    override fun startListening(language: SupportedLanguage) {
        startListening(language.code)
    }

    private fun startAudioEngineAndRecognition(localeTag: String) {
        stopListeningInternal()

        try {
            val locale = NSLocale.localeWithLocaleIdentifier(localeTag)
            speechRecognizer = SFSpeechRecognizer(locale)

            if (speechRecognizer?.isAvailable() != true) {
                _state.value = SpeechRecognitionState.Error("Speech recognizer is currently unavailable for $localeTag")
                return
            }

            val audioSession = AVAudioSession.sharedInstance()
            audioSession.setCategory(
                AVAudioSessionCategoryPlayAndRecord,
                AVAudioSessionModeMeasurement,
                0u,
                null
            )
            audioSession.setActive(true, null)

            val request = SFSpeechAudioBufferRecognitionRequest().apply {
                shouldReportPartialResults = true
            }
            recognitionRequest = request

            val engine = AVAudioEngine()
            audioEngine = engine
            val inputNode = engine.inputNode

            recognitionTask = speechRecognizer?.recognitionTaskWithRequest(request) { result, error ->
                if (result != null) {
                    val bestTranscription = result.bestTranscription.formattedString
                    if (result.isFinal()) {
                        _state.value = SpeechRecognitionState.FinalResult(bestTranscription)
                    } else {
                        _state.value = SpeechRecognitionState.PartialResult(bestTranscription)
                    }
                }

                if (error != null) {
                    stopListeningInternal()
                    _state.value = SpeechRecognitionState.Error(error.localizedDescription ?: "Speech recognition error")
                }
            }

            val recordingFormat = inputNode.outputFormatForBus(0u)
            inputNode.installTapOnBus(0u, 1024u, recordingFormat) { buffer, _ ->
                if (buffer != null) {
                    request.appendAudioPCMBuffer(buffer)
                }
            }

            engine.prepare()
            engine.startAndReturnError(null)
            _state.value = SpeechRecognitionState.Listening
        } catch (e: Exception) {
            stopListeningInternal()
            _state.value = SpeechRecognitionState.Error("Failed to start iOS audio engine: ${e.message}")
        }
    }

    override fun stopListening() {
        stopListeningInternal()
        _state.value = SpeechRecognitionState.Idle
    }

    override fun release() {
        stopListening()
    }

    private fun stopListeningInternal() {
        try {
            audioEngine?.stop()
            audioEngine?.inputNode?.removeTapOnBus(0u)
            audioEngine = null

            recognitionRequest?.endAudio()
            recognitionRequest = null

            recognitionTask?.cancel()
            recognitionTask = null

            speechRecognizer = null
        } catch (e: Exception) {
            // Ignore cleanup exceptions
        }
    }
}
