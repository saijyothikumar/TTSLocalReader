package com.tts.reader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NovelEntity::class, ChapterEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chapterDao(): ChapterDao
    abstract fun novelDao(): NovelDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `novels` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `coverUrl` TEXT,
                        `lastReadUrl` TEXT,
                        `lastReadChapterTitle` TEXT,
                        `lastReadSentenceIndex` INTEGER NOT NULL DEFAULT 0,
                        `totalChapters` INTEGER NOT NULL DEFAULT 0,
                        `lastReadTimestamp` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE `chapters` ADD COLUMN `novelId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `chapters` ADD COLUMN `chapterNumber` REAL NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_novelId` ON `chapters` (`novelId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure novels table and index exist if upgrading from v2
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `novels` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `coverUrl` TEXT,
                        `lastReadUrl` TEXT,
                        `lastReadChapterTitle` TEXT,
                        `lastReadSentenceIndex` INTEGER NOT NULL DEFAULT 0,
                        `totalChapters` INTEGER NOT NULL DEFAULT 0,
                        `lastReadTimestamp` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_novelId` ON `chapters` (`novelId`)")
            }
        }

        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2.migrate(db)
                MIGRATION_2_3.migrate(db)
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tts_novel_reader.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_1_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
