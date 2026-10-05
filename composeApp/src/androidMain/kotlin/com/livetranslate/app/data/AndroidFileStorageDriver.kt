package com.livetranslate.app.data

import android.content.Context
import java.io.File

/**
 * Android storage driver persisting table content to app internal storage.
 */
class AndroidFileStorageDriver(
    private val context: Context,
    private val fileName: String = "conversation_table.json"
) : StorageDriver {

    private val lock = Any()

    private val file: File
        get() = File(context.filesDir, fileName)

    override fun read(): String? = synchronized(lock) {
        return try {
            val targetFile = file
            if (targetFile.exists()) {
                targetFile.readText(Charsets.UTF_8)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun write(content: String): Unit = synchronized(lock) {
        try {
            val targetFile = file
            val tempFile = File(context.filesDir, "$fileName.tmp")
            tempFile.writeText(content, Charsets.UTF_8)
            if (tempFile.exists()) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                tempFile.renameTo(targetFile)
            }
        } catch (e: Exception) {
            // Log or fallback to direct write if rename fails
            try {
                file.writeText(content, Charsets.UTF_8)
            } catch (_: Exception) {}
        }
    }

    override fun delete(): Unit = synchronized(lock) {
        try {
            val targetFile = file
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val tempFile = File(context.filesDir, "$fileName.tmp")
            if (tempFile.exists()) {
                tempFile.delete()
            }
        } catch (_: Exception) {}
    }
}
