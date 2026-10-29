package com.moyue.reader.source

import com.moyue.reader.util.formatByLanguage
import com.moyue.reader.util.htmlToText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder

/**
 * A small, deliberately simple rule engine for user-supplied book sources.
 * It supports CSS selectors, "selector@attribute" extraction and common chapter pagination.
 */
class RuleBookSource(
    private val config: CustomSource,
    private val api: ApiClient
) : BookSource {
    override val id: String = config.id.orEmpty()
    override val name: String = config.name.orEmpty()
    override val tagline: String = config.tagline.orEmpty()
    override val searchable: Boolean = config.searchable

    private val baseUrl = config.baseUrl.orEmpty()
    private val contentSelector = config.contentSelector.orEmpty()
    private val contentRemove = config.contentRemove.orEmpty()
    private val tocLinkSelector = config.tocLinkSelector.orEmpty().ifBlank { "a[href]" }
    private val tocLinkRegex = config.tocLinkRegex.orEmpty().ifBlank { null }
    private val explore = config.explore.orEmpty()

    override suspend fun catalog(): List<SourceBook> = withContext(Dispatchers.IO) {
        val items = if (explore.isNotEmpty()) explore else loadExplore()
        items.mapNotNull { item ->
            val url = item.url?.trim().orEmpty()
            val title = item.title?.trim().orEmpty()
            if (url.isBlank() || title.isBlank()) return@mapNotNull null
            SourceBook(
                sourceId = id,
                key = resolveFromBase(url),
                title = title,
                author = item.author.orEmpty().ifBlank { "佚名" },
                intro = item.intro.orEmpty(),
                category = item.category.orEmpty().ifBlank { "精选" },
                coverSeed = title.hashCode()
            )
        }
    }

    override suspend fun toc(book: SourceBook): TocResult = withContext(Dispatchers.IO) {
        val bookUrl = resolve(baseUrl, book.key)
        val rawToc = config.tocUrl.orEmpty().ifBlank { "{bookUrl}" }
            .replace("{bookUrl}", bookUrl)
            .replace("{key}", bookUrl)
        val tocUrl = resolve(bookUrl, rawToc)
        val html = api.getText(tocUrl)
        val doc = Jsoup.parse(html, tocUrl)
        val bookPath = runCatching { URI(tocUrl).path.orEmpty() }.getOrDefault("")
        val regex = tocLinkRegex?.let {
            val pattern = it.replace("{bookPath}", Regex.escape(bookPath))
            runCatching { Regex(pattern) }.getOrNull()
        }
        val chapters = ArrayList<SourceChapter>()
        val seen = HashSet<String>()

        collectChapterLinks(doc, tocUrl, regex, chapters, seen)
        val nextSelector = config.tocNextSelector.orEmpty()
        var nextUrl = nextSelector.takeIf { it.isNotBlank() }?.let { doc.selectFirst(it)?.absUrl("href") }
        val visitedToc = hashSetOf(tocUrl)
        while (!nextUrl.isNullOrBlank() && visitedToc.add(nextUrl) && chapters.size < 10000) {
            val nextDoc = Jsoup.parse(api.getText(nextUrl), nextUrl)
            collectChapterLinks(nextDoc, nextUrl, regex, chapters, seen)
            nextUrl = nextDoc.selectFirst(nextSelector)?.absUrl("href")
        }

        /*
         * Links are collected in a helper so paginated tables of contents use
         * exactly the same filtering and de-duplication rules as the first page.
         */

        if (chapters.isEmpty()) {
            val content = fetchContent(bookUrl)
            if (content.isBlank()) throw SourceException("没有找到《${book.title}》的章节")
            return@withContext TocResult(
                chapters = listOf(SourceChapter(0, book.title, bookUrl)),
                intro = book.intro,
                author = book.author,
                preloaded = mapOf(0 to content)
            )
        }

        val ordered = when (config.tocSort.orEmpty().lowercase()) {
            "urlnumeric" -> chapters.sortedBy { lastNumber(it.url) }
            "none" -> chapters
            else -> autoSort(chapters)
        }.mapIndexed { index, chapter -> chapter.copy(index = index) }
        TocResult(chapters = ordered, intro = book.intro, author = book.author)
    }

    override suspend fun chapter(book: SourceBook, chapter: SourceChapter): String =
        withContext(Dispatchers.IO) {
            val url = chapter.url.ifBlank { resolve(baseUrl, book.key) }
            val content = fetchContent(url)
            if (content.isBlank()) throw SourceException("本章暂无内容")
            content
        }

    override suspend fun search(query: String): List<SourceBook> {
        if (query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val remoteUrl = config.searchUrl.orEmpty()
            val listSelector = config.searchList.orEmpty()
            if (remoteUrl.isBlank() || listSelector.isBlank()) {
                return@withContext localSearch(query)
            }
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val rawUrl = remoteUrl
                .replace("{{key}}", encoded)
                .replace("{query}", encoded)
                .replace("{key}", encoded)
                .replace("{{page}}", "1")
                .replace("{page}", "1")
            val url = resolve(baseUrl, rawUrl)
            val html = if (config.searchMethod.orEmpty().lowercase() == "post") {
                val body = config.searchBody.orEmpty()
                    .replace("{{key}}", encoded)
                    .replace("{key}", encoded)
                    .replace("{{page}}", "1")
                    .replace("{page}", "1")
                api.postText(url, body)
            } else {
                api.getText(url)
            }
            val doc = Jsoup.parse(html, url)
            val items = doc.select(listSelector)
            if (items.isEmpty()) throw SourceException("没有解析到搜索结果，请检查 searchList 选择器")
            buildList {
                items.forEach { item ->
                    val title = extractText(item, config.searchTitle.orEmpty().ifBlank { "a" })
                        .ifBlank { item.text().trim().take(80) }
                    if (title.isBlank()) return@forEach
                    val linkSpec = config.searchLink.orEmpty().ifBlank { "a@href" }
                    val rawLink = extractAttr(item, linkSpec) ?: item.selectFirst("a")?.attr("href")
                    if (rawLink.isNullOrBlank()) return@forEach
                    val link = normalizeBookUrl(resolve(url, rawLink))
                    val author = extractText(item, config.searchAuthor.orEmpty())
                    val intro = extractText(item, config.searchIntro.orEmpty())
                    add(
                        SourceBook(
                            sourceId = id,
                            key = link,
                            title = title,
                            author = author.ifBlank { "佚名" },
                            intro = intro.ifBlank { "来自「$name」的搜索结果" },
                            category = "搜索结果",
                            coverSeed = title.hashCode()
                        )
                    )
                }
            }
        }
    }

    private suspend fun fetchContent(url: String): String {
        val html = api.getText(url)
        val doc = Jsoup.parse(html, url)
        val text = buildString {
            append(contentHtml(doc))
            val nextSelector = config.contentNextSelector.orEmpty()
            var nextUrl = nextSelector.takeIf { it.isNotBlank() }?.let { doc.selectFirst(it)?.absUrl("href") }
            val visited = hashSetOf(url)
            while (!nextUrl.isNullOrBlank() && visited.add(nextUrl) && visited.size < 100) {
                val nextDoc = Jsoup.parse(api.getText(nextUrl), nextUrl)
                // Apply the same cleanup rules to continuation pages.
                append(contentHtml(nextDoc))
                nextUrl = nextDoc.selectFirst(nextSelector)?.absUrl("href")
            }
        }
        val formatted = formatByLanguage(htmlToText(text))
        return formatted.trim()
    }

    private fun contentHtml(doc: org.jsoup.nodes.Document): String {
        val container = if (contentSelector.isBlank()) {
            doc.body()
        } else {
            doc.selectFirst(contentSelector) ?: doc.body()
        }
        if (contentRemove.isNotBlank()) {
            contentRemove.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { selector ->
                runCatching { container.select(selector).remove() }
            }
        }
        return container.outerHtml()
    }

    private fun localSearch(query: String): List<SourceBook> {
        val needle = query.trim()
        return explore.mapNotNull { item ->
            val title = item.title.orEmpty()
            val author = item.author.orEmpty()
            val intro = item.intro.orEmpty()
            if (!title.contains(needle, true) && !author.contains(needle, true) &&
                !intro.contains(needle, true)
            ) {
                return@mapNotNull null
            }
            val url = item.url.orEmpty()
            if (url.isBlank()) return@mapNotNull null
            SourceBook(
                sourceId = id,
                key = resolveFromBase(url),
                title = title,
                author = author.ifBlank { "佚名" },
                intro = intro,
                category = item.category.orEmpty().ifBlank { "精选" },
                coverSeed = title.hashCode()
            )
        }
    }

    private suspend fun loadExplore(): List<CustomCatalogItem> {
        val rule = config.exploreUrl.orEmpty().trim()
        val listSelector = config.exploreList.orEmpty()
        if (rule.isBlank() || listSelector.isBlank()) return emptyList()
        val entries = exploreEntries(rule).take(MAX_EXPLORE_CATEGORIES)
        if (entries.isEmpty()) return emptyList()

        val results = ArrayList<CustomCatalogItem>()
        val seenLinks = HashSet<String>()
        for ((rawCategory, rawPath) in entries) {
            val path = rawPath
                .replace("{{page}}", "1")
                .replace("{page}", "1")
            val url = resolve(baseUrl, path)
            // One slow category must not block the whole catalog.
            val doc = withTimeoutOrNull(10_000) {
                try {
                    Jsoup.parse(api.getText(url), url)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            } ?: continue
            val category = rawCategory.ifBlank { "精选" }
            for (item in doc.select(listSelector)) {
                val configuredTitle = config.exploreTitle.orEmpty()
                val title = if (configuredTitle.isNotBlank()) {
                    extractText(item, configuredTitle)
                } else {
                    ""
                }.ifBlank {
                    extractAttr(item, "img@alt")?.trim().orEmpty()
                }.ifBlank { extractText(item, "a") }
                val link = extractAttr(item, "a@href") ?: continue
                val normalized = normalizeBookUrl(resolve(url, link))
                if (!seenLinks.add(normalized)) continue
                results.add(
                    CustomCatalogItem(
                        title = title,
                        url = normalized,
                        category = category
                    )
                )
            }
        }
        return results
    }

    /**
     * Explore rules may contain several categories, either as a
     * "category::url" line list or as a JSON array of objects.
     */
    private fun exploreEntries(rule: String): List<Pair<String, String>> {
        if (rule.startsWith("[") || rule.startsWith("{")) {
            return runCatching {
                val root = com.google.gson.JsonParser.parseString(rule)
                val array = if (root.isJsonArray) root.asJsonArray else com.google.gson.JsonArray().apply { add(root) }
                array.mapNotNull { element ->
                    element.takeIf { value -> value.isJsonObject }?.asJsonObject?.let { item ->
                        val url = item.get("url")
                            ?.takeIf { it.isJsonPrimitive }
                            ?.asString
                            ?.trim()
                            .orEmpty()
                        if (url.isBlank()) {
                            null
                        } else {
                            item.get("title")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty() to url
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }
        return rule.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                if (line.contains("::")) {
                    line.substringBefore("::").trim() to line.substringAfter("::").trim()
                } else {
                    "" to line
                }
            }
            .toList()
    }

    private fun normalizeBookUrl(url: String): String {
        val match = QIDIAN_CHAPTER_ZERO.find(url) ?: return url
        return "https://m.qidian.com/book/${match.groupValues[1]}/"
    }

    private fun collectChapterLinks(
        doc: org.jsoup.nodes.Document,
        pageUrl: String,
        regex: Regex?,
        chapters: ArrayList<SourceChapter>,
        seen: MutableSet<String>
    ) {
        doc.select(tocLinkSelector).forEach { element ->
            val rawHref = element.attr("href").trim()
            if (rawHref.isBlank() || rawHref.startsWith("javascript:") || rawHref.startsWith("#")) return@forEach
            val url = element.absUrl("href").ifBlank { resolve(pageUrl, rawHref) }
            val path = runCatching { URI(url).path.orEmpty() }.getOrDefault("")
            if (regex != null && !regex.containsMatchIn(path) && !regex.containsMatchIn(rawHref)) return@forEach
            val title = element.text().trim().replace(Regex("\\s+"), " ").take(100)
            if (title.isBlank() || title in ignoredTitles || !seen.add(url)) return@forEach
            chapters.add(SourceChapter(chapters.size, title, url))
        }
    }

    private fun extractText(root: Element, spec: String): String {
        if (spec.isBlank()) return ""
        val (rule, suffix) = spec.split("##", limit = 2).let { it[0] to it.getOrNull(1) }
        val (selector, _) = splitSpec(rule)
        if (selector.isBlank()) return ""
        val text = root.selectFirst(selector)?.text()?.trim().orEmpty()
        return if (suffix.isNullOrBlank()) text else text.replace(suffix, "").trim()
    }

    private fun extractAttr(root: Element, spec: String): String? {
        if (spec.isBlank()) return null
        val (selector, attr) = splitSpec(spec)
        val element = if (selector.isBlank()) root else root.selectFirst(selector) ?: return null
        return if (attr.isNullOrBlank()) {
            element.absUrl("href").ifBlank { element.attr("href") }.ifBlank { null }
        } else {
            element.attr(attr).ifBlank { null }
        }
    }

    private fun splitSpec(spec: String): Pair<String, String?> {
        val at = spec.lastIndexOf('@')
        return if (at > 0) {
            spec.substring(0, at).trim() to spec.substring(at + 1).trim()
        } else {
            spec.trim() to null
        }
    }

    private fun resolve(base: String, reference: String): String {
        if (reference.isBlank()) return base
        if (reference.startsWith("http://") || reference.startsWith("https://")) return reference
        return runCatching { URI(base).resolve(reference).toString() }.getOrDefault(reference)
    }

    private fun resolveFromBase(reference: String): String {
        val safeBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return resolve(safeBase, reference)
    }

    private fun autoSort(chapters: List<SourceChapter>): List<SourceChapter> {
        val numbered = chapters.map { it to chapterNumber(it.title) }
        val count = numbered.count { it.second != null }
        if (count < chapters.size * 0.8f) return chapters
        return numbered.sortedBy { it.second ?: -1 }.map { it.first }
    }

    private fun chapterNumber(title: String): Int? {
        val match = chapterNumberRegex.find(title) ?: return null
        return chineseNumberToInt(match.groupValues[1])
    }

    private fun chineseNumberToInt(raw: String): Int? {
        val text = raw.trim().replace(" ", "").map {
            if (it in '０'..'９') '0' + (it - '０') else it
        }.joinToString("")
        if (text.isEmpty()) return null
        if (text.all { it.isDigit() }) return text.toIntOrNull()
        val digits = mapOf(
            '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '兩' to 2,
            '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
        )
        val units = mapOf('十' to 10, '百' to 100, '千' to 1000, '万' to 10000, '萬' to 10000)
        var total = 0
        var section = 0
        var number = 0
        for (char in text) {
            val digit = digits[char]
            if (digit != null) {
                number = digit
                continue
            }
            val unit = units[char] ?: return null
            if (unit == 10000) {
                total += (section + number) * unit
                section = 0
                number = 0
            } else {
                if (number == 0) number = 1
                section += number * unit
                number = 0
            }
        }
        return total + section + number
    }

    private fun lastNumber(url: String): Int =
        Regex("(\\d+)(?!.*\\d)").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE

    private companion object {
        const val MAX_EXPLORE_CATEGORIES = 12
        val QIDIAN_CHAPTER_ZERO = Regex("^https?://m\\.qidian\\.com/chapter/(\\d+)/0/?$")
        val chapterNumberRegex = Regex(
            "^(?:第\\s*|卷\\s*)([0-9０-９零〇一二三四五六七八九十百千万两兩]+)\\s*[回章卷节節篇]"
        )
        val ignoredTitles = setOf(
            "上一页", "下一页", "上一頁", "下一頁", "目录", "目錄", "首页", "首頁",
            "末页", "末頁", "返回", "上一章", "下一章"
        )
    }
}
