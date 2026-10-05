package com.livetranslate.app.data

/**
 * In-memory implementation of [StorageDriver], ideal for testing and preview.
 */
class InMemoryStorageDriver(private var data: String? = null) : StorageDriver {
    override fun read(): String? = data

    override fun write(content: String) {
        data = content
    }

    override fun delete() {
        data = null
    }
}
