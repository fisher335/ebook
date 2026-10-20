package com.moyue.reader.util

private val namedEntities = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to " ",
    "hellip" to "…",
    "mdash" to "—",
    "ndash" to "–",
    "ldquo" to "“",
    "rdquo" to "”",
    "lsquo" to "‘",
    "rsquo" to "’",
    "middot" to "·",
    "bull" to "•",
    "zwsp" to ""
)

fun decodeEntities(input: String): String {
    if (input.indexOf('&') < 0) return input
    var result = Regex("&([a-zA-Z]+);").replace(input) { match ->
        namedEntities[match.groupValues[1].lowercase()] ?: match.value
    }
    result = Regex("&#(x?)([0-9a-fA-F]+);").replace(result) { match ->
        try {
            val code = if (match.groupValues[1].isNotEmpty()) {
                match.groupValues[2].toInt(16)
            } else {
                match.groupValues[2].toInt()
            }
            String(Character.toChars(code))
        } catch (_: Exception) {
            match.value
        }
    }
    return result
}

fun stripTags(input: String): String =
    decodeEntities(input.replace(Regex("<[^>]+>"), " ")).replace(Regex("\\s+"), " ").trim()

fun htmlToText(html: String): String {
    var text = html
    text = text.replace(Regex("(?is)<(script|style|head|nav)[^>]*>.*?</\\1>"), "")
    text = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
    text = text.replace(Regex("(?i)</(p|div|h[1-6]|li|tr|dd|dt|dl|blockquote|section|article)>"), "\n\n")
    text = text.replace(Regex("(?i)<(h[1-6])[^>]*>"), "\n\n")
    text = text.replace(Regex("(?s)<!--.*?-->"), "")
    text = text.replace(Regex("(?s)<[^>]+>"), "")
    text = decodeEntities(text)
    text = text.replace('\u00A0', ' ').replace("\r", "")
    text = text.replace(Regex("[ \\t]+\\n"), "\n")
    text = text.replace(Regex("\\n{3,}"), "\n\n")
    return text.trim()
}

private val navLine = Regex("^(回目录|回目錄|上一回|下一回|上一章|下一章|上一節|下一節|回目錄\\s*[　 ]*下一回|回目录\\s*[　 ]*下一回)$")
private val headingLine = Regex("^=+\\s*(.+?)\\s*=+$")

fun cleanExtract(raw: String): String {
    val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val output = ArrayList<String>(lines.size)
    for (line in lines) {
        var value = line.replace('\u00A0', ' ').trim()
        val heading = headingLine.find(value)
        if (heading != null) {
            value = heading.groupValues[1].trim()
        }
        value = value.replace(Regex("^\\[编辑]"), "").trim()
        if (value.isEmpty()) {
            if (output.isNotEmpty() && output.last().isNotEmpty()) output.add("")
            continue
        }
        if (navLine.matches(value)) continue
        if (value.startsWith("姊妹计划")) continue
        if (value.startsWith("回目录") && value.length <= 16) continue
        if (value.startsWith("回目錄") && value.length <= 16) continue
        output.add(value)
    }
    val builder = StringBuilder()
    var blank = false
    for (line in output) {
        if (line.isEmpty()) {
            if (!blank) {
                builder.append('\n')
                blank = true
            }
        } else {
            builder.append(line).append('\n')
            blank = false
        }
    }
    return builder.toString().trim()
}

fun formatForReading(cleaned: String): String {
    val paragraphs = cleaned.split(Regex("\\n+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return paragraphs.joinToString("\n\n") { paragraph ->
        if (paragraph.length > 45 && !paragraph.startsWith("　")) {
            "　　$paragraph"
        } else {
            paragraph
        }
    }
}

fun formatPlainText(text: String): String {
    val paragraphs = text.replace("\r\n", "\n").replace('\r', '\n')
        .split(Regex("\\n\\s*\\n"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return paragraphs.joinToString("\n\n")
}

fun formatByLanguage(text: String): String {
    val cjk = text.count { it in '\u4e00'..'\u9fff' }
    return if (cjk > 20 && cjk * 4 > text.length) {
        formatForReading(cleanExtract(text))
    } else {
        formatPlainText(text)
    }
}

fun splitPlainTextChapters(text: String): List<Pair<String, String>> {
    val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
    if (normalized.isEmpty()) return emptyList()

    val headingRegex = Regex(
        "(?m)^[ \\t　]*(第[0-9０-９零〇一二三四五六七八九十百千万两兩]+[章节節回卷集部篇][^\\n]{0,60}|" +
            "(?:Chapter|CHAPTER|Book|BOOK|Part|PART)\\s+[0-9IVXLCDMivxlcdm]+[^\\n]{0,80})\\s*$"
    )
    val matches = headingRegex.findAll(normalized).toList()
    if (matches.size < 2) {
        return chunkText(normalized)
    }

    val chapters = ArrayList<Pair<String, String>>()
    val firstStart = matches.first().range.first
    if (firstStart > 300) {
        val preface = normalized.substring(0, firstStart).trim()
        if (preface.length > 300) chapters.add("前言" to preface)
    }
    matches.forEachIndexed { index, match ->
        val start = match.range.last + 1
        val end = if (index + 1 < matches.size) matches[index + 1].range.first else normalized.length
        val title = match.value.trim().take(80).ifBlank { "第 ${index + 1} 章" }
        val content = normalized.substring(start, end).trim()
        if (content.isNotEmpty()) chapters.add(title to content)
    }
    return chapters.ifEmpty { chunkText(normalized) }
}

private fun chunkText(text: String): List<Pair<String, String>> {
    if (text.length < 9000) return listOf("正文" to text)
    val chapters = ArrayList<Pair<String, String>>()
    var cursor = 0
    var index = 1
    while (cursor < text.length) {
        var end = (cursor + 7000).coerceAtMost(text.length)
        if (end < text.length) {
            val breakAt = text.lastIndexOf('\n', end)
            if (breakAt > cursor + 3000) end = breakAt
        }
        val part = text.substring(cursor, end).trim()
        if (part.isNotEmpty()) chapters.add("第 $index 节" to part)
        cursor = end
        index++
    }
    return chapters
}
