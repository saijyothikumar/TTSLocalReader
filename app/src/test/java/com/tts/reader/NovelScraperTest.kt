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
    fun testUniversalHtmlSanitization() {
        val sampleHtml = """
            <html>
            <head><title>Immortal Journey - Chapter 120</title></head>
            <body>
                <header class="header">Site Navigation Header</header>
                <div class="breadcrumb"><a href="#">Immortal Journey</a></div>
                <h1 class="title">Chapter 120 - Gathering Clouds</h1>
                <div class="chapter-content">
                    <p>The dawn broke over the misty valley.</p>
                    <p>Lin Feng stood at the peak of the mountain.</p>
                </div>
                <div class="navigation">
                    <a href="/chapter-119">Prev</a>
                    <a href="/chapter-121" rel="next">Next Chapter</a>
                </div>
                <div id="comments" class="comments-tree">
                    <div class="comment">User1: Great chapter!</div>
                    <div class="comment">User2: Update soon please!</div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(sampleHtml, "https://example-novels.com/c120.html")
        
        val unwantedSelectors = listOf(
            "#dle-comments-list", "#comments", ".comments", ".comments-tree", ".navigation", ".header"
        )
        for (sel in unwantedSelectors) {
            doc.select(sel).remove()
        }

        val content = doc.selectFirst(".chapter-content")?.text() ?: ""
        assertTrue(content.contains("The dawn broke over the misty valley."))
        assertTrue(content.contains("Lin Feng stood at the peak of the mountain."))
        assertFalse(content.contains("User1: Great chapter!"))
        assertFalse(content.contains("Site Navigation Header"))
    }

    @Test
    fun testRawTextParsing() {
        val scraper = NovelScraper()
        val raw = "Paragraph 1 line.\n\nParagraph 2 line with more words."
        val chapter = scraper.parseRawText("My Draft", raw)
        assertEquals("My Draft", chapter.chapterTitle)
        assertEquals(2, chapter.paragraphs.size)
        assertEquals("Paragraph 1 line.", chapter.paragraphs[0])
    }
}
