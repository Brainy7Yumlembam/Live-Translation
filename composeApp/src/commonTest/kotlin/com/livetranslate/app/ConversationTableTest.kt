package com.livetranslate.app

import com.livetranslate.app.data.ConversationRepository
import com.livetranslate.app.data.ConversationTable
import com.livetranslate.app.data.InMemoryStorageDriver
import com.livetranslate.app.model.TranslationSegment
import com.livetranslate.app.util.RETENTION_PERIOD_MILLIS
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationTableTest {

    private val oneDayMillis = 24L * 60 * 60 * 1000L

    @Test
    fun testInsertAndRetrieveConversationSegments() {
        val driver = InMemoryStorageDriver()
        val table = ConversationTable(driver)

        val segment1 = TranslationSegment(
            id = "seg_1",
            sourceText = "こんにちは",
            translatedText = "Hello",
            sourceLanguage = "ja",
            targetLanguage = "en",
            timestamp = 1000000L
        )
        val segment2 = TranslationSegment(
            id = "seg_2",
            sourceText = "ありがとう",
            translatedText = "Thank you",
            sourceLanguage = "ja",
            targetLanguage = "en",
            timestamp = 2000000L
        )

        table.insert(segment1)
        table.insert(segment2)

        val records = table.selectAll()
        assertEquals(2, records.size)
        assertEquals("Hello", records[0].translatedText)
        assertEquals("Thank you", records[1].translatedText)
    }

    @Test
    fun testAutoDeleteExpiredSegmentsAfterOneWeek() {
        val driver = InMemoryStorageDriver()
        val table = ConversationTable(driver)

        val baseTime = 10_000_000_000L

        // Segment 1: Created 8 days ago (Expired)
        val expiredSegment = TranslationSegment(
            id = "seg_expired",
            sourceText = "古いメッセージ",
            translatedText = "Old message",
            sourceLanguage = "ja",
            targetLanguage = "en",
            timestamp = baseTime - (8 * oneDayMillis)
        )

        // Segment 2: Created 3 days ago (Valid - within 1 week)
        val validSegment = TranslationSegment(
            id = "seg_valid",
            sourceText = "最近のメッセージ",
            translatedText = "Recent message",
            sourceLanguage = "ja",
            targetLanguage = "en",
            timestamp = baseTime - (3 * oneDayMillis)
        )

        // Segment 3: Created right now (Valid)
        val freshSegment = TranslationSegment(
            id = "seg_fresh",
            sourceText = "いまのメッセージ",
            translatedText = "Now message",
            sourceLanguage = "ja",
            targetLanguage = "en",
            timestamp = baseTime
        )

        table.insertAll(listOf(expiredSegment, validSegment, freshSegment))
        assertEquals(3, table.count())

        // Run 1-week auto-deletion check
        val deletedCount = table.autoDeleteExpired(nowMillis = baseTime)
        assertEquals(1, deletedCount, "Should delete exactly 1 expired segment (> 7 days old)")

        val remaining = table.selectAll()
        assertEquals(2, remaining.size)
        assertEquals("seg_valid", remaining[0].id)
        assertEquals("seg_fresh", remaining[1].id)
    }

    @Test
    fun testConversationRepositoryAutoDeletesExpiredOnLoad() = runTest {
        val driver = InMemoryStorageDriver()
        val repository = ConversationRepository(driver)

        val currentTime = 20_000_000_000L

        // 10 days ago (expired)
        val expired = TranslationSegment(
            id = "seg_old",
            sourceText = "Bonjour",
            translatedText = "Hello",
            sourceLanguage = "fr",
            targetLanguage = "en",
            timestamp = currentTime - (10 * oneDayMillis)
        )

        // 2 days ago (active)
        val active = TranslationSegment(
            id = "seg_recent",
            sourceText = "Merci",
            translatedText = "Thank you",
            sourceLanguage = "fr",
            targetLanguage = "en",
            timestamp = currentTime - (2 * oneDayMillis)
        )

        val table = ConversationTable(driver)
        table.insertAll(listOf(expired, active))

        // Repository getActiveSegments should auto-delete the 10-day old segment and only return active
        val activeSegments = repository.getActiveSegments(nowMillis = currentTime)
        assertEquals(1, activeSegments.size)
        assertEquals("seg_recent", activeSegments[0].id)

        // Table should also now only have 1 record persisted
        assertEquals(1, table.count())
    }

    @Test
    fun testClearHistoryRemovesAllTableRecords() = runTest {
        val driver = InMemoryStorageDriver()
        val repository = ConversationRepository(driver)

        val segment = TranslationSegment(
            id = "seg_1",
            sourceText = "Test",
            translatedText = "Test",
            sourceLanguage = "en",
            targetLanguage = "en",
            timestamp = com.livetranslate.app.util.currentTimeMillis()
        )

        repository.saveSegment(segment)
        assertEquals(1, repository.getActiveSegments().size)

        repository.clearHistory()
        assertEquals(0, repository.getActiveSegments().size)
    }

    @Test
    fun testIsExpiredMethod() {
        val now = 1000000000L
        val segRecent = TranslationSegment(
            id = "1",
            sourceText = "a",
            translatedText = "b",
            sourceLanguage = "ja",
            timestamp = now - (6 * oneDayMillis)
        )
        val segExpired = TranslationSegment(
            id = "2",
            sourceText = "c",
            translatedText = "d",
            sourceLanguage = "ja",
            timestamp = now - (8 * oneDayMillis)
        )

        assertFalse(segRecent.isExpired(now))
        assertTrue(segExpired.isExpired(now))
    }
}
