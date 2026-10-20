package com.moyue.reader.source

data class SourceBook(
    val sourceId: String,
    val key: String,
    val title: String,
    val author: String,
    val intro: String,
    val category: String,
    val coverSeed: Int = key.hashCode(),
    val totalChaptersHint: Int = 0
)

data class SourceChapter(
    val index: Int,
    val title: String,
    val url: String
)

data class TocResult(
    val chapters: List<SourceChapter>,
    val intro: String? = null,
    val author: String? = null,
    val preloaded: Map<Int, String>? = null
)

interface BookSource {
    val id: String
    val name: String
    val tagline: String
    val searchable: Boolean

    suspend fun catalog(): List<SourceBook>
    suspend fun toc(book: SourceBook): TocResult
    suspend fun chapter(book: SourceBook, chapter: SourceChapter): String
    suspend fun search(query: String): List<SourceBook>
}

class SourceException(
    message: String,
    cause: Throwable? = null,
    val httpCode: Int = 0
) : Exception(message, cause)
