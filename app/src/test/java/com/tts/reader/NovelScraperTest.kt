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
    fun testNavigationExtractionAndTitleDeduplication() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Chapter 120 - Gathering Clouds - Read Novel</title></head>
            <body>
                <div class="chapter-nav">
                    <a href="https://example-novels.com/c119.html" rel="prev" class="btn prev">Previous</a>
                    <a href="https://example-novels.com/c121.html" rel="next" class="btn next">Next</a>
                </div>
                <h1 class="chapter-title">Chapter 120 - Gathering Clouds</h1>
                <div class="reading-content">
                    <p>The dawn broke over the misty valley.</p>
                    <p>-------------------------</p>
                    <p>Lin Feng stood at the peak of the mountain.</p>
                </div>
                <div class="chapter-nav">
                    <a href="https://example-novels.com/c119.html">Prev</a>
                    <a href="https://example-novels.com/c121.html">Next</a>
                </div>
            </body>
            </html>
        """.trimIndent()

        val scraper = NovelScraper()
        val doc = Jsoup.parse(sampleHtml, "https://example-novels.com/c120.html")
        val chapter = scraper.parseDocument(doc, "https://example-novels.com/c120.html")

        assertEquals("Chapter 120 - Gathering Clouds", chapter.chapterTitle)
        // Previous and Next links should be successfully extracted even though .chapter-nav is stripped
        assertEquals("https://example-novels.com/c119.html", chapter.prevChapterUrl)
        assertEquals("https://example-novels.com/c121.html", chapter.nextChapterUrl)

        // Novel title should NOT duplicate the chapter title
        assertTrue(chapter.novelTitle.isEmpty() || chapter.novelTitle != chapter.chapterTitle)
    }

    @Test
    fun testBreadcrumbDoesNotHijackNavigation() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Chapter 10</title></head>
            <body>
                <div class="breadcrumbs">
                    <a href="https://example.com/">Home ></a>
                    <a href="https://example.com/genre/fantasy">Fantasy ></a>
                </div>
                <div class="reading-content"><p>Chapter story content goes here.</p></div>
                <div class="footer-nav">
                    <a href="https://example.com/c11" class="btn next">Next Chapter</a>
                </div>
            </body>
            </html>
        """.trimIndent()

        val scraper = NovelScraper()
        val doc = Jsoup.parse(sampleHtml, "https://example.com/c10")
        val chapter = scraper.parseDocument(doc, "https://example.com/c10")

        // Must point to chapter 11, NOT to the breadcrumbs "Fantasy >" or "Home >"
        assertEquals("https://example.com/c11", chapter.nextChapterUrl)
    }

    @Test
    fun testUrlSecurityValidation() = kotlinx.coroutines.runBlocking {
        val scraper = NovelScraper()

        // Localhost / Loopback
        val res1 = scraper.scrape("http://localhost:8080/secret")
        assertTrue(res1.isFailure)

        val res2 = scraper.scrape("http://127.0.0.1/admin")
        assertTrue(res2.isFailure)

        // Private IPv4 subnets
        val res3 = scraper.scrape("http://192.168.1.1/")
        assertTrue(res3.isFailure)

        val res4 = scraper.scrape("http://10.0.0.1/internal")
        assertTrue(res4.isFailure)

        // Non-http schemes
        val res5 = scraper.scrape("file:///data/data/com.tts.reader/databases")
        assertTrue(res5.isFailure)
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

