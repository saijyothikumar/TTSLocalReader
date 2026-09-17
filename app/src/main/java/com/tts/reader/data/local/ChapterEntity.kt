package com.tts.reader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Entity(tableName = "chapters")
@TypeConverters(StringListConverter::class)
data class ChapterEntity(
    @PrimaryKey
    val url: String,
    val novelTitle: String,
    val chapterTitle: String,
    val paragraphs: List<String>,
    val nextChapterUrl: String? = null,
    val prevChapterUrl: String? = null,
    val lastSentenceIndex: Int = 0,
    val scrollPosition: Int = 0,
    val cachedAtTimestamp: Long = System.currentTimeMillis()
)

class StringListConverter {
    @TypeConverter
    fun fromList(list: List<String>): String {
        return list.joinToString(DELIMITER)
    }

    @TypeConverter
    fun toList(data: String): List<String> {
        if (data.isEmpty()) return emptyList()
        return data.split(DELIMITER)
    }

    companion object {
        private const val DELIMITER = "___PARAGRAPH_SEP___"
    }
}
