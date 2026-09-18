package com.tts.reader.data.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ScrapedChapter(
    val novelTitle: String,
    val chapterTitle: String,
    val paragraphs: List<String>,
    val nextChapterUrl: String? = null,
    val prevChapterUrl: String? = null
)

class NovelScraper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val browserUserAgent =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    suspend fun scrape(url: String): Result<ScrapedChapter> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", browserUserAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val html = response.body?.string() ?: throw Exception("Empty response body from novel URL")
            val doc = Jsoup.parse(html, url)

            val scraped = parseDocument(doc, url)
            Result.success(scraped)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseRawText(title: String, text: String): ScrapedChapter {
        val rawParagraphs = text.split("\n\n", "\r\n\r\n", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return ScrapedChapter(
            novelTitle = "Custom Text",
            chapterTitle = title.ifBlank { "Pasted Chapter" },
            paragraphs = if (rawParagraphs.isEmpty()) listOf("No content provided.") else rawParagraphs
        )
    }

    private fun parseDocument(doc: Document, baseUrl: String): ScrapedChapter {
        // 1. Strip comments, navigation bars, social widgets, advertisements universally
        val unwantedSelectors = listOf(
            "#dle-comments-list", ".comments", ".comments-tree", ".comment-list",
            "#comments", ".navigation", ".ad-block", ".ads", "script", "style",
            "header", "footer", "nav", ".header", ".footer", ".sidebar",
            ".soc-buttons", ".share-box", ".socials", ".rating", ".comment-form",
            "#disqus_thread", ".disqus", ".fb-comments", ".reactions",
            ".report-chapter", ".author-note-wrapper", ".ad-container",
            ".chapter-nav", ".nav-buttons"
        )
        for (selector in unwantedSelectors) {
            doc.select(selector).remove()
        }

        // 2. Extract Novel & Chapter Title
        val chapterTitle = extractChapterTitle(doc)
        val novelTitle = extractNovelTitle(doc, chapterTitle)

        // 3. Extract Main Story Content Container Universally
        val contentElement = doc.selectFirst("#arrticle")
            ?: doc.selectFirst(".chapter-content")
            ?: doc.selectFirst("#chapter-content")
            ?: doc.selectFirst(".reading-content")
            ?: doc.selectFirst(".text")
            ?: doc.selectFirst(".read-text")
            ?: doc.selectFirst(".entry-content")
            ?: doc.selectFirst(".post-content")
            ?: doc.selectFirst(".chapter-body")
            ?: doc.selectFirst(".novel-content")
            ?: doc.selectFirst("article")
            ?: doc.body()

        // 4. Extract Clean Paragraphs
        val paragraphs = mutableListOf<String>()
        val pTags = contentElement.select("p")
        if (pTags.isNotEmpty()) {
            for (p in pTags) {
                val text = cleanParagraphText(p.text())
                if (text.isNotBlank() && !isSpamOrAdLine(text)) {
                    paragraphs.add(text)
                }
            }
        }

        // Fallback for novel sites that separate text with <br> or raw linebreaks
        if (paragraphs.isEmpty()) {
            val fullText = contentElement.wholeText()
            val lines = fullText.split("\n")
                .map { cleanParagraphText(it) }
                .filter { it.isNotBlank() && !isSpamOrAdLine(it) }
            paragraphs.addAll(lines)
        }

        // 5. Detect Next / Previous Chapter Navigation Links Universally
        val nextUrl = findNavigationLink(doc, baseUrl, isNext = true)
        val prevUrl = findNavigationLink(doc, baseUrl, isNext = false)

        return ScrapedChapter(
            novelTitle = novelTitle,
            chapterTitle = chapterTitle,
            paragraphs = if (paragraphs.isEmpty()) listOf("No readable text found in this chapter.") else paragraphs,
            nextChapterUrl = nextUrl,
            prevChapterUrl = prevUrl
        )
    }

    private fun cleanParagraphText(text: String): String {
        return text.replace("\\s+".toRegex(), " ")
            .replace("^[\\s\\u00A0]+|[\\s\\u00A0]+$".toRegex(), "")
            .trim()
    }

    private fun isSpamOrAdLine(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("translator notes") ||
                lower.contains("support the author") ||
                lower.contains("read latest chapters at") ||
                lower.contains("join our discord") ||
                lower.contains("sponsored by") ||
                lower.contains("patreon.com") ||
                lower.startsWith("chapter ") && text.length < 20
    }

    private fun extractChapterTitle(doc: Document): String {
        return doc.selectFirst("h1.title")?.text()
            ?: doc.selectFirst("h1.chapter-title")?.text()
            ?: doc.selectFirst(".chapter-title")?.text()
            ?: doc.selectFirst("h1")?.text()
            ?: doc.selectFirst(".title")?.text()
            ?: doc.title()
    }

    private fun extractNovelTitle(doc: Document, chapterTitle: String): String {
        val breadcrumb = doc.select(".breadcrumb a, .breadcrumbs a").last()?.text()
        if (!breadcrumb.isNullOrBlank() && breadcrumb != chapterTitle) {
            return breadcrumb
        }
        val ogNovel = doc.select("meta[property=og:novel:novel_name]").attr("content")
        if (ogNovel.isNotBlank()) return ogNovel

        return doc.title().split("-", "|").firstOrNull()?.trim() ?: "Web Novel"
    }

    private fun findNavigationLink(doc: Document, baseUrl: String, isNext: Boolean): String? {
        val keywords = if (isNext) {
            listOf("next", "next chapter", "siguiente", "следующая", "下", ">>", ">")
        } else {
            listOf("prev", "previous", "anterior", "предыдущая", "上", "<<", "<")
        }
        val links = doc.select("a[href]")

        for (link in links) {
            val text = link.text().lowercase().trim()
            val rel = link.attr("rel").lowercase()

            if (rel == (if (isNext) "next" else "prev")) {
                return link.absUrl("href")
            }

            for (kw in keywords) {
                if (text == kw || text == "$kw chapter" || text.contains(kw)) {
                    val href = link.absUrl("href")
                    if (href.isNotBlank() && href != baseUrl && !href.startsWith("javascript")) {
                        return href
                    }
                }
            }
        }
        return null
    }

    companion object {
        private val SENTENCE_PATTERN = Pattern.compile("(?<=[.!?…])\\s+|(?<=\\n)")

        fun splitIntoSentences(paragraph: String): List<String> {
            return paragraph.split(SENTENCE_PATTERN)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
    }
}
