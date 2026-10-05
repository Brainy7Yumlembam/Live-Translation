package com.livetranslate.app.data

import com.livetranslate.app.model.TranslationSegment
import com.livetranslate.app.util.RETENTION_PERIOD_MILLIS
import com.livetranslate.app.util.currentTimeMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Data container representing the serializable database table state.
 *
 * Table Schema:
 * - Table Name: `conversation_segments`
 * - Columns:
 *     - `id`: TEXT (PRIMARY KEY)
 *     - `sourceText`: TEXT
 *     - `translatedText`: TEXT
 *     - `sourceLanguage`: TEXT
 *     - `targetLanguage`: TEXT
 *     - `timestamp`: INTEGER (Epoch millis)
 */
@Serializable
data class ConversationTableEntity(
    val version: Int = 1,
    val records: List<TranslationSegment> = emptyList()
)

/**
 * Manages operations on the Conversation Database Table, including CRUD and automatic
 * 1-week expiry cleanup.
 */
class ConversationTable(
    private val driver: StorageDriver,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
) {
    companion object {
        const val TABLE_NAME = "conversation_segments"
    }

    /**
     * Loads all records currently stored in the table.
     */
    fun selectAll(): List<TranslationSegment> {
        val raw = driver.read() ?: return emptyList()
        return try {
            val entity = json.decodeFromString<ConversationTableEntity>(raw)
            entity.records
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Inserts or replaces a segment in the table.
     */
    fun insert(segment: TranslationSegment) {
        val currentRecords = selectAll().toMutableList()
        val existingIndex = currentRecords.indexOfFirst { it.id == segment.id }
        if (existingIndex >= 0) {
            currentRecords[existingIndex] = segment
        } else {
            currentRecords.add(segment)
        }
        saveRecords(currentRecords)
    }

    /**
     * Inserts multiple segments in a single atomic batch operation.
     */
    fun insertAll(segments: Collection<TranslationSegment>) {
        if (segments.isEmpty()) return
        val currentRecords = selectAll().toMutableList()
        for (seg in segments) {
            val idx = currentRecords.indexOfFirst { it.id == seg.id }
            if (idx >= 0) {
                currentRecords[idx] = seg
            } else {
                currentRecords.add(seg)
            }
        }
        saveRecords(currentRecords)
    }

    /**
     * Deletes a segment by its primary key ID.
     */
    fun deleteById(id: String): Boolean {
        val current = selectAll()
        val filtered = current.filterNot { it.id == id }
        if (filtered.size != current.size) {
            saveRecords(filtered)
            return true
        }
        return false
    }

    /**
     * Deletes all segments older than the specified cutoff timestamp.
     *
     * @param cutoffEpochMillis Any segment with timestamp < cutoffEpochMillis will be deleted.
     * @return Number of deleted segments.
     */
    fun deleteOlderThan(cutoffEpochMillis: Long): Int {
        val current = selectAll()
        val valid = current.filter { it.timestamp >= cutoffEpochMillis }
        val deletedCount = current.size - valid.size
        if (deletedCount > 0) {
            saveRecords(valid)
        }
        return deletedCount
    }

    /**
     * Auto-deletes all conversation segments that are older than 1 week (7 days).
     *
     * @param nowMillis Current timestamp (defaults to system time)
     * @param retentionMillis Retention threshold (defaults to 7 days = 604,800,000 ms)
     * @return Number of auto-deleted segments
     */
    fun autoDeleteExpired(
        nowMillis: Long = currentTimeMillis(),
        retentionMillis: Long = RETENTION_PERIOD_MILLIS
    ): Int {
        val cutoff = nowMillis - retentionMillis
        return deleteOlderThan(cutoff)
    }

    /**
     * Clears all records from the table.
     */
    fun deleteAll() {
        driver.delete()
    }

    /**
     * Returns total record count in table.
     */
    fun count(): Int = selectAll().size

    private fun saveRecords(records: List<TranslationSegment>) {
        val entity = ConversationTableEntity(records = records)
        val raw = json.encodeToString(entity)
        driver.write(raw)
    }
}
