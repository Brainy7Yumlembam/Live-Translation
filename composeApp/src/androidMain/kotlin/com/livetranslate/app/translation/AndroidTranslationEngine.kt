package com.livetranslate.app.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * On-device Translation Engine powered by Google ML Kit Translation SDK for Android.
 *
 * Translates text locally on the device without sending text to external servers or incurring cloud API costs.
 * Downloads the translation model once per language pair and reuses the cached translator instance.
 */
class AndroidTranslationEngine(
    private val downloadConditions: DownloadConditions = DownloadConditions.Builder().build()
) : TranslationEngine {

    private val translatorCache = ConcurrentHashMap<String, Translator>()
    private val mutex = Mutex()

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): TranslationResult = withContext(Dispatchers.IO) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) {
            return@withContext TranslationResult.Success(
                originalText = text,
                translatedText = "",
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage
            )
        }

        val sourceLangTag = TranslateLanguage.fromLanguageTag(sourceLanguage)
        val targetLangTag = TranslateLanguage.fromLanguageTag(targetLanguage)

        if (sourceLangTag == null) {
            return@withContext TranslationResult.Error(
                originalText = text,
                message = "Unsupported source language for ML Kit on-device translation: '$sourceLanguage'"
            )
        }

        if (targetLangTag == null) {
            return@withContext TranslationResult.Error(
                originalText = text,
                message = "Unsupported target language for ML Kit on-device translation: '$targetLanguage'"
            )
        }

        val pairKey = "$sourceLangTag->$targetLangTag"

        try {
            val translator = getOrCreateTranslator(sourceLangTag, targetLangTag, pairKey)

            // Ensure the required model is downloaded to device storage
            try {
                translator.downloadModelIfNeeded(downloadConditions).await()
            } catch (e: Exception) {
                return@withContext TranslationResult.Error(
                    originalText = text,
                    message = "Failed to download on-device translation model for $sourceLanguage -> $targetLanguage. " +
                            "Please check your internet connection for the initial download. (${e.message})",
                    cause = e
                )
            }

            // Perform fast, local on-device translation
            val translatedText = translator.translate(trimmedText).await()

            TranslationResult.Success(
                originalText = text,
                translatedText = translatedText,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage
            )
        } catch (e: Exception) {
            TranslationResult.Error(
                originalText = text,
                message = "On-device translation error: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
    }

    private suspend fun getOrCreateTranslator(
        sourceLangTag: String,
        targetLangTag: String,
        pairKey: String
    ): Translator {
        translatorCache[pairKey]?.let { return it }

        return mutex.withLock {
            translatorCache[pairKey]?.let { return it }

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLangTag)
                .setTargetLanguage(targetLangTag)
                .build()

            val translator = Translation.getClient(options)
            translatorCache[pairKey] = translator
            translator
        }
    }

    override fun close() {
        for ((_, translator) in translatorCache) {
            try {
                translator.close()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
        translatorCache.clear()
    }
}
