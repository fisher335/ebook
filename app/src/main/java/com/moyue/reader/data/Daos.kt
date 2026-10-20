package com.moyue.reader.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query(
        "SELECT * FROM books ORDER BY " +
            "CASE WHEN lastReadAt = 0 THEN 1 ELSE 0 END, lastReadAt DESC, addedAt DESC"
    )
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getBook(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE localPath IS NOT NULL")
    suspend fun getLocalBooks(): List<BookEntity>

    @Query("SELECT * FROM books WHERE sourceId = :sourceId AND sourceKey = :sourceKey LIMIT 1")
    suspend fun findBySource(sourceId: String, sourceKey: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Query(
        "UPDATE books SET currentChapter = :chapter, currentPage = :page, " +
            "progress = :progress, lastReadAt = :time WHERE id = :bookId"
    )
    suspend fun updateProgress(
        bookId: String,
        chapter: Int,
        page: Int,
        progress: Float,
        time: Long
    )

    @Query("UPDATE books SET lastReadAt = :time WHERE id = :bookId")
    suspend fun touch(bookId: String, time: Long)

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("DELETE FROM books")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM books")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex")
    fun observeChapters(bookId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex")
    suspend fun getChapters(bookId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND chapterIndex = :index LIMIT 1")
    suspend fun getChapter(bookId: String, index: Int): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterEntity>)

    @Query(
        "UPDATE chapters SET content = :content, cachedAt = :time " +
            "WHERE bookId = :bookId AND chapterIndex = :index"
    )
    suspend fun saveContent(bookId: String, index: Int, content: String, time: Long)

    @Query("UPDATE chapters SET content = NULL, cachedAt = 0 WHERE bookId = :bookId")
    suspend fun clearCacheForBook(bookId: String)

    @Query(
        "UPDATE chapters SET content = NULL, cachedAt = 0 " +
            "WHERE bookId IN (SELECT id FROM books WHERE sourceId NOT IN ('local', 'gutenberg'))"
    )
    suspend fun clearRemoteCache()

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}

@Dao
interface BookmarkDao {
    @Query(
        "SELECT * FROM bookmarks WHERE bookId = :bookId " +
            "ORDER BY chapterIndex ASC, page ASC, createdAt DESC"
    )
    fun observeForBook(bookId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND chapterIndex = :chapter AND page = :page LIMIT 1")
    suspend fun find(bookId: String, chapter: Int, page: Int): BookmarkEntity?

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM bookmarks")
    fun observeCount(): Flow<Int>
}
