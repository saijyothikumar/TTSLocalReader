package com.tts.reader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {

    @Query("SELECT * FROM novels ORDER BY lastReadTimestamp DESC")
    fun getAllNovels(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels WHERE id = :id LIMIT 1")
    suspend fun getNovelById(id: String): NovelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateNovel(novel: NovelEntity)

    @Update
    suspend fun updateNovel(novel: NovelEntity)

    @Query("UPDATE novels SET lastReadUrl = :url, lastReadChapterTitle = :chapterTitle, lastReadSentenceIndex = :sentenceIndex, lastReadTimestamp = :timestamp WHERE id = :id")
    suspend fun updateLastRead(
        id: String,
        url: String,
        chapterTitle: String,
        sentenceIndex: Int,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE novels SET totalChapters = :total WHERE id = :id")
    suspend fun updateTotalChapters(id: String, total: Int)

    @Query("DELETE FROM novels WHERE id = :id")
    suspend fun deleteNovel(id: String)

    @Query("DELETE FROM novels")
    suspend fun clearAllNovels()
}
