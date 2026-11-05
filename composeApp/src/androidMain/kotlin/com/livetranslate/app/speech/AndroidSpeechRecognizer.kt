package com.livetranslate.app.speech

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer as AndroidPlatformSpeechRecognizer
import androidx.core.content.ContextCompat
import com.livetranslate.app.model.SupportedLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android implementation of [SpeechRecognizer] with continuous listening support
 * and dynamic multi-language recognition / Auto Detect capability.
 *
 * Emits partial results as the user speaks and final results when a sentence/phrase completes.
 * Automatically and safely continues listening across pauses until explicitly stopped by the user.
 */
class AndroidSpeechRecognizer(
    private val context: Context
) : SpeechRecognizer, RecognitionListener {

    private val _state = MutableStateFlow<SpeechRecognitionState>(SpeechRecognitionState.Idle)
    override val state: StateFlow<SpeechRecognitionState> = _state.asStateFlow()

    private var speechRecognizer: AndroidPlatformSpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var userRequestedListening = false
    private var currentLanguageCode: String = "auto"
    private var isSessionActive = false

    private val restartRunnable = Runnable {
        if (userRequestedListening) {
            startListeningInternal(currentLanguageCode)
        }
    }

    override fun startListening(languageCode: String) {
        currentLanguageCode = languageCode
        userRequestedListening = true
        mainHandler.removeCallbacks(restartRunnable)

        mainHandler.post {
            startListeningInternal(languageCode)
        }
    }

    override fun startListening(language: SupportedLanguage) {
        startListening(language.code)
    }

    private fun startListeningInternal(languageCode: String) {
        if (!userRequestedListening) return

        // Verify microphone permission at runtime (especially for Android 13+)
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            _state.value = SpeechRecognitionState.PermissionDenied
            userRequestedListening = false
            return
        }

        val googleComponent = ComponentName(
            "com.google.android.googlequicksearchbox",
            "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
        )
        val isGenericAvailable = AndroidPlatformSpeechRecognizer.isRecognitionAvailable(context)

        if (!isGenericAvailable) {
            val isGoogleServiceAvailable = try {
                val intent = Intent(android.speech.RecognitionService.SERVICE_INTERFACE).setComponent(googleComponent)
                context.packageManager.queryIntentServices(intent, 0).isNotEmpty()
            } catch (e: Exception) {
                false
            }

            if (!isGoogleServiceAvailable) {
                _state.value = SpeechRecognitionState.Error(
                    message = "Speech recognition is not available on this device. Please install or enable Google Speech Services.",
                    isRecoverable = false
                )
                userRequestedListening = false
                return
            }
        }

        val isAuto = languageCode.equals("auto", ignoreCase = true)
        val localeTag = resolveSpeechLocaleTag(languageCode)

        try {
            cleanupRecognizer()

            speechRecognizer = try {
                if (isGenericAvailable) {
                    AndroidPlatformSpeechRecognizer.createSpeechRecognizer(context)
                } else {
                    AndroidPlatformSpeechRecognizer.createSpeechRecognizer(context, googleComponent)
                }
            } catch (e: Exception) {
                AndroidPlatformSpeechRecognizer.createSpeechRecognizer(context, googleComponent)
            }

            speechRecognizer?.setRecognitionListener(this@AndroidSpeechRecognizer)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
                if (!isAuto) {
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, localeTag)
                }
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

                if (isAuto) {
                    // Android 14+ (API 34+) automatic language detection and switching
                    putExtra("android.speech.extra.ENABLE_LANGUAGE_DETECTION", true)
                    putExtra("android.speech.extra.ENABLE_LANGUAGE_SWITCH", true)
                    val allowed = arrayListOf(
                        "en-US", "en-IN", "en-GB", "ja-JP", "zh-CN", "ko-KR", "ru-RU", "hi-IN", "fr-FR", "de-DE", "es-ES"
                    )
                    putStringArrayListExtra("android.speech.extra.LANGUAGE_SWITCH_ALLOWED_LANGUAGES", allowed)
                    putStringArrayListExtra("android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES", allowed)
                }
            }

            speechRecognizer?.startListening(intent)
            isSessionActive = true
            _state.value = SpeechRecognitionState.Listening
        } catch (e: Exception) {
            _state.value = SpeechRecognitionState.Error("Failed to start speech recognition: ${e.message}")
            scheduleRestart(delayMs = 1000L)
        }
    }

    override fun stopListening() {
        userRequestedListening = false
        mainHandler.removeCallbacks(restartRunnable)

        mainHandler.post {
            cleanupRecognizer()
            _state.value = SpeechRecognitionState.Idle
        }
    }

    override fun release() {
        stopListening()
    }

    private fun cleanupRecognizer() {
        isSessionActive = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignore cleanup exceptions
        } finally {
            speechRecognizer = null
        }
    }

    private fun scheduleRestart(delayMs: Long = 300L) {
        mainHandler.removeCallbacks(restartRunnable)
        if (userRequestedListening) {
            mainHandler.postDelayed(restartRunnable, delayMs)
        }
    }

    private fun resolveSpeechLocaleTag(code: String): String = when (code.lowercase()) {
        "ja" -> "ja-JP"
        "zh" -> "zh-CN"
        "ko" -> "ko-KR"
        "ru" -> "ru-RU"
        "hi" -> "hi-IN"
        "fr" -> "fr-FR"
        "de" -> "de-DE"
        "es" -> "es-ES"
        "en" -> "en-US"
        "auto" -> java.util.Locale.getDefault().toLanguageTag()
        else -> code
    }

    // --- RecognitionListener Callbacks ---

    override fun onReadyForSpeech(params: Bundle?) {
        if (userRequestedListening) {
            _state.value = SpeechRecognitionState.Listening
        }
    }

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        isSessionActive = false
    }

    override fun onError(error: Int) {
        isSessionActive = false

        when (error) {
            AndroidPlatformSpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                userRequestedListening = false
                _state.value = SpeechRecognitionState.PermissionDenied
            }
            AndroidPlatformSpeechRecognizer.ERROR_NO_MATCH,
            AndroidPlatformSpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                // Natural pause or silence: automatically continue listening without interrupting user flow
                if (userRequestedListening) {
                    scheduleRestart(delayMs = 250L)
                }
            }
            AndroidPlatformSpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            AndroidPlatformSpeechRecognizer.ERROR_CLIENT -> {
                if (userRequestedListening) {
                    scheduleRestart(delayMs = 400L)
                }
            }
            AndroidPlatformSpeechRecognizer.ERROR_AUDIO -> {
                _state.value = SpeechRecognitionState.Error("Audio recording error")
                if (userRequestedListening) scheduleRestart(delayMs = 800L)
            }
            AndroidPlatformSpeechRecognizer.ERROR_NETWORK,
            AndroidPlatformSpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                _state.value = SpeechRecognitionState.Error("Network error during speech recognition")
                if (userRequestedListening) scheduleRestart(delayMs = 1000L)
            }
            AndroidPlatformSpeechRecognizer.ERROR_SERVER -> {
                _state.value = SpeechRecognitionState.Error("Speech recognition server error")
                if (userRequestedListening) scheduleRestart(delayMs = 1000L)
            }
            else -> {
                _state.value = SpeechRecognitionState.Error("Speech recognition error ($error)")
                if (userRequestedListening) scheduleRestart(delayMs = 500L)
            }
        }
    }

    override fun onResults(results: Bundle?) {
        isSessionActive = false
        val matches = results?.getStringArrayList(AndroidPlatformSpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.trim() ?: ""

        if (text.isNotEmpty()) {
            _state.value = SpeechRecognitionState.FinalResult(text)
        }

        // Continue listening for the next phrase automatically
        if (userRequestedListening) {
            scheduleRestart(delayMs = 250L)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(AndroidPlatformSpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.trim() ?: ""

        if (text.isNotEmpty() && userRequestedListening) {
            _state.value = SpeechRecognitionState.PartialResult(text)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
