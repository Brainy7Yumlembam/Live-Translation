package com.livetranslate.app

import com.livetranslate.app.data.ConversationRepository
import com.livetranslate.app.data.ConversationTable
import com.livetranslate.app.data.InMemoryStorageDriver
import com.livetranslate.app.model.SupportedLanguage
import com.livetranslate.app.model.TranslationSegment
import com.livetranslate.app.speech.SpeechRecognitionState
import com.livetranslate.app.speech.SpeechRecognizer
import com.livetranslate.app.translation.TranslationEngine
import com.livetranslate.app.translation.TranslationResult
import com.livetranslate.app.viewmodel.LiveTranslateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTranslateViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeSpeechRecognizer : SpeechRecognizer {
        val mutableState = MutableStateFlow<SpeechRecognitionState>(SpeechRecognitionState.Idle)
        override val state: StateFlow<SpeechRecognitionState> = mutableState.asStateFlow()

        var isListeningRequested = false
        var lastLanguageCode: String? = null

        override fun startListening(languageCode: String) {
            isListeningRequested = true
            lastLanguageCode = languageCode
            mutableState.value = SpeechRecognitionState.Listening
        }

        override fun stopListening() {
            isListeningRequested = false
            mutableState.value = SpeechRecognitionState.Idle
        }
    }

    private class FakeTranslationEngine : TranslationEngine {
        val translatedRequests = mutableListOf<String>()

        override suspend fun translate(
            text: String,
            sourceLanguage: String,
            targetLanguage: String
        ): TranslationResult {
            translatedRequests.add(text)
            val translated = when (text) {
                "今日はいい天気ですね。" -> "It's a nice day today."
                "明日は雨かもしれません。" -> "It might rain tomorrow."
                else -> "Translated: $text"
            }
            return TranslationResult.Success(
                originalText = text,
                translatedText = translated,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage
            )
        }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testPartialResultDoesNotTriggerTranslation() = runTest {
        val speechRecognizer = FakeSpeechRecognizer()
        val translationEngine = FakeTranslationEngine()
        val viewModel = LiveTranslateViewModel(translationEngine, speechRecognizer)

        speechRecognizer.startListening("ja")
        advanceUntilIdle()

        speechRecognizer.mutableState.value = SpeechRecognitionState.PartialResult("今日は")
        advanceUntilIdle()

        assertEquals("今日は", viewModel.uiState.value.currentSpeechText)
        assertEquals(0, translationEngine.translatedRequests.size, "Partial speech should NOT be sent to translator")
        assertEquals(0, viewModel.uiState.value.segments.size)
    }

    @Test
    fun testFinalResultTriggersTranslationAndAddsToHistory() = runTest {
        val speechRecognizer = FakeSpeechRecognizer()
        val translationEngine = FakeTranslationEngine()
        val driver = InMemoryStorageDriver()
        val repository = ConversationRepository(driver, testDispatcher)
        val viewModel = LiveTranslateViewModel(translationEngine, speechRecognizer, repository)

        // Sentence 1
        speechRecognizer.mutableState.value = SpeechRecognitionState.PartialResult("今日は")
        advanceUntilIdle()
        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("今日はいい天気ですね。")
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.currentSpeechText, "Current speech should be cleared on final result")
        assertEquals(1, viewModel.uiState.value.segments.size)
        val firstSegment = viewModel.uiState.value.segments[0]
        assertEquals("今日はいい天気ですね。", firstSegment.sourceText)
        assertEquals("It's a nice day today.", firstSegment.translatedText)

        // Verify persisted into repository table
        val persisted = repository.getActiveSegments()
        assertEquals(1, persisted.size)
        assertEquals("今日はいい天気ですね。", persisted[0].sourceText)

        // Sentence 2 (Continuous listening)
        speechRecognizer.mutableState.value = SpeechRecognitionState.PartialResult("明日は")
        advanceUntilIdle()
        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("明日は雨かもしれません。")
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.segments.size)
        val firstSegmentAfter = viewModel.uiState.value.segments[0]
        assertEquals("今日はいい天気ですね。", firstSegmentAfter.sourceText)
        val secondSegment = viewModel.uiState.value.segments[1]
        assertEquals("明日は雨かもしれません。", secondSegment.sourceText)
        assertEquals("It might rain tomorrow.", secondSegment.translatedText)

        // Verify table contains 2 segments
        assertEquals(2, repository.getActiveSegments().size)
    }

    @Test
    fun testManualTextTranslationModeContinuesWorkingAndPersists() = runTest {
        val speechRecognizer = FakeSpeechRecognizer()
        val translationEngine = FakeTranslationEngine()
        val driver = InMemoryStorageDriver()
        val repository = ConversationRepository(driver, testDispatcher)
        val viewModel = LiveTranslateViewModel(translationEngine, speechRecognizer, repository)

        viewModel.onInputTextChanged("今日はいい天気ですね。")
        viewModel.translateManualText()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.segments.size)
        assertEquals("It's a nice day today.", viewModel.uiState.value.segments[0].translatedText)

        // Table persistence verification
        val stored = repository.getActiveSegments()
        assertEquals(1, stored.size)
        assertEquals("It's a nice day today.", stored[0].translatedText)
    }

    @Test
    fun testStartupRestoresPersistedSegmentsAndFiltersExpired() = runTest {
        val driver = InMemoryStorageDriver()
        val table = ConversationTable(driver)
        val now = com.livetranslate.app.util.currentTimeMillis()
        val oneDay = 24L * 60 * 60 * 1000L

        // Saved 9 days ago (expired, > 7 days)
        val expiredSeg = TranslationSegment(
            id = "seg_old",
            sourceText = "Old Japanese",
            translatedText = "Old English",
            sourceLanguage = "ja",
            targetLanguage = "en",
            timestamp = now - (9 * oneDay)
        )
        // Saved 2 days ago (valid, within 1 week)
        val activeSeg = TranslationSegment(
            id = "seg_active",
            sourceText = "Active Japanese",
            translatedText = "Active English",
            sourceLanguage = "ja",
            targetLanguage = "en",
            timestamp = now - (2 * oneDay)
        )
        table.insertAll(listOf(expiredSeg, activeSeg))

        val repository = ConversationRepository(table, testDispatcher)
        val translationEngine = FakeTranslationEngine()
        val speechRecognizer = FakeSpeechRecognizer()

        val viewModel = LiveTranslateViewModel(translationEngine, speechRecognizer, repository)
        advanceUntilIdle()

        // Expired segment must be auto-deleted on load, active segment restored
        assertEquals(1, viewModel.uiState.value.segments.size)
        assertEquals("seg_active", viewModel.uiState.value.segments[0].id)
    }

    @Test
    fun testClearHistoryClearsTableAndState() = runTest {
        val driver = InMemoryStorageDriver()
        val repository = ConversationRepository(driver, testDispatcher)
        val translationEngine = FakeTranslationEngine()
        val speechRecognizer = FakeSpeechRecognizer()
        val viewModel = LiveTranslateViewModel(translationEngine, speechRecognizer, repository)

        viewModel.onInputTextChanged("今日はいい天気ですね。")
        viewModel.translateManualText()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.segments.size)
        assertEquals(1, repository.getActiveSegments().size)

        viewModel.clearHistory()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.segments.size)
        assertEquals(0, repository.getActiveSegments().size)
    }
}
