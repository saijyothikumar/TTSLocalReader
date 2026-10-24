package com.tts.reader

import com.tts.reader.data.local.ChapterEntity
import com.tts.reader.data.scraper.NovelIdentifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelIdentifierTest {

    @Test
    fun testNormalizeNovelTitleWithSiteDecorations() {
        val title1 = "Shadow Slave - Read Novel Online"
        assertEquals("Shadow Slave", NovelIdentifier.normalizeNovelTitle(title1))

        val title2 = "Lord of the Mysteries - Light Novel Pub"
        assertEquals("Lord of the Mysteries", NovelIdentifier.normalizeNovelTitle(title2))

        val title3 = "The Beginning After the End (LN)"
        assertEquals("The Beginning After the End", NovelIdentifier.normalizeNovelTitle(title3))

        val title4 = "Omniscient Reader's Viewpoint - Ranobes"
        assertEquals("Omniscient Reader's Viewpoint", NovelIdentifier.normalizeNovelTitle(title4))
    }

    @Test
    fun testNormalizeNovelTitleFallbackToUrl() {
        val url = "https://ranobes.net/novels/123456-shadow-slave/c1.html"
        val title = NovelIdentifier.normalizeNovelTitle("", "Chapter 1", url)
        assertEquals("Shadow Slave", title)
    }

    @Test
    fun testNormalizeNovelTitleFallbackToChapter() {
        val title = NovelIdentifier.normalizeNovelTitle("", "Reverend Insanity - Chapter 500", "")
        assertEquals("Reverend Insanity", title)
    }

    @Test
    fun testGroupChaptersByNovel() {
        val c1 = ChapterEntity(
            url = "https://ranobes.net/novels/shadow-slave/c1.html",
            novelTitle = "Shadow Slave - Ranobes",
            chapterTitle = "Chapter 1",
            paragraphs = listOf("P1")
        )
        val c2 = ChapterEntity(
            url = "https://lightnovelpub.vip/shadow-slave/chapter-2",
            novelTitle = "Shadow Slave - Light Novel Pub",
            chapterTitle = "Chapter 2",
            paragraphs = listOf("P2")
        )
        val c3 = ChapterEntity(
            url = "https://freewebnovel.com/lord-of-mysteries/c1",
            novelTitle = "Lord of the Mysteries",
            chapterTitle = "Chapter 1",
            paragraphs = listOf("P3")
        )

        val groups = NovelIdentifier.groupChaptersByNovel(listOf(c1, c2, c3))
        assertEquals(2, groups.size)
        assertTrue(groups.containsKey("Shadow Slave"))
        assertTrue(groups.containsKey("Lord of the Mysteries"))
        assertEquals(2, groups["Shadow Slave"]?.size)
    }
}
