package com.tts.reader.data.local

import com.tts.reader.data.scraper.NovelClassifier

/**
 * Idempotent self-healing reconciler engine:
 * Detects legacy, orphaned, or unclassified chapters (e.g. from v1 schema migrations),
 * generates canonical parent novels, and synchronizes chapter counts so that no
 * user reading data is ever lost or invisible on the Bookshelf.
 */
object NovelReconciler {

    data class ReconciliationPlan(
        val updatedChapters: List<Triple<String, String, Float>>, // url, novelId, chapterNumber
        val newNovels: List<NovelEntity>,
        val novelChapterCounts: Map<String, Int>
    )

    /**
     * Pure business logic: computes the necessary mutations to bring the database
     * to a fully reconciled, classified state.
     */
    fun planReconciliation(
        allChapters: List<ChapterEntity>,
        existingNovels: List<NovelEntity>
    ): ReconciliationPlan {
        if (allChapters.isEmpty()) {
            return ReconciliationPlan(emptyList(), emptyList(), emptyMap())
        }

        val existingMap = existingNovels.associateBy { it.id }.toMutableMap()
        val novelChapterCounts = mutableMapOf<String, Int>()
        val updatedChapters = mutableListOf<Triple<String, String, Float>>()
        val newNovels = mutableListOf<NovelEntity>()

        for (chapter in allChapters) {
            var novelId = chapter.novelId
            var chapterNum = chapter.chapterNumber

            if (novelId.isBlank()) {
                val classified = NovelClassifier.classify(
                    scrapedNovelTitle = chapter.novelTitle,
                    chapterTitle = chapter.chapterTitle,
                    url = chapter.url
                )
                novelId = classified.novelId
                chapterNum = if (chapterNum == 0f) classified.chapterNumber else chapterNum
                updatedChapters.add(Triple(chapter.url, novelId, chapterNum))
            }

            novelChapterCounts[novelId] = (novelChapterCounts[novelId] ?: 0) + 1

            val existingNovel = existingMap[novelId]
            if (existingNovel == null) {
                val cleanTitle = NovelClassifier.cleanNovelTitle(chapter.novelTitle).ifBlank { "Saved Novel" }
                val newNovel = NovelEntity(
                    id = novelId,
                    title = cleanTitle,
                    lastReadUrl = chapter.url,
                    lastReadChapterTitle = chapter.chapterTitle,
                    lastReadSentenceIndex = chapter.lastSentenceIndex,
                    totalChapters = 1,
                    lastReadTimestamp = chapter.cachedAtTimestamp
                )
                existingMap[novelId] = newNovel
                newNovels.add(newNovel)
            }
        }

        return ReconciliationPlan(
            updatedChapters = updatedChapters,
            newNovels = newNovels,
            novelChapterCounts = novelChapterCounts
        )
    }

    /**
     * Executes the reconciliation plan against Room DAOs in an idempotent fashion.
     */
    suspend fun reconcile(chapterDao: ChapterDao, novelDao: NovelDao) {
        val allChapters = chapterDao.getAllChaptersDirect()
        if (allChapters.isEmpty()) return

        val existingNovels = novelDao.getAllNovelsDirect()
        val plan = planReconciliation(allChapters, existingNovels)

        for ((url, novelId, chNum) in plan.updatedChapters) {
            chapterDao.updateChapterClassification(url, novelId, chNum)
        }

        for (newNovel in plan.newNovels) {
            novelDao.insertOrUpdateNovel(newNovel)
        }

        for ((novelId, count) in plan.novelChapterCounts) {
            novelDao.updateTotalChapters(novelId, count)
        }
    }
}
