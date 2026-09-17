package com.tts.reader.data.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
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
        // 1. Strip comments, menus, scripts, banners, ads
        val unwantedSelectors = listOf(
            "#dle-comments-list", ".comments", ".comments-tree", ".comment-list",
            "#comments", ".navigation", ".ad-block", ".ads", "script", "style",
            "header", "footer", "nav", ".header", ".footer", ".sidebar",
            ".soc-buttons", ".share-box", ".socials", ".rating", ".comment-form"
        )
        for (selector in unwantedSelectors) {
            doc.select(selector).remove()
        }

        // 2. Extract Novel & Chapter Title
        val chapterTitle = extractChapterTitle(doc)
        val novelTitle = extractNovelTitle(doc, chapterTitle)

        // 3. Extract Main Story Content Container
        // Ranobes specifically uses #arrticle (with double 'r'), .text, or .read-text
        val contentElement = doc.selectFirst("#arrticle")
            ?: doc.selectFirst(".text")
            ?: doc.selectFirst(".read-text")
            ?: doc.selectFirst(".chapter-content")
            ?: doc.selectFirst(".entry-content")
            ?: doc.selectFirst("#chapter-content")
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

        // Fallback if content was divided by <br> tags rather than <p>
        if (paragraphs.isEmpty()) {
            val fullText = contentElement.wholeText()
            val lines = fullText.split("\n")
                .map { cleanParagraphText(it) }
                .filter { it.isNotBlank() && !isSpamOrAdLine(it) }
            paragraphs.addAll(lines)
        }

        // 5. Detect Next / Previous Chapter Navigation Links
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
                lower.startsWith("chapter ") && text.length < 20 // duplicate title line
    }

    private fun extractChapterTitle(doc: Document): String {
        return doc.selectFirst("h1.title")?.text()
            ?: doc.selectFirst(".title")?.text()
            ?: doc.selectFirst("h1")?.text()
            ?: doc.selectFirst(".chapter-title")?.text()
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
        val keywords = if (isNext) listOf("next", "следующая", "next chapter", ">>") else listOf("prev", "предыдущая", "previous", "<<")
        val links = doc.select("a[href]")

        for (link in links) {
            val text = link.text().lowercase().trim()
            val rel = link.attr("rel").lowercase()

            if (rel == (if (isNext) "next" else "prev")) {
                return link.absUrl("href")
            }

            for (kw in keywords) {
                if (text == kw || text.contains(kw)) {
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
