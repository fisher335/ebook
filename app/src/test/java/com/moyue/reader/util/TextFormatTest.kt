package com.moyue.reader.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFormatTest {

    @Test
    fun decodeEntitiesHandlesNamedAndNumericReferences() {
        assertEquals("&< 你好", decodeEntities("&amp;&lt;&nbsp;&#20320;&#x597D;"))
        assertEquals("&unknown;", decodeEntities("&unknown;"))
    }

    @Test
    fun htmlToTextDropsScriptsAndKeepsParagraphs() {
        val html = """
            <html><head><style>p{color:red}</style></head>
            <body><p>第一段</p><p>第二段</p><script>alert(1)</script></body></html>
        """.trimIndent()
        assertEquals("第一段\n\n第二段", htmlToText(html))
    }

    @Test
    fun splitPlainTextChaptersSplitsOnHeadings() {
        val text = "第一章 开始\n内容一\n第二章 继续\n内容二"
        val chapters = splitPlainTextChapters(text)
        assertEquals(2, chapters.size)
        assertEquals("第一章 开始", chapters[0].first)
        assertEquals("内容一", chapters[0].second)
        assertEquals("第二章 继续", chapters[1].first)
        assertEquals("内容二", chapters[1].second)
    }

    @Test
    fun splitPlainTextChaptersBreaksLongTextWithoutHeadings() {
        val chapters = splitPlainTextChapters("字".repeat(12_000))
        assertEquals(2, chapters.size)
        assertEquals("第 1 节", chapters[0].first)
        assertEquals("第 2 节", chapters[1].first)
        assertTrue(chapters.all { it.second.length <= 7_000 })
    }

    @Test
    fun formatByLanguageIndentsLongChineseParagraphs() {
        val paragraph = "春眠不觉晓处处闻啼鸟夜来风雨声花落知多少".repeat(3)
        val formatted = formatByLanguage(paragraph)
        assertTrue(formatted.startsWith("　　"))
    }

    @Test
    fun cleanExtractRemovesNavigationLines() {
        assertEquals("正文\n继续", cleanExtract("正文\n上一章\n下一章\n继续"))
    }
}
