package com.livetranslate.app.model

/**
 * Represents a supported language for Google ML Kit on-device translation.
 *
 * @property code ISO 639-1 language code matching Google ML Kit TranslateLanguage constants (e.g., "ja", "en")
 * @property displayName Human-readable English name
 * @property nativeName Name written in the language's native script
 * @property flagEmoji Regional flag emoji
 */
data class SupportedLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String = displayName,
    val flagEmoji: String = "",
    val isNativeSpeechRecognitionSupported: Boolean = true
) {
    // Backward compatibility helper properties
    val id: String get() = code
    val bcp47Tag: String get() = when (code) {
        "en" -> "en-US"
        "ja" -> "ja-JP"
        "zh" -> "zh-CN"
        "ko" -> "ko-KR"
        "ru" -> "ru-RU"
        "hi" -> "hi-IN"
        "fr" -> "fr-FR"
        "de" -> "de-DE"
        "es" -> "es-ES"
        else -> code
    }

    companion object {
        val AUTO = SupportedLanguage("auto", "Auto Detect", "Auto Detect", "✨")
        val ENGLISH = SupportedLanguage("en", "English", "English", "🇺🇸")
        val JAPANESE = SupportedLanguage("ja", "Japanese", "日本語", "🇯🇵")
        val CHINESE = SupportedLanguage("zh", "Chinese (Simplified)", "中文", "🇨🇳")
        val KOREAN = SupportedLanguage("ko", "Korean", "한국어", "🇰🇷")
        val RUSSIAN = SupportedLanguage("ru", "Russian", "Русский", "🇷🇺")
        val HINDI = SupportedLanguage("hi", "Hindi", "हिन्दी", "🇮🇳")
        val FRENCH = SupportedLanguage("fr", "French", "Français", "🇫🇷")
        val GERMAN = SupportedLanguage("de", "German", "Deutsch", "🇩🇪")
        val SPANISH = SupportedLanguage("es", "Spanish", "Español", "🇪🇸")

        /**
         * List of verified Google ML Kit on-device translation supported languages.
         */
        val ALL_SUPPORTED_LANGUAGES = listOf(
            ENGLISH,
            JAPANESE,
            CHINESE,
            KOREAN,
            RUSSIAN,
            HINDI,
            FRENCH,
            GERMAN,
            SPANISH
        )

        /**
         * Dropdown items for Source Language: Auto Detect followed by all supported languages.
         */
        val SOURCE_DROPDOWN_LANGUAGES = listOf(AUTO) + ALL_SUPPORTED_LANGUAGES

        /**
         * Dropdown items for Target Language: all supported languages.
         */
        val TARGET_DROPDOWN_LANGUAGES = ALL_SUPPORTED_LANGUAGES

        /**
         * Supported source languages for backward compatibility.
         */
        val SUPPORTED_SOURCE_LANGUAGES = ALL_SUPPORTED_LANGUAGES

        /**
         * Alias for initial source languages.
         */
        val INITIAL_SOURCE_LANGUAGES = SUPPORTED_SOURCE_LANGUAGES

        fun fromCode(code: String): SupportedLanguage? =
            if (code.equals("auto", ignoreCase = true)) AUTO
            else ALL_SUPPORTED_LANGUAGES.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}
