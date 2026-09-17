package com.tts.reader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE url = :url LIMIT 1")
    suspend fun getChapterByUrl(url: String): ChapterEntity?

    @Query("SELECT * FROM chapters ORDER BY cachedAtTimestamp DESC")
    fun getAllCachedChapters(): Flow<List<ChapterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity)

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Query("UPDATE chapters SET lastSentenceIndex = :sentenceIndex, scrollPosition = :scrollPos WHERE url = :url")
    suspend fun updateProgress(url: String, sentenceIndex: Int, scrollPos: Int)

    @Query("DELETE FROM chapters WHERE url = :url")
    suspend fun deleteChapter(url: String)
}
