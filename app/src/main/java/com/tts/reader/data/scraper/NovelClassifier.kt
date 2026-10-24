package com.tts.reader.data.scraper

import java.net.URI
import java.util.Locale

data class NovelClassificationResult(
    val novelId: String,
    val novelTitle: String,
    val chapterNumber: Float,
    val cleanChapterTitle: String
)

object NovelClassifier {

    private val STRIP_SITE_SUFFIXES = listOf(
        " - read novel online",
        " - read online",
        " - light novel pub",
        " - lightnovelpub",
        " - ranobes",
        " - freewebnovel",
        " | freewebnovel",
        " - webnovel",
        " - novelcool",
        " - boxnovel",
        " - novelhall",
        " - wuxiaworld",
        " - read light novel",
        " read online",
        " (ln)",
        " (wn)",
        " light novel",
        " web novel"
    )

    private val GENERIC_NOVEL_TITLES = setOf(
        "", "universal reader", "web novel", "audio playback",
        "shared story", "no chapter loaded", "novel", "read novel",
        "light novel", "online novel", "free web novel", "pasted story"
    )

    /**
     * Converts any novel title or mirror variation into a consistent, canonical alphanumeric slug ID.
     * e.g. "Shadow Slave - Ranobes" -> "shadow-slave"
     *      "Shadow Slave - Light Novel Pub" -> "shadow-slave"
     *      "Lord of the Mysteries (LN)" -> "lord-of-the-mysteries"
     */
    fun toCanonicalNovelId(title: String): String {
        val clean = cleanNovelTitle(title)
        val slug = clean.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
        return slug.ifBlank { "uncategorized-novel" }
    }

    /**
     * Normalizes a novel display title by stripping site watermarks, domain tags, and mirror branding.
     */
    fun cleanNovelTitle(rawTitle: String): String {
        var clean = rawTitle.trim()

        var changed = true
        while (changed) {
            changed = false
            val lower = clean.lowercase(Locale.ROOT)
            for (suffix in STRIP_SITE_SUFFIXES) {
                if (lower.endsWith(suffix) && clean.length > suffix.length) {
                    clean = clean.substring(0, clean.length - suffix.length).trim()
                    changed = true
                    break
                }
            }
        }

        clean = clean.trimEnd('-', '|', ':', '–', '—', ' ').trim()
        return clean
    }

    /**
     * Extracts chapter numbering (e.g., 105.0) from chapter title or URL for sequential sorting.
     */
    fun extractChapterNumber(chapterTitle: String, url: String = ""): Float {
        // Pattern 1: "Chapter 105", "Ch. 105", "Ch 105.5", "Chapter 105: Title"
        val chRegex = Regex("(?i)(?:chapter|ch\\.?|episode|ep\\.?)\\s*([0-9]+(?:\\.[0-9]+)?)")
        val match = chRegex.find(chapterTitle)
        if (match != null) {
            match.groupValues[1].toFloatOrNull()?.let { return it }
        }

        // Pattern 2: Leading number "105. The Long Night"
        val leadRegex = Regex("^([0-9]+(?:\\.[0-9]+)?)\\s*[-.: ]")
        val leadMatch = leadRegex.find(chapterTitle.trim())
        if (leadMatch != null) {
            leadMatch.groupValues[1].toFloatOrNull()?.let { return it }
        }

        // Pattern 3: URL slug "/c105.html", "/chapter-105", "/105-chapter"
        if (url.isNotBlank()) {
            val urlRegex = Regex("(?i)(?:chapter|ch|c)[-_]?([0-9]+(?:\\.[0-9]+)?)")
            val urlMatch = urlRegex.find(url)
            if (urlMatch != null) {
                urlMatch.groupValues[1].toFloatOrNull()?.let { return it }
            }
        }

        return 0f
    }

    /**
     * Core classification engine: Takes raw scraped fields, page context, URL, and active reading session
     * to deterministically resolve the Novel and Chapter metadata.
     */
    fun classify(
        scrapedNovelTitle: String,
        pageTitle: String = "",
        chapterTitle: String = "",
        url: String = "",
        activeNovelId: String? = null,
        activeNovelTitle: String? = null
    ): NovelClassificationResult {
        var candidateTitle = cleanNovelTitle(scrapedNovelTitle)

        // If scraped novel title is missing or generic, inspect page title parts
        if (candidateTitle.isBlank() || isGenericTitle(candidateTitle)) {
            val fromPageTitle = extractNovelFromPageTitle(pageTitle, chapterTitle)
            if (fromPageTitle.isNotBlank() && !isGenericTitle(fromPageTitle)) {
                candidateTitle = fromPageTitle
            }
        }

        // If still missing, inspect URL path slug
        if (candidateTitle.isBlank() || isGenericTitle(candidateTitle)) {
            val fromUrl = extractNovelFromUrlSlug(url)
            if (fromUrl.isNotBlank() && !isGenericTitle(fromUrl)) {
                candidateTitle = fromUrl
            }
        }

        // Active context inheritance: When navigating between chapters of an existing novel
        if ((candidateTitle.isBlank() || isGenericTitle(candidateTitle)) && !activeNovelTitle.isNullOrBlank()) {
            candidateTitle = activeNovelTitle
        }

        // Fallback
        if (candidateTitle.isBlank() || isGenericTitle(candidateTitle)) {
            candidateTitle = if (url.startsWith("local_paste")) "Pasted Stories" else "Uncategorized Novel"
        }

        val canonicalId = if (candidateTitle == "Uncategorized Novel" && !activeNovelId.isNullOrBlank()) {
            activeNovelId
        } else {
            toCanonicalNovelId(candidateTitle)
        }

        val chNumber = extractChapterNumber(chapterTitle, url)
        val cleanChTitle = chapterTitle.ifBlank {
            if (chNumber > 0f) "Chapter ${chNumber.toInt()}" else "Chapter"
        }

        return NovelClassificationResult(
            novelId = canonicalId,
            novelTitle = candidateTitle,
            chapterNumber = chNumber,
            cleanChapterTitle = cleanChTitle
        )
    }

    private fun isGenericTitle(title: String): Boolean {
        val lower = title.lowercase(Locale.ROOT).trim()
        return GENERIC_NOVEL_TITLES.contains(lower)
    }

    private fun extractNovelFromPageTitle(pageTitle: String, chapterTitle: String): String {
        if (pageTitle.isBlank()) return ""
        val parts = pageTitle.split("-", "|", "–", "—", ":")
            .map { cleanNovelTitle(it) }
            .filter { it.isNotBlank() && !isGenericTitle(it) }

        for (part in parts) {
            val lower = part.lowercase(Locale.ROOT)
            if (!part.equals(chapterTitle, ignoreCase = true) &&
                !lower.contains("chapter") &&
                !lower.contains("ch.") &&
                part.length > 2
            ) {
                return part
            }
        }
        return ""
    }

    private fun extractNovelFromUrlSlug(url: String): String {
        return try {
            val uri = URI(url)
            val path = uri.path ?: return ""
            val segments = path.split("/").filter { it.isNotBlank() }
            if (segments.size >= 2) {
                val candidate = if (segments[0] == "novels" || segments[0] == "novel" || segments[0] == "book" || segments[0] == "ranobe") {
                    segments.getOrNull(1)
                } else {
                    segments.firstOrNull()
                }

                if (!candidate.isNullOrBlank()) {
                    val cleanSlug = candidate.replace(Regex("^[0-9]+-"), "")
                        .replace("-", " ")
                        .replace("_", " ")
                        .trim()
                    return cleanSlug.split(" ")
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { word ->
                            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                        }
                }
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }
}
