package com.livetranslate.app

import com.livetranslate.app.detection.DefaultLanguageDetector
import com.livetranslate.app.detection.HindiTransliterator
import com.livetranslate.app.model.SupportedLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanguageDetectorTest {

    private val detector = DefaultLanguageDetector()

    @Test
    fun testDetectJapanese() {
        assertEquals(SupportedLanguage.JAPANESE, detector.detectLanguage("今日はいい天気ですね。"))
        assertEquals(SupportedLanguage.JAPANESE, detector.detectLanguage("ありがとうございます"))
        assertEquals(SupportedLanguage.JAPANESE, detector.detectLanguage("コンニチハ"))
    }

    @Test
    fun testDetectChinese() {
        assertEquals(SupportedLanguage.CHINESE, detector.detectLanguage("今天天气真好。"))
        assertEquals(SupportedLanguage.CHINESE, detector.detectLanguage("你好，世界"))
        assertEquals(SupportedLanguage.CHINESE, detector.detectLanguage("非常感谢你的帮助"))
    }

    @Test
    fun testDetectRussian() {
        assertEquals(SupportedLanguage.RUSSIAN, detector.detectLanguage("Сегодня хорошая погода."))
        assertEquals(SupportedLanguage.RUSSIAN, detector.detectLanguage("Привет мир"))
        assertEquals(SupportedLanguage.RUSSIAN, detector.detectLanguage("Большое спасибо за помощь"))
    }

    @Test
    fun testDetectKorean() {
        assertEquals(SupportedLanguage.KOREAN, detector.detectLanguage("오늘 날씨가 정말 좋네요."))
        assertEquals(SupportedLanguage.KOREAN, detector.detectLanguage("안녕하세요 반갑습니다"))
        assertEquals(SupportedLanguage.KOREAN, detector.detectLanguage("감사합니다"))
    }

    @Test
    fun testDetectHindi() {
        assertEquals(SupportedLanguage.HINDI, detector.detectLanguage("आज मौसम बहुत अच्छा है।"))
        assertEquals(SupportedLanguage.HINDI, detector.detectLanguage("नमस्ते दुनिया"))
        assertEquals(SupportedLanguage.HINDI, detector.detectLanguage("बहुत-बहुत धन्यवाद"))
    }

    @Test
    fun testDetectRomanizedHindi() {
        assertEquals(SupportedLanguage.HINDI, detector.detectLanguage("kab Shuru Kiya"))
        assertEquals(SupportedLanguage.HINDI, detector.detectLanguage("aap Kisi box office"))
        assertEquals(SupportedLanguage.HINDI, detector.detectLanguage("Tumhen vahan per kha raha tha to Tum Aaye Kyon Nahin vahan per"))
        assertEquals(SupportedLanguage.HINDI, detector.detectLanguage("Kuchh pagal log Hain Yahan per"))
        assertEquals(SupportedLanguage.HINDI, detector.detectLanguage("yah Tumhare ya tumhare phone mein main Dal Dunga na"))
    }

    @Test
    fun testHindiTransliterator() {
        val devanagari1 = HindiTransliterator.toDevanagari("kab Shuru Kiya")
        assertTrue(devanagari1.contains("कब"))
        assertTrue(devanagari1.contains("शुरू"))
        assertTrue(devanagari1.contains("किया"))

        val devanagari2 = HindiTransliterator.toDevanagari("aap Kisi box office")
        assertTrue(devanagari2.contains("आप"))
        assertTrue(devanagari2.contains("किसी"))

        val devanagari3 = HindiTransliterator.toDevanagari("Kuchh pagal log Hain Yahan per")
        assertTrue(devanagari3.contains("कुछ"))
        assertTrue(devanagari3.contains("पागल"))
        assertTrue(devanagari3.contains("लोग"))
        assertTrue(devanagari3.contains("हैं"))
        assertTrue(devanagari3.contains("यहाँ"))
        assertTrue(devanagari3.contains("पर"))

        val devanagari4 = HindiTransliterator.toDevanagari("yah Tumhare ya tumhare phone mein main Dal Dunga na")
        assertTrue(devanagari4.contains("यह"))
        assertTrue(devanagari4.contains("तुम्हारे"))
        assertTrue(devanagari4.contains("या"))
        assertTrue(devanagari4.contains("फ़ोन"))
        assertTrue(devanagari4.contains("में"))
        assertTrue(devanagari4.contains("मैं"))
        assertTrue(devanagari4.contains("डाल"))
        assertTrue(devanagari4.contains("दूँगा"))
        assertTrue(devanagari4.contains("ना"))

        val originalDevanagari = "आज मौसम बहुत अच्छा है।"
        assertEquals(originalDevanagari, HindiTransliterator.toDevanagari(originalDevanagari))
    }

    @Test
    fun testDetectEnglish() {
        assertEquals(SupportedLanguage.ENGLISH, detector.detectLanguage("It's a nice day today."))
        assertEquals(SupportedLanguage.ENGLISH, detector.detectLanguage("Hello, how are you doing today?"))
        assertEquals(SupportedLanguage.ENGLISH, detector.detectLanguage("Thank you very much for your help."))
        assertEquals(SupportedLanguage.ENGLISH, detector.detectLanguage("but how do you know"))
        assertEquals(SupportedLanguage.ENGLISH, detector.detectLanguage("you are not there"))
        assertEquals(SupportedLanguage.ENGLISH, detector.detectLanguage("now you are asking before"))
        assertEquals(SupportedLanguage.ENGLISH, detector.detectLanguage("Where is the train station?"))
        assertEquals(SupportedLanguage.ENGLISH, detector.detectLanguage("Can you please help me with the luggage?"))
        assertEquals(SupportedLanguage.ENGLISH, detector.detectLanguage("Show me the picture"))
        assertEquals(SupportedLanguage.ENGLISH, detector.detectLanguage("Tell me the time"))
        assertEquals(SupportedLanguage.ENGLISH, detector.detectLanguage("What is your name?"))
    }

    @Test
    fun testDetectSpanish() {
        assertEquals(SupportedLanguage.SPANISH, detector.detectLanguage("Hoy hace buen tiempo."))
        assertEquals(SupportedLanguage.SPANISH, detector.detectLanguage("Hola amigo, ¿cómo estás?"))
        assertEquals(SupportedLanguage.SPANISH, detector.detectLanguage("Muchas gracias por su ayuda."))
    }

    @Test
    fun testDetectFrench() {
        assertEquals(SupportedLanguage.FRENCH, detector.detectLanguage("Il fait beau aujourd'hui."))
        assertEquals(SupportedLanguage.FRENCH, detector.detectLanguage("Bonjour tout le monde."))
        assertEquals(SupportedLanguage.FRENCH, detector.detectLanguage("Merci beaucoup pour votre aide."))
    }

    @Test
    fun testDetectGerman() {
        assertEquals(SupportedLanguage.GERMAN, detector.detectLanguage("Heute ist schönes Wetter."))
        assertEquals(SupportedLanguage.GERMAN, detector.detectLanguage("Guten Tag, wie geht es Ihnen?"))
        assertEquals(SupportedLanguage.GERMAN, detector.detectLanguage("Vielen Dank für Ihre Hilfe."))
    }

    @Test
    fun testAlternatingLanguages() {
        // Hindi -> English -> Hindi -> Japanese -> Russian -> English
        assertEquals(SupportedLanguage.HINDI, detector.detectLanguage("kab Shuru Kiya"))
        assertEquals(SupportedLanguage.ENGLISH, detector.detectLanguage("Can you tell me the time please?"))
        assertEquals(SupportedLanguage.HINDI, detector.detectLanguage("aap Kisi box office"))
        assertEquals(SupportedLanguage.JAPANESE, detector.detectLanguage("今日はいい天気ですね。"))
        assertEquals(SupportedLanguage.RUSSIAN, detector.detectLanguage("Сегодня хорошая погода."))
        assertEquals(SupportedLanguage.ENGLISH, detector.detectLanguage("Where is the train station?"))
    }

    @Test
    fun testUnrecognizableAndShortPhrasesReturnNullGracefully() {
        assertNull(detector.detectLanguage(""))
        assertNull(detector.detectLanguage(" "))
        assertNull(detector.detectLanguage("a"))
        assertNull(detector.detectLanguage("123456!@#$%"))
        assertNull(detector.detectLanguage("xyz uvw abcde"))
    }
}
