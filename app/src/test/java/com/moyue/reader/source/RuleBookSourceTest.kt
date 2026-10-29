package com.moyue.reader.source

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuleBookSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var source: RuleBookSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        val config = CustomSource(
            id = "custom_test",
            name = "测试书源",
            tagline = "本地测试",
            baseUrl = base,
            explore = listOf(
                CustomCatalogItem(
                    title = "测试书",
                    author = "作者",
                    intro = "简介",
                    category = "精选",
                    url = "/book/1/"
                )
            ),
            searchUrl = "/search?q={query}",
            searchList = ".book-item",
            searchTitle = "h3",
            searchAuthor = ".author",
            searchIntro = ".intro",
            searchLink = "a@href",
            tocUrl = "{bookUrl}",
            tocLinkSelector = "a[href]",
            tocLinkRegex = "^{bookPath}\\d+\\.html$",
            tocSort = "auto",
            contentSelector = ".chapter-content",
            contentRemove = ".ads,script,style"
        )
        source = RuleBookSource(config, ApiClient(OkHttpClient()))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun catalogUsesExploreListAndAbsoluteUrls() = runBlocking {
        val catalog = source.catalog()
        assertEquals(1, catalog.size)
        assertEquals("测试书", catalog[0].title)
        assertTrue(catalog[0].key.startsWith("http"))
    }

    @Test
    fun tocFiltersAndSortsChapters() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                <html><body>
                  <a href="/help/">帮助</a>
                  <a href="/book/1/002.html">第二回 二</a>
                  <a href="/book/1/001.html">第一回 一</a>
                  <a href="/book/1/003.html">第三回 三</a>
                </body></html>
                """.trimIndent()
            )
        )
        val book = source.catalog().first()
        val toc = source.toc(book)
        assertEquals(3, toc.chapters.size)
        assertEquals("第一回 一", toc.chapters[0].title)
        assertEquals("第二回 二", toc.chapters[1].title)
        assertEquals("第三回 三", toc.chapters[2].title)
    }

    @Test
    fun catalogParsesJsonExploreRuleAndQidianSearchLinks() = runBlocking {
        val config = CustomSource(
            id = "custom_json",
            name = "JSON 发现规则",
            baseUrl = server.url("/").toString().trimEnd('/'),
            exploreUrl = """[{"title":"月票榜","url":"/rank/"}]""",
            exploreList = ".item",
            exploreTitle = "h2",
            contentSelector = ".content"
        )
        val jsonSource = RuleBookSource(config, ApiClient(OkHttpClient()))
        server.enqueue(
            MockResponse().setBody(
                """
                <html><body>
                  <div class="item">
                    <h2>测试小说</h2>
                    <a href="https://m.qidian.com/chapter/123/0/">阅读</a>
                  </div>
                </body></html>
                """.trimIndent()
            )
        )
        val catalog = jsonSource.catalog()
        assertEquals(1, catalog.size)
        assertEquals("测试小说", catalog[0].title)
        assertEquals("月票榜", catalog[0].category)
        assertEquals("https://m.qidian.com/book/123/", catalog[0].key)
    }

    @Test
    fun chapterRemovesUnwantedNodes() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                <html><body>
                  <div class="chapter-content">
                    <p>第一段正文</p>
                    <div class="ads">这是广告</div>
                    <p>第二段正文</p>
                  </div>
                </body></html>
                """.trimIndent()
            )
        )
        val content = source.chapter(
            source.catalog().first(),
            SourceChapter(0, "第一章", server.url("/book/1/001.html").toString())
        )
        assertTrue(content.contains("第一段正文"))
        assertTrue(content.contains("第二段正文"))
        assertFalse(content.contains("广告"))
    }

    @Test
    fun catalogLoadsEveryExploreCategory() = runBlocking {
        val config = CustomSource(
            id = "custom_multi",
            name = "多分类书源",
            baseUrl = server.url("/").toString().trimEnd('/'),
            exploreUrl = "第一类::/a/\n第二类::/b/",
            exploreList = ".item",
            exploreTitle = "h2",
            contentSelector = ".content"
        )
        val multiSource = RuleBookSource(config, ApiClient(OkHttpClient()))
        server.enqueue(
            MockResponse().setBody(
                """<div class="item"><h2>A</h2><a href="/book/a/">阅读</a></div>"""
            )
        )
        server.enqueue(
            MockResponse().setBody(
                """<div class="item"><h2>B</h2><a href="/book/b/">阅读</a></div>"""
            )
        )

        val catalog = multiSource.catalog()
        assertEquals(2, catalog.size)
        assertEquals(setOf("第一类", "第二类"), catalog.map { it.category }.toSet())
    }

    @Test
    fun continuationPagesAlsoRemoveUnwantedNodes() = runBlocking {
        val config = CustomSource(
            id = "custom_paged",
            name = "分页书源",
            baseUrl = server.url("/").toString().trimEnd('/'),
            explore = listOf(CustomCatalogItem(title = "书", url = "/book/1/")),
            contentSelector = ".chapter-content",
            contentRemove = ".ads",
            contentNextSelector = "a.next"
        )
        val pagedSource = RuleBookSource(config, ApiClient(OkHttpClient()))
        server.enqueue(
            MockResponse().setBody(
                """
                <html><body>
                  <div class="chapter-content"><p>第一页正文</p><div class="ads">广告一</div></div>
                  <a class="next" href="/book/1/2.html">下一页</a>
                </body></html>
                """.trimIndent()
            )
        )
        server.enqueue(
            MockResponse().setBody(
                """
                <html><body>
                  <div class="chapter-content"><p>第二页正文</p><div class="ads">广告二</div></div>
                </body></html>
                """.trimIndent()
            )
        )

        val content = pagedSource.chapter(
            pagedSource.catalog().first(),
            SourceChapter(0, "第一章", server.url("/book/1/1.html").toString())
        )
        assertTrue(content.contains("第一页正文"))
        assertTrue(content.contains("第二页正文"))
        assertFalse(content.contains("广告"))
    }

    @Test
    fun searchParsesRemoteList() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                <html><body>
                  <div class="book-item">
                    <h3>搜索结果</h3>
                    <span class="author">某作者</span>
                    <p class="intro">一段简介</p>
                    <a href="/book/2/">详情</a>
                  </div>
                </body></html>
                """.trimIndent()
            )
        )
        val results = source.search("测试")
        assertEquals(1, results.size)
        assertEquals("搜索结果", results[0].title)
        assertEquals("某作者", results[0].author)
        assertEquals("一段简介", results[0].intro)
        assertTrue(results[0].key.startsWith("http"))
    }
}
