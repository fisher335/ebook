package com.moyue.reader.data

import android.net.Uri
import androidx.room.withTransaction
import com.moyue.reader.source.BookSource
import com.moyue.reader.source.LocalImporter
import com.moyue.reader.source.SourceBook
import com.moyue.reader.source.SourceChapter
import com.moyue.reader.source.SourceRegistry
import com.moyue.reader.source.TocResult
import com.moyue.reader.util.formatByLanguage
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class BookRepository(
    private val database: AppDatabase,
    private val sourceRegistry: SourceRegistry,
    private val localImporter: LocalImporter
) {
    private val bookDao = database.bookDao()
    private val chapterDao = database.chapterDao()
    private val bookmarkDao = database.bookmarkDao()

    val books: Flow<List<BookEntity>> = bookDao.observeBooks()
    val bookCount: Flow<Int> = bookDao.observeCount()
    val bookmarkCount: Flow<Int> = bookmarkDao.observeCount()

    fun source(id: String): BookSource? = sourceRegistry.get(id)

    suspend fun seedGuideIfEmpty() {
        if (bookDao.count() > 0) return
        val now = System.currentTimeMillis()
        val guideId = "builtin:moyue-guide"
        val guide = BookEntity(
            id = guideId,
            title = "墨阅 · 使用指南",
            author = "墨阅",
            intro = "四分钟了解墨阅：找书、读书、以及为什么这里只有公版书。",
            category = "使用指南",
            sourceId = "local",
            sourceKey = "moyue-guide",
            totalChapters = GUIDE_CHAPTERS.size,
            addedAt = now,
            lastReadAt = 0L,
            coverSeed = "guide".hashCode()
        )
        val chapters = GUIDE_CHAPTERS.mapIndexed { index, (title, content) ->
            ChapterEntity(
                bookId = guideId,
                chapterIndex = index,
                title = title,
                url = null,
                content = formatByLanguage(content),
                cachedAt = now
            )
        }
        database.withTransaction {
            bookDao.upsert(guide)
            chapterDao.insertAll(chapters)
        }
    }

    private companion object {
        val GUIDE_CHAPTERS: List<Pair<String, String>> = listOf(
            "写在前面" to """
                欢迎来到墨阅。

                这是一款安静的文字阅读器。没有推送，没有广告，也不会催你打卡。它只想做好一件事：让你在手机屏幕上，安安稳稳地读完一本书。

                书架的第一本书就是这份使用指南，你可以像读普通书一样翻页。读完之后，长按它即可移出书架。

                愿你在字里行间，找到片刻清静。
            """.trimIndent(),
            "把书找回来" to """
                打开底部的「书城」，你会看到公共领域书源。

                维基文库收录了大量中文典籍：四大名著、经史子集、诸子百家、蒙学经典，都可以免费阅读。点开一本书，确认章节目录后即可加入书架。

                如果想读自己的文件，点击书架右上角的「＋」，可以导入 TXT 与 EPUB。TXT 会按「第X章」自动分章，EPUB 会读取书脊与目录结构。

                搜索框支持按书名或关键词查找，输入后按键盘上的搜索键即可。
            """.trimIndent(),
            "阅读器技巧" to """
                阅读时轻点屏幕两侧可以翻页，轻点中间呼出菜单。

                顶部的书签图标可以收藏当前页；底部的「目录」能快速跳转章节，「设置」里可以调整字号、行距、页边距与背景纸张。

                墨阅会记住每本书读到哪儿。回到书架，点「继续阅读」就能接着上次的位置。

                如果你喜欢夜色，可以在阅读设置里把背景换成「夜间」，同时手机的状态栏也会自动适配。
            """.trimIndent(),
            "关于书源" to """
                墨阅只聚合公共领域内容。

                维基文库的文本由社区校对，遵循 CC BY-SA 协议。

                应用本身不存储、不传播任何受版权保护的书籍，也不提供盗版下载源。请把这里的每一页，都当作人类共同文化遗产的一部分来珍惜。

                如果你有想读而书城没有的公版书，欢迎在本地导入阅读。
            """.trimIndent(),
            "一首小诗" to """
                静夜思

                床前明月光，
                疑是地上霜。
                举头望明月，
                低头思故乡。

                —— 唐 · 李白
            """.trimIndent()
        )
    }

    fun sourceBooks(): List<BookSource> = sourceRegistry.allSources()

    suspend fun getBook(bookId: String): BookEntity? = bookDao.getBook(bookId)

    fun observeBookmarks(bookId: String): Flow<List<BookmarkEntity>> =
        bookmarkDao.observeForBook(bookId)

    fun observeChapters(bookId: String): Flow<List<ChapterEntity>> =
        chapterDao.observeChapters(bookId)

    suspend fun previewToc(sourceBook: SourceBook): TocResult {
        val source = sourceRegistry.get(sourceBook.sourceId)
            ?: throw com.moyue.reader.source.SourceException("未知书源")
        return source.toc(sourceBook)
    }

    suspend fun addFromSource(sourceBook: SourceBook): BookEntity {
        bookDao.findBySource(sourceBook.sourceId, sourceBook.key)?.let { return it }
        val source = sourceRegistry.get(sourceBook.sourceId)
            ?: throw com.moyue.reader.source.SourceException("未知书源")
        val toc = source.toc(sourceBook)
        if (toc.chapters.isEmpty()) {
            throw com.moyue.reader.source.SourceException("这本书暂时没有可阅读的章节")
        }
        val now = System.currentTimeMillis()
        val book = BookEntity(
            id = UUID.randomUUID().toString(),
            title = sourceBook.title,
            author = toc.author ?: sourceBook.author,
            intro = toc.intro ?: sourceBook.intro,
            category = sourceBook.category,
            sourceId = sourceBook.sourceId,
            sourceKey = sourceBook.key,
            totalChapters = toc.chapters.size,
            addedAt = now,
            lastReadAt = 0L,
            currentChapter = 0,
            currentPage = 0,
            progress = 0f,
            coverSeed = sourceBook.coverSeed
        )
        val chapters = toc.chapters.map { chapter ->
            ChapterEntity(
                bookId = book.id,
                chapterIndex = chapter.index,
                title = chapter.title,
                url = chapter.url.ifBlank { null },
                content = toc.preloaded?.get(chapter.index),
                cachedAt = if (toc.preloaded?.containsKey(chapter.index) == true) now else 0L
            )
        }
        database.withTransaction {
            bookDao.upsert(book)
            chapterDao.insertAll(chapters)
        }
        return book
    }

    suspend fun importLocal(uri: Uri): BookEntity {
        val imported = localImporter.import(uri)
        val now = System.currentTimeMillis()
        val book = BookEntity(
            id = UUID.randomUUID().toString(),
            title = imported.title,
            author = imported.author,
            intro = imported.intro,
            category = "本地导入",
            sourceId = "local",
            sourceKey = imported.sourceKey,
            totalChapters = imported.chapters.size,
            addedAt = now,
            lastReadAt = 0L,
            coverSeed = imported.coverSeed,
            localPath = imported.localPath
        )
        val chapters = imported.chapters.mapIndexed { index, (title, content) ->
            ChapterEntity(
                bookId = book.id,
                chapterIndex = index,
                title = title,
                url = null,
                content = content,
                cachedAt = now
            )
        }
        database.withTransaction {
            bookDao.upsert(book)
            chapterDao.insertAll(chapters)
        }
        return book
    }

    suspend fun getChapters(bookId: String): List<ChapterEntity> = chapterDao.getChapters(bookId)

    suspend fun loadChapter(book: BookEntity, chapterIndex: Int): String {
        val chapter = chapterDao.getChapter(book.id, chapterIndex)
            ?: throw com.moyue.reader.source.SourceException("章节不存在")
        chapter.content?.takeIf { it.isNotBlank() }?.let { return it }

        if (book.sourceId == "local") {
            throw com.moyue.reader.source.SourceException("本地章节内容缺失，请重新导入")
        }
        val source = sourceRegistry.get(book.sourceId)
            ?: throw com.moyue.reader.source.SourceException("书源不可用")
        val text = source.chapter(
            sourceBook(book),
            SourceChapter(chapter.chapterIndex, chapter.title, chapter.url.orEmpty())
        )
        chapterDao.saveContent(book.id, chapterIndex, text, System.currentTimeMillis())
        return text
    }

    suspend fun prefetchChapter(book: BookEntity, chapterIndex: Int) {
        if (book.sourceId == "local" || chapterIndex !in 0 until book.totalChapters) return
        runCatching { loadChapter(book, chapterIndex) }
    }

    suspend fun updateProgress(
        bookId: String,
        chapterIndex: Int,
        page: Int,
        progress: Float
    ) {
        bookDao.updateProgress(
            bookId = bookId,
            chapter = chapterIndex,
            page = page,
            progress = progress.coerceIn(0f, 1f),
            time = System.currentTimeMillis()
        )
    }

    suspend fun touch(bookId: String) {
        bookDao.touch(bookId, System.currentTimeMillis())
    }

    suspend fun deleteBook(book: BookEntity) {
        database.withTransaction {
            chapterDao.deleteForBook(book.id)
            bookmarkDao.deleteForBook(book.id)
            bookDao.delete(book)
        }
        localImporter.deleteImported(book.localPath)
    }

    suspend fun clearCachedChapters() {
        chapterDao.clearRemoteCache()
    }

    suspend fun clearShelf() {
        val localBooks = bookDao.getLocalBooks()
        database.withTransaction {
            bookmarkDao.deleteAll()
            chapterDao.clearRemoteCache()
            bookDao.deleteAll()
        }
        localBooks.forEach { localImporter.deleteImported(it.localPath) }
    }

    suspend fun addBookmark(
        bookId: String,
        chapterIndex: Int,
        page: Int,
        chapterTitle: String,
        snippet: String
    ): Boolean {
        val existing = bookmarkDao.find(bookId, chapterIndex, page)
        if (existing != null) {
            bookmarkDao.delete(existing)
            return false
        }
        bookmarkDao.insert(
            BookmarkEntity(
                bookId = bookId,
                chapterIndex = chapterIndex,
                page = page,
                chapterTitle = chapterTitle,
                snippet = snippet.take(120),
                createdAt = System.currentTimeMillis()
            )
        )
        return true
    }

    suspend fun removeBookmark(bookmark: BookmarkEntity) {
        bookmarkDao.delete(bookmark)
    }

    private fun sourceBook(book: BookEntity) = SourceBook(
        sourceId = book.sourceId,
        key = book.sourceKey,
        title = book.title,
        author = book.author,
        intro = book.intro,
        category = book.category,
        coverSeed = book.coverSeed
    )
}
