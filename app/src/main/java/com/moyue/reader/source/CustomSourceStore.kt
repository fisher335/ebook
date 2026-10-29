package com.moyue.reader.source

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CustomSourceStore(context: Context) {
    private val prefs = context.getSharedPreferences("moyue_custom_sources", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _sources = MutableStateFlow(load())
    val sources: StateFlow<List<CustomSource>> = _sources.asStateFlow()

    @Synchronized
    fun add(json: String): CustomSource {
        return importJson(json).firstOrNull() ?: throw SourceException("没有可导入的书源")
    }

    @Synchronized
    fun importJson(json: String, replaceExisting: Boolean = false): List<CustomSource> {
        val element = try {
            JsonParser.parseString(json)
        } catch (error: Exception) {
            throw SourceException("JSON 格式有误：${error.message ?: "请检查括号与引号"}", error)
        }
        val objects = when {
            element.isJsonObject -> listOf(element.asJsonObject)
            element.isJsonArray -> element.asJsonArray.mapNotNull { it.takeIf { value -> value.isJsonObject }?.asJsonObject }
            else -> emptyList()
        }
        if (objects.isEmpty()) throw SourceException("书源 JSON 为空或格式不支持")

        val imported = ArrayList<CustomSource>()
        var current = _sources.value
        var lastError: Exception? = null
        objects.forEach { objectValue ->
            try {
                val raw = gson.fromJson(
                    if (objectValue.has("bookSourceName")) adaptReaderSource(objectValue) else objectValue,
                    CustomSource::class.java
                ) ?: throw SourceException("书源 JSON 为空")
                val normalized = normalize(raw)
                val index = current.indexOfFirst {
                    it.id == normalized.id ||
                        sameName(it, normalized) ||
                        sameSite(it, normalized)
                }
                if (index < 0) {
                    current = current + normalized
                    imported += normalized
                } else if (replaceExisting) {
                    val replacement = normalized.copy(id = current[index].id)
                    current = current.toMutableList().also { it[index] = replacement }
                    imported += replacement
                }
            } catch (error: Exception) {
                lastError = error
            }
        }
        if (imported.isEmpty()) {
            val error = lastError
            throw if (error is SourceException) error
            else SourceException(error?.message ?: "没有可导入的书源", error)
        }
        save(current)
        _sources.value = current
        return imported
    }

    @Synchronized
    fun remove(id: String) {
        val updated = _sources.value.filterNot { it.id == id }
        if (updated.size == _sources.value.size) return
        save(updated)
        _sources.value = updated
    }

    private fun normalize(raw: CustomSource): CustomSource {
        val name = raw.name?.trim().orEmpty()
        if (name.isBlank()) throw SourceException("缺少 name（书源名称）")
        val baseUrl = raw.baseUrl?.trim().orEmpty()
        if (!baseUrl.startsWith("https://")) {
            // The app ships with usesCleartextTraffic=false, so http sources
            // would fail at request time. Reject them up front with a clear message.
            throw SourceException("baseUrl 必须是 https 网址（应用已禁用明文 HTTP）")
        }
        val contentSelector = raw.contentSelector?.trim().orEmpty()
        if (contentSelector.isBlank()) {
            throw SourceException("缺少 contentSelector（正文选择器）")
        }
        val explore = raw.explore.orEmpty()
            .filter { !it.title.isNullOrBlank() && !it.url.isNullOrBlank() }
            .map {
                it.copy(
                    title = it.title?.trim(),
                    author = it.author?.trim().orEmpty(),
                    intro = it.intro?.trim().orEmpty(),
                    category = it.category?.trim()?.ifBlank { "精选" } ?: "精选",
                    url = it.url?.trim()
                )
            }
        val remoteSearch = !raw.searchUrl.isNullOrBlank() && !raw.searchList.isNullOrBlank()
        if (!remoteSearch && explore.isEmpty() && raw.exploreUrl.isNullOrBlank()) {
            throw SourceException("至少需要提供 explore 书单，或同时提供 searchUrl 与 searchList")
        }

        val rawId = raw.id?.trim()?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }
        val id = when {
            rawId == null -> "custom_${sourceKey(name, baseUrl).hashCode().toUInt().toString(16)}"
            rawId.startsWith("custom_") -> rawId
            else -> "custom_$rawId"
        }

        val tocSort = raw.tocSort?.trim()?.lowercase()
            ?.takeIf { it == "auto" || it == "none" || it == "urlnumeric" }
            ?: "auto"

        return CustomSource(
            id = id,
            name = name,
            tagline = raw.tagline?.trim().orEmpty().ifBlank { "自定义书源 · 由你添加" },
            baseUrl = baseUrl,
            searchable = remoteSearch || explore.isNotEmpty() || !raw.exploreUrl.isNullOrBlank(),
            explore = explore,
            exploreUrl = raw.exploreUrl?.trim()?.ifBlank { null },
            exploreList = raw.exploreList?.trim()?.ifBlank { null },
            exploreTitle = raw.exploreTitle?.trim()?.ifBlank { null },
            searchUrl = raw.searchUrl?.trim()?.ifBlank { null },
            searchMethod = raw.searchMethod?.trim()?.lowercase()?.ifBlank { "get" } ?: "get",
            searchBody = raw.searchBody?.trim()?.ifBlank { null },
            searchList = raw.searchList?.trim()?.ifBlank { null },
            searchTitle = raw.searchTitle?.trim()?.ifBlank { "a" } ?: "a",
            searchAuthor = raw.searchAuthor?.trim()?.ifBlank { null },
            searchIntro = raw.searchIntro?.trim()?.ifBlank { null },
            searchLink = raw.searchLink?.trim()?.ifBlank { "a@href" } ?: "a@href",
            tocUrl = raw.tocUrl?.trim()?.ifBlank { "{bookUrl}" } ?: "{bookUrl}",
            tocLinkSelector = raw.tocLinkSelector?.trim()?.ifBlank { "a[href]" } ?: "a[href]",
            tocLinkRegex = raw.tocLinkRegex?.trim()?.ifBlank { null },
            tocNextSelector = raw.tocNextSelector?.trim()?.ifBlank { null },
            tocSort = tocSort,
            contentSelector = contentSelector,
            contentRemove = raw.contentRemove?.trim().orEmpty(),
            contentNextSelector = raw.contentNextSelector?.trim()?.ifBlank { null },
            builtIn = false
        )
    }

    private fun adaptReaderSource(source: JsonObject): JsonObject {
        val sourceUrl = source.string("bookSourceUrl").orEmpty()
        if (sourceUrl.contains("qidian.com")) return adaptQidian(source)
        val exploreRule = source.string("exploreUrl").orEmpty()
        val searchRule = source.getAsJsonObject("ruleSearch") ?: JsonObject()
        val tocRule = source.getAsJsonObject("ruleToc") ?: JsonObject()
        val contentRule = source.getAsJsonObject("ruleContent") ?: JsonObject()
        val exploreRuleObject = source.getAsJsonObject("ruleExplore") ?: JsonObject()
        val rawSearch = searchRule.string("searchUrl").orEmpty()
            .ifBlank { source.string("searchUrl").orEmpty() }
        val result = JsonObject()
        result.addProperty("name", source.string("bookSourceName").orEmpty())
        result.addProperty("tagline", "兼容阅读器书源 · ${source.string("bookSourceUrl").orEmpty()}")
        result.addProperty("baseUrl", source.string("bookSourceUrl").orEmpty().trimEnd('/'))
        result.addProperty("searchUrl", readerUrl(rawSearch))
        result.addProperty("searchMethod", if (isPostSearch(rawSearch)) "post" else "get")
        result.addProperty("searchBody", readerBody(rawSearch))
        result.addProperty("searchList", cssRule(searchRule.string("bookList")))
        result.addProperty("searchTitle", cssRule(searchRule.string("name")))
        result.addProperty("searchAuthor", "${cssRule(searchRule.string("author"))}##作者：")
        result.addProperty("searchIntro", cssRule(searchRule.string("intro")))
        result.addProperty("searchLink", attrRule(searchRule.string("bookUrl"), "href"))
        result.addProperty("tocUrl", "{bookUrl}")
        result.addProperty("tocLinkSelector", cssRule(tocRule.string("chapterList")))
        result.addProperty("tocNextSelector", nextSelector(tocRule.string("nextTocUrl")))
        result.addProperty("contentSelector", cssRule(contentRule.string("content")).ifBlank { "body" })
        result.addProperty("contentNextSelector", nextSelector(contentRule.string("nextContentUrl")))
        result.addProperty("contentRemove", "script,style")
        result.addProperty("tocSort", "auto")
        if (exploreRule.isNotBlank()) {
            result.addProperty("exploreUrl", exploreRule)
            result.addProperty("exploreList", cssRule(exploreRuleObject.string("bookList")))
        }
        return result
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.ifBlank { null }

    private fun adaptQidian(source: JsonObject): JsonObject = JsonObject().apply {
        addProperty("name", source.string("bookSourceName").orEmpty())
        addProperty("tagline", "起点中文网移动版")
        addProperty("baseUrl", "https://m.qidian.com")
        addProperty("searchUrl", "/search?kw={{key}}")
        addProperty("searchMethod", "get")
        addProperty("searchList", "[class*='_bookWrapper_']")
        addProperty("searchTitle", "[class*='_searchBookName']")
        addProperty("searchAuthor", "[class*='_searchBookAuthor']")
        addProperty("searchIntro", "[class*='_searchBookDesc']")
        addProperty("searchLink", "a@href")
        addProperty("tocUrl", "{bookUrl}catalog/")
        addProperty("tocLinkSelector", "a[href*='/chapter/']")
        addProperty("tocSort", "auto")
        addProperty("contentSelector", ".content")
        addProperty("contentRemove", "script,style")
        addProperty(
            "exploreUrl",
            "月票榜::/rank/yuepiao/\n" +
                "畅销榜::/rank/hotsales/\n" +
                "阅读指数榜::/rank/readindex/\n" +
                "粉丝榜::/rank/newfans/\n" +
                "推荐榜::/rank/rec/\n" +
                "更新榜::/rank/update/"
        )
        addProperty("exploreList", "[class*='_bookItem_']")
        addProperty("exploreTitle", "[class*='_title_']")
    }

    private fun sameName(a: CustomSource, b: CustomSource): Boolean =
        !a.name.isNullOrBlank() && a.name.trim().equals(b.name?.trim(), ignoreCase = true)

    private fun sameSite(a: CustomSource, b: CustomSource): Boolean =
        !a.baseUrl.isNullOrBlank() && a.baseUrl.trim().trimEnd('/')
            .equals(b.baseUrl?.trim()?.trimEnd('/'), ignoreCase = true)

    private fun readerUrl(raw: String): String = raw.substringBefore(',').trim()

    private fun readerBody(raw: String): String? = Regex("['\"]body['\"]\\s*:\\s*['\"]([^'\"]*)['\"]")
        .find(raw)?.groupValues?.get(1)

    private fun isPostSearch(raw: String): Boolean =
        Regex("['\"]?method['\"]?\\s*[:=]\\s*['\"]?post", RegexOption.IGNORE_CASE).containsMatchIn(raw)

    private fun cssRule(raw: String?): String {
        val text = raw.orEmpty().substringBefore("##").trim()
        if (text.isBlank()) return ""
        if (text.startsWith("@css:")) return cssRule(text.removePrefix("@css:"))
        if (text.startsWith("//") || text.startsWith("/")) return text
        val selectorParts = ArrayList<String>()
        for (segment in text.split("@").map { it.trim() }.filter { it.isNotEmpty() }) {
            if (segment in attributeMarkers) break
            val tokens = segment.split('.')
            when {
                tokens[0] == "class" && tokens.size > 1 -> selectorParts += ".${tokens[1]}"
                tokens[0] == "id" && tokens.size > 1 -> selectorParts += "#${tokens[1]}"
                tokens[0] == "tag" && tokens.size > 1 -> selectorParts += tokens[1]
                segment.all { it.isDigit() } -> Unit
                else -> selectorParts += segment
            }
        }
        return selectorParts.joinToString(" ")
    }

    private fun attrRule(raw: String?, defaultAttr: String): String {
        val segments = raw.orEmpty().substringBefore("##").split("@").map { it.trim() }
        val attr = segments.lastOrNull()?.takeIf { it in attributeMarkers } ?: defaultAttr
        return "${cssRule(raw)}@$attr"
    }

    private fun sourceKey(name: String?, baseUrl: String?): String =
        "${name.orEmpty().trim().lowercase()}|${baseUrl.orEmpty().trim().trimEnd('/').lowercase()}"

    private fun nextSelector(raw: String?): String {
        val value = raw.orEmpty().trim()
        if (value.isBlank()) return ""
        if (value.contains("@css:")) {
            return value.substringAfter("@css:").substringBeforeLast("@").trim()
        }
        if (value.contains("prenext")) return "div.prenext span a:matchesOwn(下一页)"
        return cssRule(value.substringBeforeLast("@"))
    }

    private fun load(): List<CustomSource> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val type = com.google.gson.reflect.TypeToken.getParameterized(
                List::class.java,
                CustomSource::class.java
            ).type
            gson.fromJson<List<CustomSource>>(json, type).orEmpty()
                .filter { !it.id.isNullOrBlank() && !it.name.isNullOrBlank() }
        }.getOrDefault(emptyList())
    }

    private fun save(sources: List<CustomSource>) {
        prefs.edit().putString(KEY, gson.toJson(sources)).apply()
    }

    private companion object {
        const val KEY = "sources_json"
        val attributeMarkers = setOf(
            "text", "textNodes", "html", "ownText", "src", "href", "alt", "title", "value", "content"
        )
    }
}
