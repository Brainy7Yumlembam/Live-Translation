package com.livetranslate.app

import com.livetranslate.app.data.ConversationRepository
import com.livetranslate.app.data.ConversationTable
import com.livetranslate.app.data.InMemoryStorageDriver
import com.livetranslate.app.detection.DefaultLanguageDetector
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
import kotlin.test.assertNotNull
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
        val languagePairs = mutableListOf<Pair<String, String>>()

        override suspend fun translate(
            text: String,
            sourceLanguage: String,
            targetLanguage: String
        ): TranslationResult {
            translatedRequests.add(text)
            languagePairs.add(Pair(sourceLanguage, targetLanguage))
            val translated = when {
                text == "今日はいい天気ですね。" && targetLanguage == "en" -> "It's a nice day today."
                text == "明日は雨かもしれません。" && targetLanguage == "en" -> "It might rain tomorrow."
                text == "今天天气真好。" && targetLanguage == "en" -> "The weather is really nice today."
                text == "Сегодня хорошая погода." && targetLanguage == "en" -> "The weather is good today."
                text == "今日はいい天気ですね。" && targetLanguage == "zh" -> "今天天气真好。"
                text == "It's a nice day today." && targetLanguage == "ja" -> "今日はいい天気ですね。"
                text == "今日はいい天気ですね。" && targetLanguage == "es" -> "Hoy hace buen tiempo."
                text == "明日は雨かもしれません。" && targetLanguage == "es" -> "Mañana podría llover."
                text == "Сегодня хорошая погода." && targetLanguage == "de" -> "Heute ist schönes Wetter."
                text.contains("पागल") && targetLanguage == "en" -> "Some crazy people are here."
                text.contains("तुम्हारे") && targetLanguage == "en" -> "I will put this in your phone."
                (text.contains("शुरू") || text.contains("कब") || text.contains("kab")) && targetLanguage == "en" -> "When did it start."
                else -> "Translated to $targetLanguage: $text"
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

    // --- Scenario 1: Fresh app launch: Auto Detect → English ---
    @Test
    fun testScenario1FreshAppLaunchDefaultsToAutoDetectAndEnglish() = runTest {
        val speechRecognizer = FakeSpeechRecognizer()
        val translationEngine = FakeTranslationEngine()
        val viewModel = LiveTranslateViewModel(translationEngine, speechRecognizer)

        val state = viewModel.uiState.value
        assertEquals(SupportedLanguage.AUTO, state.selectedSourceLanguage, "Default source must be Auto Detect")
        assertTrue(state.isAutoDetect, "Auto detect mode must be enabled by default")
        assertEquals(SupportedLanguage.ENGLISH, state.targetLanguage, "Default target must be English")
        assertFalse(state.isListening)
    }

    // --- Scenario 2: Speak Japanese: source changes to Japanese and English translation appears ---
    @Test
    fun testScenario2SpeakJapaneseChangesSourceToJapaneseAndEnglishTranslationAppears() = runTest {
        val speechRecognizer = FakeSpeechRecognizer()
        val translationEngine = FakeTranslationEngine()
        val viewModel = LiveTranslateViewModel(translationEngine, speechRecognizer)

        speechRecognizer.startListening("auto")
        advanceUntilIdle()

        // User speaks Japanese
        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("今日はいい天気ですね。")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SupportedLanguage.JAPANESE, state.selectedSourceLanguage, "Source language must update to Japanese")
        assertTrue(state.isAutoDetect, "Auto detect should remain active for subsequent speech")
        assertEquals(SupportedLanguage.ENGLISH, state.targetLanguage, "Target language must remain English")
        assertEquals(1, state.segments.size)
        assertEquals("今日はいい天気ですね。", state.segments[0].sourceText)
        assertEquals("It's a nice day today.", state.segments[0].translatedText)
        assertEquals("ja", state.segments[0].sourceLanguage)
        assertEquals("en", state.segments[0].targetLanguage)
    }

    // --- Scenario 3: Speak Chinese: source changes to Chinese and English translation appears ---
    @Test
    fun testScenario3SpeakChineseChangesSourceToChineseAndEnglishTranslationAppears() = runTest {
        val speechRecognizer = FakeSpeechRecognizer()
        val translationEngine = FakeTranslationEngine()
        val viewModel = LiveTranslateViewModel(translationEngine, speechRecognizer)

        speechRecognizer.startListening("auto")
        advanceUntilIdle()

        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("今天天气真好。")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SupportedLanguage.CHINESE, state.selectedSourceLanguage, "Source language must update to Chinese")
        assertEquals(SupportedLanguage.ENGLISH, state.targetLanguage)
        assertEquals(1, state.segments.size)
        assertEquals("今天天气真好。", state.segments[0].sourceText)
        assertEquals("The weather is really nice today.", state.segments[0].translatedText)
        assertEquals("zh", state.segments[0].sourceLanguage)
        assertEquals("en", state.segments[0].targetLanguage)
    }

    // --- Scenario 4: Speak Russian: source changes to Russian and English translation appears ---
    @Test
    fun testScenario4SpeakRussianChangesSourceToRussianAndEnglishTranslationAppears() = runTest {
        val speechRecognizer = FakeSpeechRecognizer()
        val translationEngine = FakeTranslationEngine()
        val viewModel = LiveTranslateViewModel(translationEngine, speechRecognizer)

        speechRecognizer.startListening("auto")
        advanceUntilIdle()

        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("Сегодня хорошая погода.")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SupportedLanguage.RUSSIAN, state.selectedSourceLanguage, "Source language must update to Russian")
        assertEquals(SupportedLanguage.ENGLISH, state.targetLanguage)
        assertEquals(1, state.segments.size)
        assertEquals("Сегодня хорошая погода.", state.segments[0].sourceText)
        assertEquals("The weather is good today.", state.segments[0].translatedText)
        assertEquals("ru", state.segments[0].sourceLanguage)
        assertEquals("en", state.segments[0].targetLanguage)
    }

    // --- Scenario: Speak Romanized Hindi (Hinglish): source changes to Hindi and English translation appears ---
    @Test
    fun testSpeakRomanizedHindiChangesSourceToHindiAndTranslatesToEnglish() = runTest {
        val speechRecognizer = FakeSpeechRecognizer()
        val translationEngine = FakeTranslationEngine()
        val viewModel = LiveTranslateViewModel(translationEngine, speechRecognizer)

        speechRecognizer.startListening("auto")
        advanceUntilIdle()

        // User speaks Romanized Hindi from speech recognizer
        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("kab Shuru Kiya")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SupportedLanguage.HINDI, state.selectedSourceLanguage, "Source language must update to Hindi")
        assertEquals(SupportedLanguage.ENGLISH, state.targetLanguage)
        assertEquals(1, state.segments.size)
        assertEquals("kab Shuru Kiya", state.segments[0].sourceText)
        assertEquals("When did it start.", state.segments[0].translatedText)
        assertEquals("hi", state.segments[0].sourceLanguage)
        assertEquals("en", state.segments[0].targetLanguage)
    }

    // --- Scenario 5: Manually select Japanese → English: Japanese speech is recognized and translated into English ---
    @Test
    fun testScenario5ManuallySelectJapaneseToEnglish() = runTest {
        val speechRecognizer = FakeSpeechRecognizer()
        val translationEngine = FakeTranslationEngine()
        val viewModel = LiveTranslateViewModel(translationEngine, speechRecognizer)

        // Manual selection of Japanese
        viewModel.selectSourceLanguage(SupportedLanguage.JAPANESE)
        advanceUntilIdle()

        val stateBefore = viewModel.uiState.value
        assertEquals(SupportedLanguage.JAPANESE, stateBefore.selectedSourceLanguage)
        assertFalse(stateBefore.isAutoDetect, "Auto detect must be disabled when user manually selects a language")
        assertEquals(SupportedLanguage.ENGLISH, stateBefore.targetLanguage)

        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("今日はいい天気ですね。")
        advanceUntilIdle()

        val stateAfter = viewModel.uiState.value
        assertEquals(SupportedLanguage.JAPANESE, stateAfter.selectedSourceLanguage)
        assertEquals(1, stateAfter.segments.size)
        assertEquals("ja", stateAfter.segments[0].sourceLanguage)
        assertEquals("en", stateAfter.segments[0].targetLanguage)
        assertEquals("It's a nice day today.", stateAfter.segments[0].translatedText)
    }

    // --- Scenario 6: Manually select English → Japanese: English speech is translated into Japanese ---
    @Test
    fun testScenario6ManuallySelectEnglishToJapanese() = runTest {
        val speechRecognizer = FakeSpeechRecognizer()
        val translationEngine = FakeTranslationEngine()
        val viewModel = LiveTranslateViewModel(translationEngine, speechRecognizer)

        viewModel.selectSourceLanguage(SupportedLanguage.ENGLISH)
        viewModel.selectTargetLanguage(SupportedLanguage.JAPANESE)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SupportedLanguage.ENGLISH, state.selectedSourceLanguage)
        assertEquals(SupportedLanguage.JAPANESE, state.targetLanguage)

        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("It's a nice day today.")
        advanceUntilIdle()

        val stateAfter = viewModel.uiState.value
        assertEquals(1, stateAfter.segments.size)
        assertEquals("en", stateAfter.segments[0].sourceLanguage)
        assertEquals("ja", stateAfter.segments[0].targetLanguage)
        assertEquals("今日はいい天気ですね。", stateAfter.segments[0].translatedText)
    }

    // --- Scenario 7: Manually select Japanese → Chinese: Japanese speech is translated into Chinese ---
    @Test
    fun testScenario7ManuallySelectJapaneseToChinese() = runTest {
        val speechRecognizer = FakeSpeechRecognizer()
        val translationEngine = FakeTranslationEngine()
        val viewModel = LiveTranslateViewModel(translationEngine, speechRecognizer)

        viewModel.selectSourceLanguage(SupportedLanguage.JAPANESE)
        viewModel.selectTargetLanguage(SupportedLanguage.CHINESE)
        advanceUntilIdle()

        assertEquals(SupportedLanguage.JAPANESE, viewModel.uiState.value.selectedSourceLanguage)
        assertEquals(SupportedLanguage.CHINESE, viewModel.uiState.value.targetLanguage)

        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("今日はいい天気ですね。")
        advanceUntilIdle()

        val segment = viewModel.uiState.value.segments[0]
        assertEquals("ja", segment.sourceLanguage)
        assertEquals("zh", segment.targetLanguage)
        assertEquals("今天天气真好。", segment.translatedText)
    }

    // --- Scenario 8: Change the target language while the session is running: subsequent translations use the new target ---
    @Test
    fun testScenario8ChangeTargetLanguageWhileSessionIsRunning() = runTest {
        val speechRecognizer = FakeSpeechRecognizer()
        val translationEngine = FakeTranslationEngine()
        val viewModel = LiveTranslateViewModel(translationEngine, speechRecognizer)

        viewModel.selectSourceLanguage(SupportedLanguage.JAPANESE)
        // Target is initially English
        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("今日はいい天気ですね。")
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.segments.size)
        assertEquals("en", viewModel.uiState.value.segments[0].targetLanguage)
        assertEquals("It's a nice day today.", viewModel.uiState.value.segments[0].translatedText)

        // Change target language to Spanish while running
        viewModel.selectTargetLanguage(SupportedLanguage.SPANISH)
        advanceUntilIdle()

        assertEquals(SupportedLanguage.SPANISH, viewModel.uiState.value.targetLanguage)
        assertEquals(SupportedLanguage.JAPANESE, viewModel.uiState.value.selectedSourceLanguage, "Source language must not be changed when target changes")

        // Next sentence is translated into Spanish
        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("明日は雨かもしれません。")
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.segments.size)
        assertEquals("es", viewModel.uiState.value.segments[1].targetLanguage)
        assertEquals("Mañana podría llover.", viewModel.uiState.value.segments[1].translatedText)
    }

    // --- Scenario 9: Switch from one spoken language to another while Auto Detect is enabled ---
    @Test
    fun testScenario9SwitchSpokenLanguageWhileAutoDetectEnabled() = runTest {
        val speechRecognizer = FakeSpeechRecognizer()
        val translationEngine = FakeTranslationEngine()
        val viewModel = LiveTranslateViewModel(translationEngine, speechRecognizer)

        // Sentence 1: Japanese
        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("今日はいい天気ですね。")
        advanceUntilIdle()
        assertEquals(SupportedLanguage.JAPANESE, viewModel.uiState.value.selectedSourceLanguage)
        assertTrue(viewModel.uiState.value.isAutoDetect)

        // Sentence 2: Speaker switches to Chinese
        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("今天天气真好。")
        advanceUntilIdle()
        assertEquals(SupportedLanguage.CHINESE, viewModel.uiState.value.selectedSourceLanguage)
        assertTrue(viewModel.uiState.value.isAutoDetect)

        // Sentence 3: Speaker switches to Russian
        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("Сегодня хорошая погода.")
        advanceUntilIdle()
        assertEquals(SupportedLanguage.RUSSIAN, viewModel.uiState.value.selectedSourceLanguage)
        assertTrue(viewModel.uiState.value.isAutoDetect)

        // Sentence 4: Speaker switches to Hindi
        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("kab Shuru Kiya")
        advanceUntilIdle()
        assertEquals(SupportedLanguage.HINDI, viewModel.uiState.value.selectedSourceLanguage)
        assertTrue(viewModel.uiState.value.isAutoDetect)

        // Sentence 5: Speaker switches to English
        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("Where is the train station?")
        advanceUntilIdle()
        assertEquals(SupportedLanguage.ENGLISH, viewModel.uiState.value.selectedSourceLanguage)
        assertTrue(viewModel.uiState.value.isAutoDetect)

        // Sentence 6: Speaker switches back to Hindi
        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("aap Kisi box office")
        advanceUntilIdle()
        assertEquals(SupportedLanguage.HINDI, viewModel.uiState.value.selectedSourceLanguage)
        assertTrue(viewModel.uiState.value.isAutoDetect)

        // Sentence 7: Speaker switches back to English
        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("Can you please help me with the luggage?")
        advanceUntilIdle()
        assertEquals(SupportedLanguage.ENGLISH, viewModel.uiState.value.selectedSourceLanguage)
        assertTrue(viewModel.uiState.value.isAutoDetect)

        // Ensure restarting / toggling listening in Auto Detect stays in "auto" mode
        viewModel.toggleListening() // stops listening
        viewModel.toggleListening() // starts listening
        advanceUntilIdle()
        assertEquals("auto", speechRecognizer.lastLanguageCode, "Speech recognizer must remain in 'auto' mode during Auto Detect")

        assertEquals(7, viewModel.uiState.value.segments.size)
        assertEquals("ja", viewModel.uiState.value.segments[0].sourceLanguage)
        assertEquals("zh", viewModel.uiState.value.segments[1].sourceLanguage)
        assertEquals("ru", viewModel.uiState.value.segments[2].sourceLanguage)
        assertEquals("hi", viewModel.uiState.value.segments[3].sourceLanguage)
        assertEquals("en", viewModel.uiState.value.segments[4].sourceLanguage)
        assertEquals("hi", viewModel.uiState.value.segments[5].sourceLanguage)
        assertEquals("en", viewModel.uiState.value.segments[6].sourceLanguage)
    }

    // --- Scenario 10: Unsupported language or unrecognizable speech: graceful error or fallback without crashing ---
    @Test
    fun testScenario10UnrecognizableSpeechFallbackWithoutCrashing() = runTest {
        val speechRecognizer = FakeSpeechRecognizer()
        val translationEngine = FakeTranslationEngine()
        val viewModel = LiveTranslateViewModel(translationEngine, speechRecognizer)

        // Speech with random symbols / numbers that cannot be detected as any language
        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("123456 !@#$%")
        advanceUntilIdle()

        // Should not crash and should record segment safely
        assertEquals(1, viewModel.uiState.value.segments.size)
        assertNotNull(viewModel.uiState.value.segments[0].translatedText)
    }

    // --- Scenario 11: Recognition errors and lifecycle changes do not leave UI stuck ---
    @Test
    fun testScenario11ErrorsAndLifecycleInterruptionsHandledGracefully() = runTest {
        val speechRecognizer = FakeSpeechRecognizer()
        val translationEngine = FakeTranslationEngine()
        val viewModel = LiveTranslateViewModel(translationEngine, speechRecognizer)

        // 1. Toggle listening
        viewModel.toggleListening()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isListening)

        // 2. Error emission
        speechRecognizer.mutableState.value = SpeechRecognitionState.Error("Audio buffer overflow")
        advanceUntilIdle()
        assertEquals("Audio buffer overflow", viewModel.uiState.value.errorMessage)

        // 3. Permission denied
        speechRecognizer.mutableState.value = SpeechRecognitionState.PermissionDenied
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isListening, "Should stop listening on permission denied")
        assertNotNull(viewModel.uiState.value.errorMessage)

        // 4. Lifecycle onPause
        viewModel.toggleListening()
        advanceUntilIdle()
        viewModel.onPause()
        advanceUntilIdle()
        assertFalse(speechRecognizer.isListeningRequested, "onPause must stop active listening")
    }

    // --- User can return to Auto Detect ---
    @Test
    fun testUserCanReturnToAutoDetect() = runTest {
        val speechRecognizer = FakeSpeechRecognizer()
        val translationEngine = FakeTranslationEngine()
        val viewModel = LiveTranslateViewModel(translationEngine, speechRecognizer)

        // Manually select Japanese
        viewModel.selectSourceLanguage(SupportedLanguage.JAPANESE)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isAutoDetect)
        assertEquals(SupportedLanguage.JAPANESE, viewModel.uiState.value.selectedSourceLanguage)

        // Return to Auto Detect
        viewModel.selectSourceLanguage(SupportedLanguage.AUTO)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isAutoDetect, "Auto detect should be re-enabled")
        assertEquals(SupportedLanguage.AUTO, viewModel.uiState.value.selectedSourceLanguage)
    }

    // --- Target language persistence across source changes ---
    @Test
    fun testTargetLanguagePersistedAcrossSourceLanguageChanges() = runTest {
        val speechRecognizer = FakeSpeechRecognizer()
        val translationEngine = FakeTranslationEngine()
        val viewModel = LiveTranslateViewModel(translationEngine, speechRecognizer)

        viewModel.selectTargetLanguage(SupportedLanguage.GERMAN)
        advanceUntilIdle()
        assertEquals(SupportedLanguage.GERMAN, viewModel.uiState.value.targetLanguage)

        // Change source multiple times
        viewModel.selectSourceLanguage(SupportedLanguage.FRENCH)
        assertEquals(SupportedLanguage.GERMAN, viewModel.uiState.value.targetLanguage)

        viewModel.selectSourceLanguage(SupportedLanguage.AUTO)
        assertEquals(SupportedLanguage.GERMAN, viewModel.uiState.value.targetLanguage)

        // Spoken Russian
        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("Сегодня хорошая погода.")
        advanceUntilIdle()

        val segment = viewModel.uiState.value.segments[0]
        assertEquals("ru", segment.sourceLanguage)
        assertEquals("de", segment.targetLanguage, "Subsequent translation must preserve user's German target")
        assertEquals("Heute ist schönes Wetter.", segment.translatedText)
    }

    // --- Existing regression tests ---
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

    @Test
    fun testHinglishSpeechTranslatesProperlyIntoEnglishWithoutPagalLogsBug() = runTest {
        val speechRecognizer = FakeSpeechRecognizer()
        val translationEngine = FakeTranslationEngine()
        val viewModel = LiveTranslateViewModel(translationEngine, speechRecognizer)

        // User speaks: "Kuchh pagal log Hain Yahan per"
        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("Kuchh pagal log Hain Yahan per")
        advanceUntilIdle()

        assertEquals(SupportedLanguage.HINDI, viewModel.uiState.value.selectedSourceLanguage)
        assertEquals(1, viewModel.uiState.value.segments.size)
        val seg1 = viewModel.uiState.value.segments[0]
        assertEquals("Some crazy people are here.", seg1.translatedText)
        // Ensure translation engine received Devanagari "पागल" and "लोग", not raw Latin "pagal log"
        val translatedRequest1 = translationEngine.translatedRequests[0]
        assertTrue(translatedRequest1.contains("पागल"), "Translation input must contain Devanagari pagal")
        assertTrue(translatedRequest1.contains("लोग"), "Translation input must contain Devanagari log")

        // User speaks: "yah Tumhare ya tumhare phone mein main Dal Dunga na"
        speechRecognizer.mutableState.value = SpeechRecognitionState.FinalResult("yah Tumhare ya tumhare phone mein main Dal Dunga na")
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.segments.size)
        val seg2 = viewModel.uiState.value.segments[1]
        assertEquals("I will put this in your phone.", seg2.translatedText)
        val translatedRequest2 = translationEngine.translatedRequests[1]
        assertTrue(translatedRequest2.contains("तुम्हारे"), "Translation input must contain Devanagari tumhare")
        assertTrue(translatedRequest2.contains("डाल"), "Translation input must contain Devanagari dal")
        assertTrue(translatedRequest2.contains("दूँगा"), "Translation input must contain Devanagari dunga")
    }
}
