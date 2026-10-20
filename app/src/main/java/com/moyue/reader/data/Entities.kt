package com.moyue.reader.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [Index(value = ["sourceId", "sourceKey"], unique = true)]
)
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val intro: String,
    val category: String,
    val sourceId: String,
    val sourceKey: String,
    val totalChapters: Int,
    val addedAt: Long,
    val lastReadAt: Long = 0L,
    val currentChapter: Int = 0,
    val currentPage: Int = 0,
    val progress: Float = 0f,
    val coverSeed: Int = 0,
    val localPath: String? = null
)

@Entity(
    tableName = "chapters",
    primaryKeys = ["bookId", "chapterIndex"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class ChapterEntity(
    val bookId: String,
    val chapterIndex: Int,
    val title: String,
    val url: String? = null,
    val content: String? = null,
    val cachedAt: Long = 0L
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookId: String,
    val chapterIndex: Int,
    val page: Int,
    val chapterTitle: String,
    val snippet: String,
    val createdAt: Long
)
