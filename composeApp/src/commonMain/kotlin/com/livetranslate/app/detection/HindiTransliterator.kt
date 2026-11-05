package com.livetranslate.app.detection

/**
 * High-precision transliterator that converts Romanized Hindi (Hinglish) into
 * standard Devanagari script so on-device translation engines (such as Google ML Kit)
 * can translate it accurately into English or other target languages.
 *
 * Combines an exhaustive conversational Hinglish dictionary with a phonetic syllabic
 * converter for unlisted words.
 */
object HindiTransliterator {

    private val DICTIONARY = mapOf(
        // Pronouns & Demonstratives
        "yah" to "यह",
        "ye" to "यह",
        "yeh" to "यह",
        "woh" to "वह",
        "wo" to "वह",
        "voh" to "वह",
        "vo" to "वह",
        "is" to "इस",
        "iss" to "इस",
        "iska" to "इसका",
        "iski" to "इसकी",
        "iske" to "इसके",
        "ise" to "इसे",
        "isko" to "इसको",
        "us" to "उस",
        "uss" to "उस",
        "uska" to "उसका",
        "uski" to "उसकी",
        "uske" to "उसके",
        "use" to "उसे",
        "usko" to "उसको",
        "in" to "इन",
        "inn" to "इन",
        "inka" to "इनका",
        "inki" to "इनकी",
        "inke" to "इनके",
        "inhe" to "इन्हें",
        "inhen" to "इन्हें",
        "inko" to "इनको",
        "un" to "उन",
        "unn" to "उन",
        "unka" to "उनका",
        "unki" to "उनकी",
        "unke" to "उनके",
        "unhe" to "उन्हें",
        "unhen" to "उन्हें",
        "unko" to "उनको",
        "main" to "मैं",
        "mujhe" to "मुझे",
        "mujhko" to "मुझको",
        "mera" to "मेरा",
        "meri" to "मेरी",
        "mere" to "मेरे",
        "hum" to "हम",
        "ham" to "हम",
        "humko" to "हमको",
        "hamko" to "हमको",
        "hamara" to "हमारा",
        "hamari" to "हमारी",
        "hamare" to "हमारे",
        "humara" to "हमारा",
        "humari" to "हमारी",
        "humare" to "हमारे",
        "aap" to "आप",
        "aapko" to "आपको",
        "aapka" to "आपका",
        "aapki" to "आपकी",
        "aapke" to "आपके",
        "tum" to "तुम",
        "tumhe" to "तुम्हें",
        "tumhen" to "तुम्हें",
        "tumko" to "तुमको",
        "tumhara" to "तुम्हारा",
        "tumhari" to "तुम्हारी",
        "tumhare" to "तुम्हारे",
        "tera" to "तेरा",
        "teri" to "तेरी",
        "tere" to "तेरे",
        "tujhe" to "तुझे",
        "tujhko" to "तुझको",
        "apna" to "अपना",
        "apni" to "अपनी",
        "apne" to "अपने",
        "kisi" to "किसी",
        "kisiko" to "किसी को",
        "kiska" to "किसका",
        "kiski" to "किसकी",
        "kiske" to "किसके",
        "kisko" to "किसको",
        "kise" to "किसे",
        "kaun" to "कौन",
        "koun" to "कौन",
        "koi" to "कोई",
        "kuch" to "कुछ",
        "kuchh" to "कुछ",
        "kya" to "क्या",
        "sab" to "सब",
        "sabka" to "सबका",
        "sabki" to "सबकी",
        "sabke" to "सबके",
        "sabko" to "सबको",
        "khud" to "खुद",

        // Question words & Adverbs
        "kab" to "कब",
        "kahan" to "कहाँ",
        "kaha" to "कहाँ",
        "kah" to "कह",
        "kha" to "कह",
        "kidhar" to "किधर",
        "kaise" to "कैसे",
        "kaisa" to "कैसा",
        "kaisi" to "कैसी",
        "kyun" to "क्यों",
        "kyon" to "क्यों",
        "kyu" to "क्यों",
        "kitna" to "कितना",
        "kitni" to "कितनी",
        "kitne" to "कितने",
        "yahan" to "यहाँ",
        "idhar" to "इधर",
        "vahan" to "वहाँ",
        "wahan" to "वहाँ",
        "udhar" to "उधर",
        "jahan" to "जहाँ",
        "jidhar" to "जिधर",
        "jab" to "जब",
        "tab" to "तब",
        "ab" to "अब",
        "kabhi" to "कभी",
        "hamesha" to "हमेशा",
        "pehle" to "पहले",
        "baad" to "बाद",
        "jaldi" to "जल्दी",
        "dheere" to "धीरे",
        "zyada" to "ज़्यादा",
        "jyada" to "ज़्यादा",
        "kam" to "कम",
        "bahut" to "बहुत",
        "bohot" to "बहुत",
        "sirf" to "सिर्फ",
        "hi" to "ही",
        "bhi" to "भी",
        "toh" to "तो",
        "to" to "तो",

        // Nouns & Objects
        "pagal" to "पागल",
        "paagal" to "पागल",
        "log" to "लोग",
        "logon" to "लोगों",
        "phone" to "फ़ोन",
        "fone" to "फ़ोन",
        "mobile" to "मोबाइल",
        "ghar" to "घर",
        "kamra" to "कमरा",
        "kamre" to "कमरे",
        "gaadi" to "गाड़ी",
        "gadi" to "गाड़ी",
        "paisa" to "पैसा",
        "paise" to "पैसे",
        "dost" to "दोस्त",
        "yaar" to "यार",
        "bhai" to "भाई",
        "behen" to "बहन",
        "baat" to "बात",
        "baatein" to "बातें",
        "kaam" to "काम",
        "waqt" to "वक्त",
        "samay" to "समय",
        "khana" to "खाना",
        "pani" to "पानी",
        "paani" to "पानी",
        "chai" to "चाय",
        "doodh" to "दूध",
        "roti" to "रोटी",
        "naam" to "नाम",
        "din" to "दिन",
        "raat" to "रात",
        "subah" to "सुबह",
        "shaam" to "शाम",
        "aaj" to "आज",
        "kal" to "कल",
        "parson" to "परसों",
        "box" to "बॉक्स",
        "office" to "ऑफिस",
        "madad" to "मदद",
        "zaroorat" to "ज़रूरत",
        "zarurat" to "ज़रूरत",
        "jagah" to "जगह",
        "shehar" to "शहर",
        "gaon" to "गाँव",
        "rasta" to "रास्ता",
        "sadak" to "सड़क",
        "dukan" to "दुकान",
        "bazaar" to "बाज़ार",
        "bazar" to "बाज़ार",
        "kitaab" to "किताब",
        "school" to "स्कूल",
        "bheed" to "भीड़",

        // Verbs & Conjugations
        "dal" to "डाल",
        "daal" to "डाल",
        "dala" to "डाला",
        "daala" to "डाला",
        "dali" to "डाली",
        "daali" to "डाली",
        "dale" to "डाले",
        "daale" to "डाले",
        "dalo" to "डालो",
        "daalo" to "डालो",
        "dalna" to "डालना",
        "daalna" to "डालना",
        "dunga" to "दूँगा",
        "doonga" to "दूँगा",
        "dungi" to "दूँगी",
        "doongi" to "दूँगी",
        "denge" to "देंगे",
        "dega" to "देगा",
        "degi" to "देगी",
        "dena" to "देना",
        "diya" to "दिया",
        "diye" to "दिए",
        "dijiye" to "दीजिए",
        "do" to "दो",
        "de" to "दे",
        "lunga" to "लूँगा",
        "loonga" to "लूँगा",
        "lungi" to "लूँगी",
        "loongi" to "लूँगी",
        "lenge" to "लेंगे",
        "lega" to "लेगा",
        "legi" to "लेगी",
        "lena" to "लेना",
        "liya" to "लिया",
        "liye" to "लिए",
        "lijiye" to "लीजिए",
        "lo" to "लो",
        "le" to "ले",
        "hai" to "है",
        "hain" to "हैं",
        "ho" to "हो",
        "hoon" to "हूँ",
        "hun" to "हूँ",
        "tha" to "था",
        "thi" to "थी",
        "the" to "थे",
        "raha" to "रहा",
        "rahi" to "रही",
        "rahe" to "रहे",
        "karo" to "करो",
        "karna" to "करना",
        "karein" to "करें",
        "karenge" to "करेंगे",
        "karega" to "करेगा",
        "karegi" to "करेगी",
        "kiya" to "किया",
        "kar" to "कर",
        "bolo" to "बोलो",
        "bolna" to "बोलना",
        "bole" to "बोले",
        "bolenge" to "बोलेंगे",
        "bola" to "बोला",
        "boli" to "बोली",
        "boliye" to "बोलिए",
        "batao" to "बताओ",
        "batana" to "बताना",
        "bataiye" to "बताइए",
        "bataya" to "बताया",
        "bataye" to "बताए",
        "dekh" to "देख",
        "dekho" to "देखो",
        "dekhna" to "देखना",
        "dekha" to "देखा",
        "dekhiye" to "देखिए",
        "dekhenge" to "देखेंगे",
        "suno" to "सुनो",
        "sun" to "सुन",
        "sunna" to "सुनना",
        "suniye" to "सुनिए",
        "suna" to "सुना",
        "samjho" to "समझो",
        "samajh" to "समझ",
        "samajhna" to "समझना",
        "samjha" to "समझा",
        "samjhe" to "समझे",
        "socho" to "सोचो",
        "soch" to "सोच",
        "sochna" to "सोचना",
        "socha" to "सोचा",
        "jao" to "जाओ",
        "jana" to "जाना",
        "jaana" to "जाना",
        "jaate" to "जाते",
        "jaiye" to "जाइए",
        "gaya" to "गया",
        "gaye" to "गए",
        "gayi" to "गयी",
        "aao" to "आओ",
        "aana" to "आना",
        "aata" to "आता",
        "aate" to "आते",
        "aati" to "आती",
        "aaye" to "आए",
        "aaya" to "आया",
        "aayi" to "आयी",
        "shuru" to "शुरू",
        "suru" to "शुरू",
        "khatam" to "खत्म",
        "khatm" to "खत्म",
        "ruk" to "रुक",
        "ruko" to "रुको",
        "rukna" to "रुकना",
        "ruka" to "रुका",
        "chal" to "चल",
        "chalo" to "चलो",
        "chalna" to "चलना",
        "chala" to "चला",
        "chale" to "चले",
        "chalenge" to "चलेंगे",
        "baith" to "बैठ",
        "baitho" to "बैठो",
        "baithna" to "बैठना",
        "baitha" to "बैठा",
        "uth" to "उठ",
        "utho" to "उठो",
        "uthna" to "उठना",
        "utha" to "उठा",
        "lao" to "लाओ",
        "lana" to "लाना",
        "laana" to "लाना",
        "laya" to "लाया",
        "laye" to "लाए",
        "bhejo" to "भेजो",
        "bhejna" to "भेजना",
        "bheja" to "भेजा",
        "mil" to "मिल",
        "milo" to "मिलो",
        "milna" to "मिलना",
        "mila" to "मिला",
        "mile" to "मिले",
        "pucho" to "पूछो",
        "puchna" to "पूछना",
        "pucha" to "पूछा",
        "rakh" to "रख",
        "rakho" to "रखो",
        "rakhna" to "रखना",
        "rakha" to "रखा",
        "chahiye" to "चाहिए",
        "hoga" to "होगा",
        "hogi" to "होगी",
        "honge" to "होंगे",
        "bana" to "बना",
        "banao" to "बनाओ",
        "banana" to "बनाना",
        "banaya" to "बताया",

        // Postpositions, Conjunctions & Particles
        "mein" to "में",
        "me" to "में",
        "par" to "पर",
        "per" to "पर",
        "pe" to "पे",
        "se" to "से",
        "ko" to "को",
        "ka" to "का",
        "ki" to "की",
        "ke" to "के",
        "liye" to "लिए",
        "saath" to "साथ",
        "sath" to "साथ",
        "paas" to "पास",
        "aur" to "और",
        "ya" to "या",
        "na" to "ना",
        "nahi" to "नहीं",
        "nahin" to "नहीं",
        "mat" to "मत",
        "lekin" to "लेकिन",
        "magar" to "मगर",
        "agar" to "अगर",
        "kyunki" to "क्योंकि",
        "kyonki" to "क्योंकि",
        "isliye" to "इसलिए",
        "achha" to "अच्छा",
        "accha" to "अच्छा",
        "acha" to "अच्छा",
        "acche" to "अच्छे",
        "achi" to "अच्छी",
        "bura" to "बुरा",
        "sahi" to "सही",
        "galat" to "गलत",
        "theek" to "ठीक",
        "thik" to "ठीक",
        "bada" to "बड़ा",
        "badi" to "बड़ी",
        "bade" to "बड़े",
        "chota" to "छोटा",
        "choti" to "छोटी",
        "chote" to "छोटे",
        "namaste" to "नमस्ते",
        "namaskar" to "नमस्कार",
        "dhanyawad" to "धन्यवाद",
        "shukriya" to "शुक्रिया",
        "alvida" to "अलविदा",
        "kripya" to "कृपया",
        "zaroor" to "ज़रूर",
        "zarur" to "ज़रूर",
        "zaroori" to "ज़रूरी",
        "zaruri" to "ज़रूरी"
    )

    /**
     * Checks if [text] has any Devanagari characters.
     */
    fun containsDevanagari(text: String): Boolean =
        text.any { it.code in 0x0900..0x097F }

    /**
     * Converts Romanized Hindi words into Devanagari script.
     * If the text is already in Devanagari, it is returned unchanged.
     */
    fun toDevanagari(text: String): String {
        if (containsDevanagari(text)) return text

        val words = text.split(Regex("\\s+"))
        val translatedWords = words.map { word ->
            val clean = word.lowercase().trim { !it.isLetter() }
            val devanagari = DICTIONARY[clean] ?: phoneticTransliterateWord(clean)
            if (devanagari.isNotEmpty()) {
                val leading = word.takeWhile { !it.isLetter() }
                val trailing = word.takeLastWhile { !it.isLetter() }
                "$leading$devanagari$trailing"
            } else {
                word
            }
        }

        return translatedWords.joinToString(" ")
    }

    /**
     * Fallback phonetic syllabic converter for unlisted Hinglish tokens.
     */
    private fun phoneticTransliterateWord(token: String): String {
        if (token.isEmpty() || token.any { !it.isLetter() }) return token
        val lower = token.lowercase()
        val sb = StringBuilder()
        var i = 0
        val n = lower.length

        // Consonant mappings (longest match first)
        val consonantMap = listOf(
            "chh" to "छ", "kh" to "ख", "gh" to "घ", "ch" to "च", "jh" to "झ",
            "th" to "थ", "dh" to "ध", "ph" to "फ", "bh" to "भ", "sh" to "श",
            "k" to "क", "g" to "ग", "j" to "ज", "t" to "त", "d" to "द",
            "n" to "न", "p" to "प", "f" to "फ़", "b" to "ब", "m" to "म",
            "y" to "य", "r" to "र", "l" to "ल", "v" to "व", "w" to "व",
            "s" to "स", "h" to "ह", "z" to "ज़"
        )

        // Matra (vowel signs attached to consonants)
        val matraMap = listOf(
            "aa" to "ा", "ee" to "ी", "oo" to "ू", "ai" to "ै", "au" to "ौ",
            "a" to "", "i" to "ि", "u" to "ु", "e" to "े", "o" to "ो"
        )

        // Independent initial vowels
        val initialVowelMap = listOf(
            "aa" to "आ", "ee" to "ई", "oo" to "ऊ", "ai" to "ऐ", "au" to "औ",
            "a" to "अ", "i" to "इ", "u" to "उ", "e" to "ए", "o" to "ओ"
        )

        while (i < n) {
            // Check if initial vowel (at start of word or after another vowel)
            if (i == 0 || sb.isEmpty()) {
                var vowelMatched = false
                for ((vStr, vDev) in initialVowelMap) {
                    if (lower.startsWith(vStr, i)) {
                        sb.append(vDev)
                        i += vStr.length
                        vowelMatched = true
                        break
                    }
                }
                if (vowelMatched) continue
            }

            // Check consonant
            var consMatched = false
            for ((cStr, cDev) in consonantMap) {
                if (lower.startsWith(cStr, i)) {
                    sb.append(cDev)
                    i += cStr.length
                    consMatched = true

                    // Check following vowel matra
                    var matraMatched = false
                    for ((mStr, mDev) in matraMap) {
                        if (lower.startsWith(mStr, i)) {
                            sb.append(mDev)
                            i += mStr.length
                            matraMatched = true
                            break
                        }
                    }
                    break
                }
            }

            if (!consMatched) {
                // If single letter doesn't match consonant, append as-is or skip
                sb.append(lower[i])
                i++
            }
        }

        return if (sb.isNotEmpty()) sb.toString() else token
    }
}
