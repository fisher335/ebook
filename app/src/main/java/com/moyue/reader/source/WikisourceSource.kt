package com.moyue.reader.source

import com.moyue.reader.util.cleanExtract
import com.moyue.reader.util.formatForReading
import com.moyue.reader.util.stripTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URLDecoder

class WikisourceSource(private val api: ApiClient) : BookSource {
    override val id: String = "wikisource"
    override val name: String = "维基文库"
    override val tagline: String = "中文公共领域典籍 · 在线检索 · 免费阅读"
    override val searchable: Boolean = true

    override suspend fun catalog(): List<SourceBook> = WikisourceCatalog.books

    override suspend fun toc(book: SourceBook): TocResult {
        val json = api.getJson(
            apiUrl(
                "action" to "parse",
                "page" to book.key,
                "prop" to "text",
                "redirects" to "1"
            )
        )
        if (json.has("error")) {
            throw SourceException("没有找到《${book.title}》的页面")
        }
        val html = json.getAsJsonObject("parse").get("text").asString
        val chapters = withContext(Dispatchers.Default) {
            parseSubpageChapters(html, book.key)
        }
        if (chapters.size >= 2) {
            return TocResult(chapters = chapters, intro = book.intro, author = book.author)
        }

        val text = fetchExtract(book.key)
        if (text.isBlank()) {
            throw SourceException("《${book.title}》暂时没有可阅读的正文")
        }
        val formatted = withContext(Dispatchers.Default) {
            formatForReading(cleanExtract(text))
        }
        return TocResult(
            chapters = listOf(SourceChapter(0, book.title, book.key)),
            intro = book.intro,
            author = book.author,
            preloaded = mapOf(0 to formatted)
        )
    }

    override suspend fun chapter(book: SourceBook, chapter: SourceChapter): String {
        val text = fetchExtract(chapter.url.ifBlank { book.key })
        if (text.isBlank()) throw SourceException("本章暂无内容")
        return withContext(Dispatchers.Default) {
            formatForReading(cleanExtract(text))
        }
    }

    override suspend fun search(query: String): List<SourceBook> {
        if (query.isBlank()) return emptyList()
        val json = api.getJson(
            apiUrl(
                "action" to "query",
                "list" to "search",
                "srsearch" to query.trim(),
                "srnamespace" to "0",
                "srlimit" to "24"
            )
        )
        val search = json.getAsJsonObject("query")?.getAsJsonArray("search") ?: return emptyList()
        return buildList {
            search.forEach { element ->
                val item = element.asJsonObject
                val title = item.get("title")?.asString ?: return@forEach
                if (title.contains("消歧義") || title.contains("消歧义")) return@forEach
                val snippet = stripTags(item.get("snippet")?.asString ?: "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                add(
                    SourceBook(
                        sourceId = id,
                        key = title,
                        title = title,
                        author = "维基文库",
                        intro = snippet.ifBlank { "来自维基文库的公共领域文本" },
                        category = "搜索结果",
                        coverSeed = title.hashCode()
                    )
                )
            }
        }
    }

    private suspend fun fetchExtract(title: String): String {
        val json = api.getJson(
            apiUrl(
                "action" to "query",
                "prop" to "extracts",
                "explaintext" to "1",
                "titles" to title,
                "redirects" to "1"
            )
        )
        val pages = json.getAsJsonObject("query")?.getAsJsonArray("pages") ?: return ""
        if (pages.size() == 0) return ""
        val page = pages[0].asJsonObject
        if (page.get("missing")?.asBoolean == true) return ""
        return page.get("extract")?.asString.orEmpty()
    }

    private fun parseSubpageChapters(html: String, title: String): List<SourceChapter> {
        val prefix = "$title/"
        val seen = HashSet<String>()
        val chapters = ArrayList<SourceChapter>()
        val regex = Regex(
            """<a\b[^>]*?href="(/wiki/[^"#?]+)"[^>]*?>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        regex.findAll(html).forEach { match ->
            val raw = match.groupValues[1]
            val decoded = runCatching {
                URLDecoder.decode(raw.removePrefix("/wiki/").replace("+", "%2B"), "UTF-8")
            }.getOrNull() ?: return@forEach
            val path = decoded.replace('_', ' ')
            if (!path.startsWith(prefix)) return@forEach
            if (path == title || path.contains(':')) return@forEach
            val lastSegment = path.substringAfterLast('/')
            if (lastSegment in excludedSegments) return@forEach
            if (lastSegment.startsWith("讀") || lastSegment.startsWith("读")) return@forEach
            if (!seen.add(path)) return@forEach
            val display = stripTags(match.groupValues[2])
                .ifBlank { lastSegment }
                .take(80)
            chapters.add(SourceChapter(chapters.size, display, path))
        }

        val compact = chapters.filterNot { chapter ->
            chapters.any { other ->
                other.url != chapter.url && other.url.startsWith(chapter.url + "/")
            }
        }
        return compact.mapIndexed { index, chapter -> chapter.copy(index = index) }
    }

    private fun apiUrl(vararg params: Pair<String, String>) =
        "https://zh.wikisource.org/w/api.php".toHttpUrl().newBuilder()
            .query(
                "format" to "json",
                "formatversion" to "2",
                *params
            )
            .build()
            .toString()

    private companion object {
        val excludedSegments = setOf(
            "序", "序說", "序说", "凡例", "目錄", "目录", "前言", "後記", "后记",
            "導讀", "导读", "校閱參考", "校阅参考", "版本", "版本信息", "版權", "版权",
            "附錄", "附录", "註", "注", "說明", "说明", "編輯說明", "编辑说明",
            "全覽", "全览", "參考資料", "参考资料", "外部連結", "外部链接"
        )
    }
}
