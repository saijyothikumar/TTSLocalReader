package com.tts.reader

import com.tts.reader.data.local.ChapterEntity
import com.tts.reader.data.local.NovelEntity
import com.tts.reader.data.local.NovelReconciler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelReconcilerTest {

    @Test
    fun testPlanReconciliationWithLegacyOrphanedChapters() {
        val legacyChapters = listOf(
            ChapterEntity(
                url = "https://ranobes.net/novels/123-shadow-slave/c1.html",
                novelId = "", // Legacy v1 schema with no novelId
                novelTitle = "Shadow Slave - Ranobes",
                chapterTitle = "Chapter 1: The First Nightmare",
                chapterNumber = 0f,
                paragraphs = listOf("Sentence one.", "Sentence two.")
            ),
            ChapterEntity(
                url = "https://ranobes.net/novels/123-shadow-slave/c2.html",
                novelId = "",
                novelTitle = "Shadow Slave - Ranobes",
                chapterTitle = "Chapter 2: Mountain of Bones",
                chapterNumber = 0f,
                paragraphs = listOf("Sentence three.", "Sentence four.")
            ),
            ChapterEntity(
                url = "https://lightnovelpub.com/novel/lord-of-the-mysteries/chapter-105",
                novelId = "",
                novelTitle = "Lord of the Mysteries - Light Novel Pub",
                chapterTitle = "Chapter 105: The Long Night",
                chapterNumber = 0f,
                paragraphs = listOf("Sentence five.")
            )
        )

        val plan = NovelReconciler.planReconciliation(
            allChapters = legacyChapters,
            existingNovels = emptyList()
        )

        // 1. All 3 chapters should be classified
        assertEquals(3, plan.updatedChapters.size)

        val ch1Update = plan.updatedChapters.first { it.first == legacyChapters[0].url }
        assertEquals("shadow-slave", ch1Update.second)
        assertEquals(1f, ch1Update.third, 0.01f)

        val ch2Update = plan.updatedChapters.first { it.first == legacyChapters[1].url }
        assertEquals("shadow-slave", ch2Update.second)
        assertEquals(2f, ch2Update.third, 0.01f)

        val ch3Update = plan.updatedChapters.first { it.first == legacyChapters[2].url }
        assertEquals("lord-of-the-mysteries", ch3Update.second)
        assertEquals(105f, ch3Update.third, 0.01f)

        // 2. 2 distinct parent novels created with clean titles
        assertEquals(2, plan.newNovels.size)
        val shadowSlaveNovel = plan.newNovels.first { it.id == "shadow-slave" }
        assertEquals("Shadow Slave", shadowSlaveNovel.title)

        val lotmNovel = plan.newNovels.first { it.id == "lord-of-the-mysteries" }
        assertEquals("Lord of the Mysteries", lotmNovel.title)

        // 3. Chapter counts accurately calculated
        assertEquals(2, plan.novelChapterCounts["shadow-slave"])
        assertEquals(1, plan.novelChapterCounts["lord-of-the-mysteries"])
    }

    @Test
    fun testPlanReconciliationWithExistingParentNovels() {
        val existingNovel = NovelEntity(
            id = "shadow-slave",
            title = "Shadow Slave",
            totalChapters = 1
        )

        val chapters = listOf(
            ChapterEntity(
                url = "https://ranobes.net/novels/123-shadow-slave/c1.html",
                novelId = "shadow-slave", // Already classified
                novelTitle = "Shadow Slave",
                chapterTitle = "Chapter 1",
                chapterNumber = 1f,
                paragraphs = listOf("P1")
            ),
            ChapterEntity(
                url = "https://ranobes.net/novels/123-shadow-slave/c2.html",
                novelId = "", // Newly orphaned
                novelTitle = "Shadow Slave - Ranobes",
                chapterTitle = "Chapter 2",
                chapterNumber = 0f,
                paragraphs = listOf("P2")
            )
        )

        val plan = NovelReconciler.planReconciliation(
            allChapters = chapters,
            existingNovels = listOf(existingNovel)
        )

        // Only the unclassified chapter should be queued for update
        assertEquals(1, plan.updatedChapters.size)
        assertEquals(chapters[1].url, plan.updatedChapters[0].first)
        assertEquals("shadow-slave", plan.updatedChapters[0].second)
        assertEquals(2f, plan.updatedChapters[0].third, 0.01f)

        // No new parent novel created since it already exists
        assertTrue(plan.newNovels.isEmpty())

        // Total count updated to 2
        assertEquals(2, plan.novelChapterCounts["shadow-slave"])
    }

    @Test
    fun testPlanReconciliationEmptyList() {
        val plan = NovelReconciler.planReconciliation(
            allChapters = emptyList(),
            existingNovels = emptyList()
        )

        assertTrue(plan.updatedChapters.isEmpty())
        assertTrue(plan.newNovels.isEmpty())
        assertTrue(plan.novelChapterCounts.isEmpty())
    }

    @Test
    fun testPlanReconciliationIdempotency() {
        val existingNovel = NovelEntity(
            id = "shadow-slave",
            title = "Shadow Slave",
            totalChapters = 2
        )

        val chapters = listOf(
            ChapterEntity(
                url = "https://ranobes.net/c1.html",
                novelId = "shadow-slave",
                novelTitle = "Shadow Slave",
                chapterTitle = "Chapter 1",
                chapterNumber = 1f,
                paragraphs = listOf("P1")
            ),
            ChapterEntity(
                url = "https://ranobes.net/c2.html",
                novelId = "shadow-slave",
                novelTitle = "Shadow Slave",
                chapterTitle = "Chapter 2",
                chapterNumber = 2f,
                paragraphs = listOf("P2")
            )
        )

        val plan = NovelReconciler.planReconciliation(
            allChapters = chapters,
            existingNovels = listOf(existingNovel)
        )

        // Zero chapter mutations required
        assertTrue(plan.updatedChapters.isEmpty())
        // Zero novel insertions required
        assertTrue(plan.newNovels.isEmpty())
        // Counts stay 2
        assertEquals(2, plan.novelChapterCounts["shadow-slave"])
    }
}
