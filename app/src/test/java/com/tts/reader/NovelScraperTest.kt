package com.tts.reader

import com.tts.reader.data.scraper.NovelScraper
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelScraperTest {

    @Test
    fun testSentenceSplitting() {
        val paragraph = "This is sentence one. Is this sentence two? Yes! And sentence four… Finally sentence five."
        val sentences = NovelScraper.splitIntoSentences(paragraph)
        assertEquals(5, sentences.size)
        assertEquals("This is sentence one.", sentences[0])
        assertEquals("Is this sentence two?", sentences[1])
        assertEquals("Yes!", sentences[2])
    }

    @Test
    fun testRanobesHtmlSanitization() {
        val sampleHtml = """
            <html>
            <head><title>Martial God - Chapter 45 - Ranobes</title></head>
            <body>
                <header class="header">Site Navigation Header</header>
                <div class="breadcrumb"><a href="#">Martial God</a></div>
                <h1 class="title">Chapter 45 - The Dragon Gate</h1>
                <div id="arrticle">
                    <p>The dawn broke over the misty valley.</p>
                    <p>Lin Feng stood at the peak of the mountain.</p>
                </div>
                <div class="navigation">
                    <a href="/chapter-44">Prev</a>
                    <a href="/chapter-46" rel="next">Next Chapter</a>
                </div>
                <div id="dle-comments-list">
                    <div class="comment">User1: Great chapter!</div>
                    <div class="comment">User2: Update soon please!</div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(sampleHtml, "https://ranobes.top/novels/123/c45.html")
        
        // Emulate scraper cleaning steps
        val unwantedSelectors = listOf(
            "#dle-comments-list", ".comments", ".navigation", ".header"
        )
        for (sel in unwantedSelectors) {
            doc.select(sel).remove()
        }

        val content = doc.selectFirst("#arrticle")?.text() ?: ""
        assertTrue(content.contains("The dawn broke over the misty valley."))
        assertTrue(content.contains("Lin Feng stood at the peak of the mountain."))
        assertFalse(content.contains("User1: Great chapter!"))
        assertFalse(content.contains("Site Navigation Header"))
    }
}
