package com.tts.reader

import com.tts.reader.data.scraper.TextSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSanitizerTest {

    @Test
    fun testIsVisualDivider() {
        // Obvious visual dividers
        assertTrue(TextSanitizer.isVisualDivider("----------"))
        assertTrue(TextSanitizer.isVisualDivider("-------------------------"))
        assertTrue(TextSanitizer.isVisualDivider("***"))
        assertTrue(TextSanitizer.isVisualDivider("* * *"))
        assertTrue(TextSanitizer.isVisualDivider("===================="))
        assertTrue(TextSanitizer.isVisualDivider("— — — — —"))
        assertTrue(TextSanitizer.isVisualDivider("~*~*~*~*~"))
        assertTrue(TextSanitizer.isVisualDivider("・ ・ ・"))
        assertTrue(TextSanitizer.isVisualDivider("◆ ◇ ◆ ◇ ◆"))
        assertTrue(TextSanitizer.isVisualDivider("✦ ✦ ✦"))

        // Standard prose should NEVER be classified as dividers
        assertFalse(TextSanitizer.isVisualDivider("The cultivation world was vast and unforgiving."))
        assertFalse(TextSanitizer.isVisualDivider("Chapter 42: The Awakening"))
        assertFalse(TextSanitizer.isVisualDivider("He struck with his sword - fast and decisive."))
    }

    @Test
    fun testIsAuthorNote() {
        assertTrue(TextSanitizer.isAuthorNote("Author's Note: Thanks for reading chapter 5!"))
        assertTrue(TextSanitizer.isAuthorNote("Author Note: Next update will be delayed."))
        assertTrue(TextSanitizer.isAuthorNote("TL Note: Qi refers to vital energy."))
        assertTrue(TextSanitizer.isAuthorNote("A/N: Support me on Patreon!"))
        assertTrue(TextSanitizer.isAuthorNote("Translator's Note: Edited by John."))

        // Normal text should not trigger
        assertFalse(TextSanitizer.isAuthorNote("Note that the sword was already broken."))
        assertFalse(TextSanitizer.isAuthorNote("He made a mental note to avoid the elder."))
    }

    @Test
    fun testSanitizeForSpeech() {
        // Dividers should be completely silenced
        assertEquals(null, TextSanitizer.sanitizeForSpeech("---------------------"))
        assertEquals(null, TextSanitizer.sanitizeForSpeech("***"))
        assertEquals(null, TextSanitizer.sanitizeForSpeech("============"))

        // Normal sentences should have redundant symbols cleaned without breaking words
        val rawSentence = "He stepped forward... --- He saw the beast!"
        val sanitized = TextSanitizer.sanitizeForSpeech(rawSentence)
        assertTrue(sanitized != null && !sanitized.contains("---"))
        assertTrue(sanitized!!.contains("He stepped forward"))
        assertTrue(sanitized.contains("He saw the beast!"))

        // Excessive dashes/asterisks stripped
        assertEquals("Chapter 1", TextSanitizer.sanitizeForSpeech("--- Chapter 1 ---"))
    }
}
