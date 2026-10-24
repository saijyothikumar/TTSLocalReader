package com.tts.reader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "novels")
data class NovelEntity(
    @PrimaryKey
    val id: String, // Canonical alphanumeric slug, e.g. "shadow-slave", "lord-of-the-mysteries"
    val title: String, // Clean display name, e.g. "Shadow Slave"
    val coverUrl: String? = null,
    val lastReadUrl: String? = null,
    val lastReadChapterTitle: String? = null,
    val lastReadSentenceIndex: Int = 0,
    val totalChapters: Int = 0,
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
