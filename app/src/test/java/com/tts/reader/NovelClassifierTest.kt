package com.tts.reader

import com.tts.reader.data.scraper.NovelClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class NovelClassifierTest {

    @Test
    fun testCanonicalNovelIdAcrossMirrorSites() {
        // Different mirrors for the same novel must yield the EXACT same novelId
        val idRanobes = NovelClassifier.toCanonicalNovelId("Shadow Slave - Ranobes")
        val idLightNovelPub = NovelClassifier.toCanonicalNovelId("Shadow Slave - Light Novel Pub")
        val idFreeWebNovel = NovelClassifier.toCanonicalNovelId("Shadow Slave | FreeWebNovel")
        val idGeneric = NovelClassifier.toCanonicalNovelId("Shadow Slave (WN)")

        assertEquals("shadow-slave", idRanobes)
        assertEquals("shadow-slave", idLightNovelPub)
        assertEquals("shadow-slave", idFreeWebNovel)
        assertEquals("shadow-slave", idGeneric)

        assertEquals("lord-of-the-mysteries", NovelClassifier.toCanonicalNovelId("Lord of the Mysteries - Read Online"))
        assertEquals("the-beginning-after-the-end", NovelClassifier.toCanonicalNovelId("The Beginning After the End (LN)"))
    }

    @Test
    fun testCleanNovelTitle() {
        assertEquals("Shadow Slave", NovelClassifier.cleanNovelTitle("Shadow Slave - Ranobes"))
        assertEquals("Lord of the Mysteries", NovelClassifier.cleanNovelTitle("Lord of the Mysteries - Light Novel Pub"))
        assertEquals("Reverend Insanity", NovelClassifier.cleanNovelTitle("Reverend Insanity | FreeWebNovel"))
        assertEquals("Omniscient Reader's Viewpoint", NovelClassifier.cleanNovelTitle("Omniscient Reader's Viewpoint - Read Novel Online"))
    }

    @Test
    fun testExtractChapterNumber() {
        assertEquals(105f, NovelClassifier.extractChapterNumber("Chapter 105: The Long March"), 0.01f)
        assertEquals(42.5f, NovelClassifier.extractChapterNumber("Ch. 42.5 Side Story"), 0.01f)
        assertEquals(500f, NovelClassifier.extractChapterNumber("500. The End of An Era"), 0.01f)
        assertEquals(12f, NovelClassifier.extractChapterNumber("Episode 12", ""), 0.01f)
        assertEquals(99f, NovelClassifier.extractChapterNumber("Next", "https://ranobes.net/c99.html"), 0.01f)
    }

    @Test
    fun testClassifyWithActiveContextInheritance() {
        // When scraped novel title is missing and page title has no novel, inherit active novel
        val result = NovelClassifier.classify(
            scrapedNovelTitle = "",
            pageTitle = "Chapter 15",
            chapterTitle = "Chapter 15",
            url = "https://mirror-unknown.com/ch15.html",
            activeNovelId = "shadow-slave",
            activeNovelTitle = "Shadow Slave"
        )

        assertEquals("shadow-slave", result.novelId)
        assertEquals("Shadow Slave", result.novelTitle)
        assertEquals(15f, result.chapterNumber, 0.01f)
    }

    @Test
    fun testClassifyDeducesFromUrlSlug() {
        val result = NovelClassifier.classify(
            scrapedNovelTitle = "",
            pageTitle = "Chapter 1",
            chapterTitle = "Chapter 1",
            url = "https://ranobes.net/novels/12345-shadow-slave/c1.html"
        )

        assertEquals("shadow-slave", result.novelId)
        assertEquals("Shadow Slave", result.novelTitle)
        assertEquals(1f, result.chapterNumber, 0.01f)
    }

    @Test
    fun testPastedStoryFallback() {
        val result = NovelClassifier.classify(
            scrapedNovelTitle = "",
            pageTitle = "",
            chapterTitle = "Pasted Chapter 1",
            url = "local_paste_12345"
        )

        assertEquals("pasted-stories", result.novelId)
        assertEquals("Pasted Stories", result.novelTitle)
    }
}
