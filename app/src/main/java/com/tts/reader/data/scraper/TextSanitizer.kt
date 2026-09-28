package com.tts.reader.data.scraper

import java.util.regex.Pattern

object TextSanitizer {

    // Regex matching lines consisting purely of horizontal fillers, punctuation, or repeated symbols
    private val FILLER_LINE_PATTERN = Pattern.compile("^[\\s\\p{Punct}\\p{Pd}\\p{Po}\\p{S}—–―*~=_#+\\/\\\\|•◆◇✦★⚜·…]+$")

    // Common inline repetitive symbol bursts (e.g. "----", "****", "====")
    private val REPETITIVE_SYMBOLS_REGEX = Regex("[-*=_~—–―#]{2,}")

    // Author note opening markers
    private val AUTHOR_NOTE_PREFIXES = listOf(
        "author's note",
        "author note",
        "authors note",
        "a/n:",
        "a/n ",
        "a.n.",
        "t/n:",
        "tl note:",
        "tln:",
        "editor's note",
        "translator's note",
        "note from author",
        "creator's note"
    )

    /**
     * Identifies if a paragraph or line is purely a visual break/horizontal rule
     * that conveys no verbal story content.
     */
    fun isVisualDivider(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed.length <= 1 && !trimmed[0].isLetterOrDigit()) return true

        // Match lines made entirely of punctuation/symbols/dividers
        if (FILLER_LINE_PATTERN.matcher(trimmed).matches()) {
            return true
        }

        // Lines with very few letters compared to heavy symbols (e.g., "- - - o - - -")
        val lettersOrDigits = trimmed.count { it.isLetterOrDigit() }
        if (lettersOrDigits <= 1 && trimmed.length >= 3) {
            return true
        }

        return false
    }

    /**
     * Identifies if a paragraph is an Author's or Translator's note.
     */
    fun isAuthorNote(text: String): Boolean {
        val cleaned = text.trim().lowercase()
            .removePrefix("[")
            .removePrefix("(")
            .removePrefix("{")
            .trim()

        return AUTHOR_NOTE_PREFIXES.any { cleaned.startsWith(it) }
    }

    /**
     * Sanitizes a paragraph for Text-to-Speech playback.
     * Returns null if the paragraph is a visual divider and should be completely skipped.
     */
    fun sanitizeForSpeech(paragraph: String): String? {
        if (isVisualDivider(paragraph)) {
            return null
        }

        var speechText = paragraph
            // Replace long sequences of dashes, asterisks, equals with a clean pause or space
            .replace(REPETITIVE_SYMBOLS_REGEX, " ")
            // Normalize multiple whitespaces
            .replace(Regex("\\s+"), " ")
            .trim()

        // If after cleaning it has no letters or digits, skip speaking
        if (speechText.none { it.isLetterOrDigit() }) {
            return null
        }

        return speechText
    }
}
