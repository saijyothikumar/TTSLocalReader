package com.tts.reader.data.scraper

import com.tts.reader.data.local.ChapterEntity
import java.net.URI
import java.util.Locale

object NovelIdentifier {

    private val STRIP_SUFFIXES = listOf(
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

    /**
     * Normalizes a novel title to group chapters from different mirror sites cleanly into a single folder.
     */
    fun normalizeNovelTitle(novelTitle: String, chapterTitle: String = "", url: String = ""): String {
        var cleanTitle = novelTitle.trim()

        // Strip known site decoration suffixes
        var changed = true
        while (changed) {
            changed = false
            val lower = cleanTitle.lowercase(Locale.ROOT)
            for (suffix in STRIP_SUFFIXES) {
                if (lower.endsWith(suffix) && cleanTitle.length > suffix.length) {
                    cleanTitle = cleanTitle.substring(0, cleanTitle.length - suffix.length).trim()
                    changed = true
                    break
                }
            }
        }

        // Clean trailing separators (-, |, :, etc.)
        cleanTitle = cleanTitle.trimEnd('-', '|', ':', '–', '—', ' ').trim()

        // If title is generic or empty, deduce from chapter title or URL slug
        if (cleanTitle.isBlank() || isGenericTitle(cleanTitle)) {
            val fromChapter = extractNovelFromChapterTitle(chapterTitle)
            if (fromChapter.isNotBlank() && !isGenericTitle(fromChapter)) {
                return fromChapter
            }

            val fromUrl = extractNovelFromUrl(url)
            if (fromUrl.isNotBlank() && !isGenericTitle(fromUrl)) {
                return fromUrl
            }
        }

        if (cleanTitle.isBlank() || isGenericTitle(cleanTitle)) {
            return if (url.startsWith("local_paste")) "Pasted Stories" else "Uncategorized Novel"
        }

        return cleanTitle
    }

    private fun isGenericTitle(title: String): Boolean {
        val lower = title.lowercase(Locale.ROOT).trim()
        return lower.isEmpty() ||
                lower == "universal reader" ||
                lower == "web novel" ||
                lower == "audio playback" ||
                lower == "shared story" ||
                lower == "no chapter loaded" ||
                lower == "novel"
    }

    private fun extractNovelFromChapterTitle(chapterTitle: String): String {
        val separators = listOf(" - Chapter", " - Ch.", " - ch ", " Chapter ", " Ch.")
        for (sep in separators) {
            val idx = chapterTitle.indexOf(sep, ignoreCase = true)
            if (idx > 2) {
                return chapterTitle.substring(0, idx).trim()
            }
        }
        return ""
    }

    private fun extractNovelFromUrl(url: String): String {
        return try {
            val uri = URI(url)
            val path = uri.path ?: return ""
            val segments = path.split("/").filter { it.isNotBlank() }
            if (segments.size >= 2) {
                // Common patterns: /novels/shadow-slave/..., /shadow-slave/chapter-1
                val candidate = if (segments[0] == "novels" || segments[0] == "novel" || segments[0] == "book") {
                    segments.getOrNull(1)
                } else {
                    segments.firstOrNull()
                }

                if (!candidate.isNullOrBlank()) {
                    // Strip leading numeric IDs if present (e.g., 12345-shadow-slave)
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

    /**
     * Groups a list of cached chapters into structured folders keyed by their normalized novel title.
     * Chapters within each folder are naturally ordered.
     */
    fun groupChaptersByNovel(chapters: List<ChapterEntity>): Map<String, List<ChapterEntity>> {
        return chapters.groupBy { chapter ->
            normalizeNovelTitle(chapter.novelTitle, chapter.chapterTitle, chapter.url)
        }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }
}
