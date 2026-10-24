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

    // Common HTML entities found in scraped web novel text
    private val HTML_ENTITIES = listOf(
        "&nbsp;" to " ",
        "&quot;" to "\"",
        "&amp;" to " and ",
        "&lt;" to "<",
        "&gt;" to ">",
        "&#39;" to "'",
        "&rsquo;" to "'",
        "&lsquo;" to "'",
        "&rdquo;" to "\"",
        "&ldquo;" to "\"",
        "&hellip;" to "...",
        "&mdash;" to ", ",
        "&ndash;" to ", "
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

        // 1. Unescape residual HTML entities
        for ((entity, replacement) in HTML_ENTITIES) {
            if (speechText.contains(entity)) {
                speechText = speechText.replace(entity, replacement)
            }
        }

        // 2. Normalize full-width Asian punctuation and quotes commonly mixed in translated web novels
        speechText = speechText
            .replace('。', '.')
            .replace('，', ',')
            .replace('！', '!')
            .replace('？', '?')
            .replace('：', ':')
            .replace('；', ';')
            .replace('「', '"')
            .replace('」', '"')
            .replace('『', '"')
            .replace('』', '"')
            .replace('〜', ',')
            .replace('“', '"')
            .replace('”', '"')
            .replace('‘', '\'')
            .replace('’', '\'')

        // 3. Dialogue tildes (e.g., "Wait~" or "Ah~~~" -> "Wait," so TTS does not say "tilde" or glitch)
        speechText = speechText.replace(Regex("(?<=\\w)~+(?=[!?.,\\s\"']|$)"), ", ")
            .replace(Regex("~+"), " ")

        // 4. Clean LitRPG bracketed system announcements (e.g. "[System: Level Up! You gained 50 EXP.]" -> "System: Level Up! You gained 50 EXP.")
        speechText = speechText.replace(Regex("\\[([^\\[\\]\\r\\n]{1,150})\\]")) { match ->
            match.groupValues[1] + " "
        }

        // 5. Replace long sequences of dashes, asterisks, equals with a clean pause or space
        speechText = speechText
            .replace(REPETITIVE_SYMBOLS_REGEX, " ")
            // Collapse 4+ repeated dots down to standard ellipsis for natural pause
            .replace(Regex("\\.{4,}"), "... ")

        // 6. Dialogue dashes: convert isolated em-dashes and en-dashes between words to natural pause comma
        speechText = speechText
            .replace(Regex("(?<=\\w)\\s*[—–]\\s*(?=\\w)"), ", ")

        // 7. Action/emphasis markers: *sigh* -> sigh, _gasps_ -> gasps (prevents TTS from saying "asterisk" or "underscore")
        speechText = speechText
            .replace(Regex("(?<=[\\s\"'(]|^)\\*([a-zA-Z0-9_'\\s]{1,40})\\*(?=[\\s.,!?\"')]|$)"), "$1")
            .replace(Regex("(?<=[\\s\"'(]|^)_([a-zA-Z0-9_'\\s]{1,40})_(?=[\\s.,!?\"')]|$)"), "$1")

        // 8. Collapse redundant punctuation (e.g. "???" -> "?", "!!!" -> "!", "?!?" -> "?!")
        speechText = speechText
            .replace(Regex("\\?{2,}"), "?")
            .replace(Regex("!{2,}"), "!")
            // Normalize multiple whitespaces
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimStart(',', ' ', '-', '—')
            .trimEnd(',', ' ', '-', '—')
            .trim()

        // If after cleaning it has no letters or digits, skip speaking
        if (speechText.none { it.isLetterOrDigit() }) {
            return null
        }

        return speechText
    }
}
