package com.livetranslate.app.data

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * iOS storage driver persisting table content to the Documents directory.
 */
class IosFileStorageDriver(
    private val fileName: String = "conversation_table.json"
) : StorageDriver {

    private fun getFilePath(): String? {
        val fileManager = NSFileManager.defaultManager
        val urls = fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
        val documentUrl = urls.firstOrNull() as? NSURL ?: return null
        return documentUrl.URLByAppendingPathComponent(fileName)?.path
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun read(): String? {
        val path = getFilePath() ?: return null
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(path)) return null
        return try {
            NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
        } catch (e: Exception) {
            null
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun write(content: String) {
        val path = getFilePath() ?: return
        try {
            val nsString = NSString.create(string = content)
            nsString.writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        } catch (_: Exception) {}
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun delete() {
        val path = getFilePath() ?: return
        val fileManager = NSFileManager.defaultManager
        if (fileManager.fileExistsAtPath(path)) {
            fileManager.removeItemAtPath(path, null)
        }
    }
}
