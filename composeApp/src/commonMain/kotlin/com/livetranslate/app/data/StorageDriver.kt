package com.livetranslate.app.data

/**
 * Interface for abstracting low-level platform table persistence.
 */
interface StorageDriver {
    /**
     * Reads stored table text content, or returns null if not existing.
     */
    fun read(): String?

    /**
     * Persists table text content atomically.
     */
    fun write(content: String)

    /**
     * Deletes stored table content.
     */
    fun delete()
}
