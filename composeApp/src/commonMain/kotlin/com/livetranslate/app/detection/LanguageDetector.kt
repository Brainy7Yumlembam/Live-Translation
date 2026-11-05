package com.livetranslate.app.detection

import com.livetranslate.app.model.SupportedLanguage

/**
 * Interface for detecting the natural language of recognized speech or typed text.
 */
interface LanguageDetector {
    /**
     * Identifies the [SupportedLanguage] of the provided [text].
     * Returns null if the language cannot be identified with confidence.
     */
    fun detectLanguage(text: String): SupportedLanguage?
}

/**
 * High-performance, offline, on-device language detector implemented in pure Kotlin.
 *
 * Employs Unicode block character script analysis for Asian, Cyrillic, and Indic scripts,
 * combined with diacritic detection and lexical frequency modeling for Latin-script languages
 * (English, Spanish, French, German) as well as Romanized Hindi (Hinglish).
 */
class DefaultLanguageDetector : LanguageDetector {

    override fun detectLanguage(text: String): SupportedLanguage? {
        val trimmed = text.trim()
        if (trimmed.length < 2) return null

        // 1. Script-based identification (unambiguous Unicode character sets)
        var hiraganaCount = 0
        var katakanaCount = 0
        var cjkCount = 0
        var hangulCount = 0
        var cyrillicCount = 0
        var devanagariCount = 0
        var latinCount = 0

        for (ch in trimmed) {
            val code = ch.code
            when {
                // Hiragana: U+3040..U+309F
                code in 0x3040..0x309F -> hiraganaCount++
                // Katakana: U+30A0..U+30FF, Katakana Phonetic Extensions U+31F0..U+31FF
                code in 0x30A0..0x30FF || code in 0x31F0..0x31FF -> katakanaCount++
                // Hangul Syllables: U+AC00..U+D7AF, Hangul Jamo U+1100..U+11FF, Compatibility U+3130..U+318F
                code in 0xAC00..0xD7AF || code in 0x1100..0x11FF || code in 0x3130..0x318F -> hangulCount++
                // Cyrillic: U+0400..U+04FF, U+0500..U+052F
                code in 0x0400..0x04FF || code in 0x0500..0x052F -> cyrillicCount++
                // Devanagari: U+0900..U+097F
                code in 0x0900..0x097F -> devanagariCount++
                // CJK Unified Ideographs: U+4E00..U+9FFF, U+3400..U+4DBF
                code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF || code in 0xF900..0xFAFF -> cjkCount++
                // Latin alphabet and diacritics
                code in 0x0041..0x005A || code in 0x0061..0x007A || code in 0x00C0..0x024F -> latinCount++
            }
        }

        // Japanese: contains Hiragana or Katakana (often interspersed with Kanji)
        if (hiraganaCount > 0 || katakanaCount > 0) {
            return SupportedLanguage.JAPANESE
        }

        // Korean: contains Hangul characters
        if (hangulCount > 0) {
            return SupportedLanguage.KOREAN
        }

        // Russian: Cyrillic script
        if (cyrillicCount > 0) {
            return SupportedLanguage.RUSSIAN
        }

        // Hindi: Devanagari script
        if (devanagariCount > 0) {
            return SupportedLanguage.HINDI
        }

        // Chinese: contains CJK characters without Japanese Kana or Korean Hangul
        if (cjkCount > 0) {
            return SupportedLanguage.CHINESE
        }

        // 2. Latin script language discrimination (English, Spanish, French, German, Romanized Hindi)
        if (latinCount > 0) {
            return detectLatinLanguage(trimmed)
        }

        return null
    }

    private fun detectLatinLanguage(text: String): SupportedLanguage? {
        val lower = text.lowercase()

        var scoreEnglish = 0
        var scoreSpanish = 0
        var scoreFrench = 0
        var scoreGerman = 0
        var scoreHindi = 0

        var hindiMatches = 0
        var spanishMatches = 0
        var frenchMatches = 0
        var germanMatches = 0
        var englishMatches = 0

        // Check for language-defining character markers
        for (ch in lower) {
            when (ch) {
                // Spanish unique markers
                'ñ', '¿', '¡' -> scoreSpanish += 10
                // German unique markers
                'ä', 'ö', 'ü', 'ß' -> scoreGerman += 10
                // French unique markers
                'œ', 'æ', 'ç', 'è', 'ê', 'ë', 'à', 'â', 'î', 'ï', 'ô', 'û', 'ù' -> scoreFrench += 8
            }
        }

        // French apostrophe contractions
        if (FRENCH_ELISIONS.any { lower.contains(it) }) {
            scoreFrench += 8
            frenchMatches++
        }

        // Tokenize words
        val words = lower
            .replace(Regex("[^\\p{L}\\p{Nd}']"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }

        for (word in words) {
            val cleanWord = word.trim('\'')
            if (HINDI_ROMAN_WORDS.contains(cleanWord)) {
                scoreHindi += 4
                hindiMatches++
            }
            if (SPANISH_WORDS.contains(cleanWord)) {
                scoreSpanish += 4
                spanishMatches++
            }
            if (FRENCH_WORDS.contains(cleanWord)) {
                scoreFrench += 4
                frenchMatches++
            }
            if (GERMAN_WORDS.contains(cleanWord)) {
                scoreGerman += 4
                germanMatches++
            }
            if (ENGLISH_WORDS.contains(cleanWord)) {
                scoreEnglish += 4
                englishMatches++
            }
        }

        // Distinct Hindi greetings (e.g. "namaste", "dhanyawad", "shukriya") or multiple Hindi keywords
        val hasHindiGreeting = words.any { it == "namaste" || it == "dhanyawad" || it == "shukriya" }
        if (hasHindiGreeting || (hindiMatches >= 2 && scoreHindi > scoreEnglish)) {
            return SupportedLanguage.HINDI
        }

        val maxScore = maxOf(scoreEnglish, scoreSpanish, scoreFrench, scoreGerman, scoreHindi)
        if (maxScore == 0) return null

        return when {
            scoreSpanish == maxScore && scoreSpanish > 0 -> SupportedLanguage.SPANISH
            scoreFrench == maxScore && scoreFrench > 0 -> SupportedLanguage.FRENCH
            scoreGerman == maxScore && scoreGerman > 0 -> SupportedLanguage.GERMAN
            scoreEnglish == maxScore && scoreEnglish > 0 -> SupportedLanguage.ENGLISH
            else -> SupportedLanguage.ENGLISH
        }
    }

    companion object {
        private val FRENCH_ELISIONS = listOf(
            "aujourd'hui", "aujourd’hui", "c'est", "c’est", "d'un", "d’un", "d'une", "d’une",
            "j'ai", "j’ai", "n'est", "n’est", "qu'il", "qu’il", "l'eau", "l’eau", "l'un", "l’un"
        )

        /**
         * Distinct Romanized Hindi words that do not collide with standard English words.
         */
        private val HINDI_ROMAN_WORDS = hashSetOf(
            "aap", "aapke", "aapka", "aapki", "tum", "tumhen", "tumhe", "tumhara", "tumhari", "tumhare",
            "hamara", "hamari", "hamare", "mera", "meri", "mere", "tera", "teri", "tere",
            "apna", "apni", "apne", "uska", "uski", "uske", "unka", "unki", "unke",
            "kisi", "kuch", "kuchh", "koi", "kaun", "kya", "kyon", "kyu", "kyun",
            "kab", "kahan", "kaha", "kidhar", "kaise", "vahan", "wahan", "yahan", "idhar", "udhar",
            "liye", "saath", "sath", "hai", "hain", "hoon", "hun", "tha", "thi",
            "raha", "rahe", "rahi", "shuru", "suru", "kiya", "karo", "karna", "karenge", "karega",
            "nahin", "nahi", "batao", "bataiye", "bolo", "boliye", "achha", "accha", "bahut", "bohot",
            "theek", "thik", "dhanyawad", "shukriya", "bhai", "namaste", "aaye", "aaya", "aayi",
            "gaya", "gaye", "gayi", "jana", "jao", "jaiye", "dekho", "dekhiye", "bhi", "toh",
            "aur", "lekin", "magar", "agar", "subah", "shaam", "khana", "ghar", "kaam", "baat",
            "samajh", "pata", "chahiye", "suno", "sunie", "pagal", "paagal", "dunga", "doonga"
        )

        private val ENGLISH_WORDS = hashSetOf(
            "the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for", "not",
            "on", "with", "he", "as", "you", "do", "at", "this", "but", "his", "by", "from",
            "they", "we", "say", "her", "she", "or", "an", "will", "my", "one", "all", "would",
            "there", "their", "what", "so", "up", "out", "if", "about", "who", "get", "which",
            "go", "me", "when", "make", "can", "like", "time", "no", "just", "him", "know",
            "take", "people", "into", "year", "your", "good", "some", "could", "them", "see",
            "other", "than", "then", "now", "look", "only", "come", "its", "over", "think",
            "also", "back", "after", "use", "two", "how", "our", "work", "first", "well",
            "way", "even", "new", "want", "because", "any", "these", "give", "day", "most",
            "us", "is", "are", "am", "was", "were", "been", "has", "had", "hello", "hi", "hey",
            "weather", "today", "tomorrow", "yesterday", "nice", "please", "thank", "thanks",
            "morning", "night", "yes", "speak", "translate", "speech", "listen", "today's", "great",
            "asking", "before", "where", "why", "whom", "whose", "should", "must", "may",
            "might", "shall", "does", "did", "doing", "done", "having", "going", "goes", "went",
            "gone", "looking", "looked", "seeing", "saw", "seen", "saying", "said", "tell",
            "telling", "told", "talk", "talking", "talked", "call", "calling", "called",
            "ask", "asked", "answer", "need", "needed", "help", "helping", "helped",
            "feel", "feeling", "felt", "find", "finding", "found", "hear", "hearing", "heard",
            "let", "put", "keep", "kept", "try", "trying", "tried", "start", "stop", "open",
            "close", "show", "showing", "shows", "read", "write", "writing", "written", "understand", "name", "place",
            "friend", "house", "home", "car", "bus", "train", "water", "food", "money",
            "phone", "door", "room", "book", "school", "office", "world", "fine", "okay", "sure"
        )

        private val SPANISH_WORDS = hashSetOf(
            "el", "la", "que", "en", "un", "una", "unos", "unas", "ser",
            "haber", "por", "su", "para", "como", "estar", "tener", "todo",
            "pero", "mas", "más", "hacer", "poder", "este", "esta", "estos", "estas", "ya",
            "otro", "otra", "si", "mucho", "muchas", "nos", "tiempo", "hoy", "bueno", "buena",
            "buenos", "buenas", "dias", "días", "tarde", "tardes", "noche", "noches", "hola",
            "gracias", "favor", "bien", "amigo", "casa", "muy", "claro", "donde", "dónde",
            "cuando", "cuándo", "quien", "quién", "porque", "porqué", "hace", "clima", "es",
            "son", "fue", "era", "está", "estan", "estamos", "manana", "mañana", "lindo",
            "dia", "día", "calor", "frio", "frío", "lluvia", "ayuda"
        )

        private val FRENCH_WORDS = hashSetOf(
            "les", "des", "du", "une", "etre", "être",
            "avoir", "dans", "cette", "ces", "ils", "elles",
            "qui", "ne", "sur", "pas", "plus", "pouvoir", "par", "avec", "tout",
            "tous", "faire", "fait", "son", "sa", "ses", "autre", "autres", "on", "mais",
            "nous", "vous", "comme", "ou", "leur", "leurs", "dire", "devoir", "avant",
            "deux", "meme", "même", "prendre", "aussi", "donner", "bonjour", "merci", "salut",
            "oui", "non", "beau", "belle", "demain", "temps", "tres", "très", "bien", "est",
            "sont", "ete", "été", "suis", "sommes", "avez", "ont", "pluie", "fait", "froid",
            "chaud", "beaucoup", "pour", "votre", "vos", "notre", "nos", "aide", "plait", "plaît",
            "comment", "allez", "bonne", "journee", "journée", "soir"
        )

        private val GERMAN_WORDS = hashSetOf(
            "der", "die", "das", "den", "dem", "des", "ein", "eine", "einer", "einem", "einen",
            "eines", "und", "von", "zu", "mit", "sich", "auf", "fur", "für", "ist",
            "sind", "war", "waren", "nicht", "als", "auch", "es", "an", "werden", "wird",
            "wurde", "aus", "er", "sie", "wir", "ihr", "hat", "haben", "hatte", "dass",
            "nach", "bei", "um", "am", "noch", "wie", "uber", "über", "so", "nur", "oder",
            "aber", "vor", "zur", "zum", "hallo", "guten", "tag", "morgen", "abend", "danke",
            "bitte", "ja", "nein", "wetter", "heute", "schon", "schön", "gut", "sehr",
            "kann", "vielleicht", "regnen", "sonne", "kalt", "warm", "hilfe", "vielen", "dank",
            "ihre", "ihren", "ihrem", "ihrer", "geht", "ihnen"
        )
    }
}
