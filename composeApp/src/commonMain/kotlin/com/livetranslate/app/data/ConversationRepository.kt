package com.livetranslate.app.data

import com.livetranslate.app.model.TranslationSegment
import com.livetranslate.app.util.RETENTION_PERIOD_MILLIS
import com.livetranslate.app.util.currentTimeMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository orchestrating conversation persistence and 1-week auto-deletion lifecycle.
 */
class ConversationRepository(
    private val table: ConversationTable,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    constructor(
        driver: StorageDriver,
        ioDispatcher: CoroutineDispatcher = Dispatchers.Default
    ) : this(ConversationTable(driver), ioDispatcher)

    /**
     * Loads all active (non-expired) conversation segments from table.
     * Also automatically cleans up any expired records older than 1 week.
     */
    suspend fun getActiveSegments(nowMillis: Long = currentTimeMillis()): List<TranslationSegment> = withContext(ioDispatcher) {
        table.autoDeleteExpired(nowMillis)
        table.selectAll().filterNot { it.isExpired(nowMillis) }
    }

    /**
     * Persists a newly created translation segment into the table.
     * Also performs automatic expiry cleanup.
     */
    suspend fun saveSegment(segment: TranslationSegment, nowMillis: Long = currentTimeMillis()): Unit = withContext(ioDispatcher) {
        val segmentToSave = if (segment.timestamp <= 0L) {
            segment.copy(timestamp = nowMillis)
        } else {
            segment
        }
        table.autoDeleteExpired(nowMillis)
        table.insert(segmentToSave)
    }

    /**
     * Clears all conversation segments from the persistent table.
     */
    suspend fun clearHistory(): Unit = withContext(ioDispatcher) {
        table.deleteAll()
    }

    /**
     * Auto-deletes all segments older than 1 week (7 days).
     *
     * @return Count of auto-deleted segments.
     */
    suspend fun autoDeleteExpired(
        nowMillis: Long = currentTimeMillis(),
        retentionMillis: Long = RETENTION_PERIOD_MILLIS
    ): Int = withContext(ioDispatcher) {
        table.autoDeleteExpired(nowMillis, retentionMillis)
    }
}
