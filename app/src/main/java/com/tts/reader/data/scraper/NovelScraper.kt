package com.tts.reader.data.scraper

import android.webkit.CookieManager
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

    private val browserUserAgent =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
            // Automatically forward WebView cookies (e.g. Cloudflare cf_clearance tokens)
            try {
                val cookie = CookieManager.getInstance().getCookie(original.url.toString())
                if (!cookie.isNullOrBlank()) {
                    builder.header("Cookie", cookie)
                }
            } catch (_: Exception) {
                // Ignore if CookieManager not available in unit tests
            }
            chain.proceed(builder.build())
        }
        .build()

    suspend fun scrape(url: String, referer: String? = null): Result<ScrapedChapter> = withContext(Dispatchers.IO) {
        try {
            validatePublicUrl(url)

            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", browserUserAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Sec-Ch-Ua", "\"Chromium\";v=\"124\", \"Android WebView\";v=\"124\", \"Not-A.Brand\";v=\"99\"")
                .header("Sec-Ch-Ua-Mobile", "?1")
                .header("Sec-Ch-Ua-Platform", "\"Android\"")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", if (referer != null) "same-origin" else "none")
                .header("Sec-Fetch-User", "?1")
                .header("Upgrade-Insecure-Requests", "1")

            if (!referer.isNullOrBlank()) {
                requestBuilder.header("Referer", referer)
            }

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            // Check for Cloudflare / bot protection challenges
            checkBotChallenge(response.code, bodyString, url)

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            if (bodyString.isBlank()) {
                throw Exception("Empty response body from novel URL")
            }

            val doc = Jsoup.parse(bodyString, url)
            val scraped = parseDocument(doc, url)
            Result.success(scraped)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun validatePublicUrl(urlString: String) {
        val uri = try {
            java.net.URI(urlString)
        } catch (_: Exception) {
            throw IllegalArgumentException("Malformed URL format: $urlString")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw IllegalArgumentException("Unsupported URL scheme: $scheme. Only http and https are allowed.")
        }

        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("Missing host in URL: $urlString")

        // Reject localhost and local machine interfaces
        if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host.endsWith(".local") || host.endsWith(".internal")) {
            throw SecurityException("Access to local address is prohibited: $host")
        }

        // Reject private IPv4 address blocks (RFC 1918 / RFC 3927)
        if (host.matches(Regex("^(10\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[0-1])\\.|169\\.254\\.).*"))) {
            throw SecurityException("Access to private intranet IP ranges is prohibited: $host")
        }

        val path = uri.path?.lowercase() ?: ""
        if (path.contains("/comments/") || path.contains("/comment/") || path.endsWith("/comments") || path.endsWith("/comment")) {
            throw IllegalArgumentException("URL points to a comments page, not a story chapter: $urlString")
        }
    }

    fun isValidChapterUrl(url: String?, baseUrl: String = "", expectedBaseHost: String? = null): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        if (baseUrl.isNotBlank() && trimmed.equals(baseUrl.trim(), ignoreCase = true)) return false
        if (trimmed.startsWith("javascript:", ignoreCase = true) ||
            trimmed.startsWith("mailto:", ignoreCase = true) ||
            trimmed.startsWith("tel:", ignoreCase = true) ||
            trimmed.startsWith("#")) {
            return false
        }

        val uri = try {
            java.net.URI(trimmed)
        } catch (_: Exception) {
            return false
        }

        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false

        val host = uri.host?.lowercase() ?: return false

        // SSRF protection: reject localhost and private IP addresses
        if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host.endsWith(".local") || host.endsWith(".internal")) {
            return false
        }
        if (host.matches(Regex("^(10\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[0-1])\\.|169\\.254\\.).*"))) {
            return false
        }

        // Cache baseHost computation to avoid re-parsing baseUrl inside loops
        val baseHost = expectedBaseHost ?: if (baseUrl.isNotBlank()) {
            try { java.net.URI(baseUrl).host?.lowercase()?.removePrefix("www.") } catch (_: Exception) { null }
        } else null

        if (baseHost != null) {
            val cleanHost = host.removePrefix("www.")
            if (cleanHost != baseHost && !cleanHost.endsWith(".$baseHost")) {
                return false
            }
        }

        val path = (uri.path ?: "").lowercase()
        val query = (uri.query ?: "").lowercase()
        val fragment = (uri.fragment ?: "").lowercase()

        // Reject comments, profiles, forums, catalog, media, login/auth
        if (path.contains("/comments/") || path.contains("/comment/") || path.endsWith("/comments") || path.endsWith("/comment") ||
            fragment.contains("comment") || fragment.contains("disqus") ||
            query.contains("lastcomments") || query.contains("do=comments") ||
            path.contains("/user/") || path.contains("/users/") ||
            path.contains("/profile/") || path.contains("/account/") ||
            path.contains("/forum/") || path.contains("/catalog/") ||
            path.contains("/tags/") || path.contains("/genre/") ||
            path.contains("/search/") || path.contains("/bookmark/") ||
            path.contains("/login") || path.contains("/register") ||
            path.contains("/logout")
        ) {
            return false
        }

        // Reject static media files
        if (path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
            path.endsWith(".webp") || path.endsWith(".gif") || path.endsWith(".css") ||
            path.endsWith(".js") || path.endsWith(".apk") || path.endsWith(".zip")) {
            return false
        }

        return true
    }

    fun extractNovelPathPrefix(url: String): String {
        return try {
            val path = java.net.URI(url).path ?: return ""
            val lastSlash = path.lastIndexOf('/')
            if (lastSlash > 0) path.substring(0, lastSlash + 1) else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun matchesNavKeyword(text: String, keywords: List<String>): Boolean {
        if (text.isBlank()) return false
        for (kw in keywords) {
            if (kw.length <= 2) {
                if (text == kw) return true
            } else {
                if (text == kw || text.startsWith("$kw ") || text.endsWith(" $kw") || text.contains(" $kw ")) {
                    return true
                }
            }
        }
        return false
    }

    private fun checkBotChallenge(statusCode: Int, body: String, url: String) {
        val lower = body.lowercase()
        if (statusCode == 403 || statusCode == 503) {
            if (lower.contains("cloudflare") ||
                lower.contains("just a moment") ||
                lower.contains("turnstile") ||
                lower.contains("ddos-guard") ||
                lower.contains("verify you are human") ||
                lower.contains("attention required") ||
                lower.contains("challenges.cloudflare.com")
            ) {
                throw CaptchaChallengeException(url, "Cloudflare / Bot protection challenge (HTTP $statusCode)")
            }
        } else {
            if (lower.contains("cf-browser-verification") ||
                lower.contains("challenges.cloudflare.com") ||
                lower.contains("id=\"challenge-running\"") ||
                (lower.contains("just a moment...") && lower.contains("enable javascript"))
            ) {
                throw CaptchaChallengeException(url, "Cloudflare challenge page detected.")
            }
        }
    }

    fun parseRawText(title: String, text: String): ScrapedChapter {
        val rawParagraphs = text.split("\n\n", "\r\n\r\n", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return ScrapedChapter(
            novelTitle = "",
            chapterTitle = title.ifBlank { "Pasted Chapter" },
            paragraphs = if (rawParagraphs.isEmpty()) listOf("No content provided.") else rawParagraphs
        )
    }

    fun parseDocument(doc: Document, baseUrl: String): ScrapedChapter {
        // 1. Strip comments, sidebars, social widgets, advertisements universally FIRST
        val unwantedSelectors = listOf(
            "#dle-comments-list", ".comments", ".comments-tree", ".comment-list",
            "#comments", "aside", "#rightside", ".str_right", ".sidebar",
            ".sidebar-block", ".recent-comments", ".popular-block", ".top-rated",
            ".ad-block", ".ads", "script", "style", "noscript",
            ".soc-buttons", ".share-box", ".socials", ".rating", ".comment-form",
            "#disqus_thread", ".disqus", ".fb-comments", ".reactions",
            ".report-chapter", ".author-note-wrapper", ".ad-container",
            ".author-notes", ".author_notes", ".annotation", ".tln-block"
        )
        for (selector in unwantedSelectors) {
            doc.select(selector).remove()
        }

        // 2. Detect Next / Previous Chapter Navigation Links from clean document
        val nextUrl = findNavigationLink(doc, baseUrl, isNext = true)
        val prevUrl = findNavigationLink(doc, baseUrl, isNext = false)

        // 3. Extract Novel & Chapter Title
        val chapterTitle = extractChapterTitle(doc)
        val novelTitle = extractNovelTitle(doc, chapterTitle)

        // 4. Extract Main Story Content Container Universally
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

        // 5. Extract Clean Paragraphs
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

        // Guard against Ranobes / CMS comments-only placeholders
        val isCommentsOnly = paragraphs.any { p ->
            val lower = p.lowercase()
            lower.contains("this page is for comments only") ||
            (lower.contains("comments only") && lower.contains("click on the chapter title"))
        } || (doc.title().contains("Comments", ignoreCase = true) && paragraphs.size <= 4 && paragraphs.any { it.contains("comments only", ignoreCase = true) })

        if (isCommentsOnly) {
            throw IllegalStateException("Received a comments-only discussion page instead of chapter story text: $baseUrl")
        }

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
                (lower.startsWith("chapter ") && text.length < 20)
    }

    fun extractChapterTitle(doc: Document): String {
        return doc.selectFirst("h1.title")?.text()?.trim()
            ?: doc.selectFirst("h1.chapter-title")?.text()?.trim()
            ?: doc.selectFirst(".chapter-title")?.text()?.trim()
            ?: doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst(".title")?.text()?.trim()
            ?: doc.title().trim()
    }

    fun extractNovelTitle(doc: Document, chapterTitle: String): String {
        val breadcrumb = doc.select(".breadcrumb a, .breadcrumbs a").last()?.text()?.trim()
        if (!breadcrumb.isNullOrBlank() &&
            !breadcrumb.equals(chapterTitle, ignoreCase = true) &&
            !breadcrumb.contains("chapter", ignoreCase = true)
        ) {
            return breadcrumb
        }

        val ogNovel = doc.select("meta[property=og:novel:novel_name]").attr("content").trim()
        if (ogNovel.isNotBlank() && !ogNovel.equals(chapterTitle, ignoreCase = true)) {
            return ogNovel
        }

        val siteName = doc.select("meta[property=og:site_name]").attr("content").trim()

        // Inspect document title parts separated by -, |, :, etc.
        val titleParts = doc.title().split("-", "|", "–", "—", ":")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        for (part in titleParts) {
            if (!part.equals(chapterTitle, ignoreCase = true) &&
                !part.contains("chapter", ignoreCase = true) &&
                !part.equals(siteName, ignoreCase = true) &&
                !part.contains("read novel", ignoreCase = true) &&
                !part.contains("light novel", ignoreCase = true) &&
                part.length > 2
            ) {
                return part
            }
        }

        // Return empty string to prevent duplicating the chapter title
        return ""
    }

    fun findNavigationLink(doc: Document, baseUrl: String, isNext: Boolean): String? {
        val baseHost = try { java.net.URI(baseUrl).host?.lowercase()?.removePrefix("www.") } catch (_: Exception) { null }
        val novelPrefix = extractNovelPathPrefix(baseUrl)

        // 1. Check HTML head link elements first
        val headLinkRel = if (isNext) "next" else "prev"
        val headLink = doc.selectFirst("link[rel=$headLinkRel]")?.absUrl("href")
        if (isValidChapterUrl(headLink, baseUrl, baseHost)) {
            return headLink
        }

        // 2. Check site-specific standard button IDs (Ranobes uses #next / #prev)
        val buttonIds = if (isNext) {
            listOf("#next", "#next_url", "#nextChapter", "#next_chapter", "#next-chapter", "#next_page", "#btn-next")
        } else {
            listOf("#prev", "#prev_url", "#prevChapter", "#prev_chapter", "#prev-chapter", "#prev_page", "#btn-prev")
        }
        for (id in buttonIds) {
            val href = doc.selectFirst(id)?.absUrl("href")
            if (isValidChapterUrl(href, baseUrl, baseHost)) {
                return href
            }
        }

        // 3. Check class-specific selectors
        val classSelectors = if (isNext) {
            listOf("a.next", "a.ch-next-btn", "a.nav-next", "a.next_page", "a[rel=next]", "a[title='Right button']", "a[title*=Next]")
        } else {
            listOf("a.prev", "a.ch-prev-btn", "a.nav-prev", "a.prev_page", "a[rel=prev]", "a[title='Left button']", "a[title*=Previous]", "a[title*=Prev]")
        }
        for (sel in classSelectors) {
            val href = doc.selectFirst(sel)?.absUrl("href")
            if (isValidChapterUrl(href, baseUrl, baseHost)) {
                return href
            }
        }

        val exactKeywords = if (isNext) {
            listOf("next chapter", "next", "siguiente", "следующая", "下", ">>", ">")
        } else {
            listOf("prev chapter", "previous chapter", "previous", "prev", "back", "anterior", "предыдущая", "上", "<<", "<")
        }

        // 4. Scoped search inside navigation containers
        val navContainers = listOf(
            ".chapter-nav", ".navigation", ".read-topbar", ".center[data-nosnippet]",
            ".nav-links", ".entry-navigation", ".read-nav", ".wp-post-navigation"
        )
        for (containerSel in navContainers) {
            val container = doc.selectFirst(containerSel) ?: continue
            for (link in container.select("a[href]")) {
                val text = link.text().trim().lowercase()
                if (text.length > 25) continue
                val href = link.absUrl("href")
                if (!isValidChapterUrl(href, baseUrl, baseHost)) continue

                if (matchesNavKeyword(text, exactKeywords)) {
                    return href
                }
            }
        }

        // 5. Fallback search across remaining anchors (comments and sidebars are already stripped)
        var fallbackCandidate: String? = null
        for (link in doc.select("a[href]")) {
            val text = link.text().trim().lowercase()
            if (text.length > 25) continue

            // Filter out common non-chapter words
            if (text.contains("home") || text.contains("index") || text.contains("catalog") ||
                text.contains("bookmark") || text.contains("comment") || text.contains("report") ||
                text.contains("download") || text.contains("share") || text.contains("discord")) {
                continue
            }

            val href = link.absUrl("href")
            if (!isValidChapterUrl(href, baseUrl, baseHost)) continue

            if (matchesNavKeyword(text, exactKeywords)) {
                if (novelPrefix.isNotEmpty() && href.contains(novelPrefix)) {
                    return href
                }
                if (fallbackCandidate == null) {
                    fallbackCandidate = href
                }
            }
        }

        return fallbackCandidate
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
