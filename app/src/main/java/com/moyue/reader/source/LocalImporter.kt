package com.moyue.reader.source

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.moyue.reader.util.formatByLanguage
import com.moyue.reader.util.formatPlainText
import com.moyue.reader.util.htmlToText
import com.moyue.reader.util.splitPlainTextChapters
import com.moyue.reader.util.stripTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.UUID
import java.util.zip.ZipFile

data class ImportedBook(
    val title: String,
    val author: String,
    val intro: String,
    val chapters: List<Pair<String, String>>,
    val localPath: String,
    val sourceKey: String,
    val coverSeed: Int
)

private const val MAX_TEXT_FILE_BYTES = 32L * 1024 * 1024

class LocalImporter(private val context: Context) {

    suspend fun import(uri: Uri): ImportedBook = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: "未命名书籍"
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val isEpub = extension == "epub" ||
            context.contentResolver.getType(uri) == "application/epub+zip"
        val isTxt = extension == "txt" || context.contentResolver.getType(uri) == "text/plain"
        if (!isEpub && !isTxt) {
            throw SourceException("暂时只支持导入 TXT 与 EPUB 文件")
        }

        val dir = File(context.filesDir, "imported").apply { mkdirs() }
        val target = File(dir, "${UUID.randomUUID()}.${if (isEpub) "epub" else "txt"}")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw SourceException("无法读取所选文件")

        try {
            if (isEpub) {
                parseEpub(target, displayName.substringBeforeLast('.'))
            } else {
                parseTxt(target, displayName.substringBeforeLast('.'))
            }
        } catch (error: Throwable) {
            // Do not leave a half-imported file behind when parsing fails.
            target.delete()
            throw error
        }
    }

    fun deleteImported(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val root = File(context.filesDir, "imported").canonicalFile
            val target = File(path).canonicalFile
            if (target.parentFile == root) target.delete()
        }
    }

    private fun parseTxt(file: File, fallbackTitle: String): ImportedBook {
        if (file.length() > MAX_TEXT_FILE_BYTES) {
            throw SourceException("TXT 文件超过 32 MB，请拆分后再导入")
        }
        val bytes = file.readBytes()
        if (bytes.isEmpty()) throw SourceException("文件内容为空")
        val text = decodeText(bytes)
        val rawChapters = splitPlainTextChapters(text)
        if (rawChapters.isEmpty()) throw SourceException("TXT 中没有找到可阅读的文本")
        val chapters = rawChapters.map { (title, content) ->
            title to formatByLanguage(content)
        }.filter { it.second.isNotBlank() }
        if (chapters.isEmpty()) throw SourceException("TXT 中没有找到可阅读的文本")
        return ImportedBook(
            title = fallbackTitle.ifBlank { "本地书籍" },
            author = "本地导入",
            intro = "TXT · 共 ${chapters.size} 章 · 已保存到本机",
            chapters = chapters,
            localPath = file.absolutePath,
            sourceKey = file.name,
            coverSeed = fallbackTitle.hashCode()
        )
    }

    private fun parseEpub(file: File, fallbackTitle: String): ImportedBook {
        ZipFile(file).use { zip ->
            val containerEntry = zip.getEntry("META-INF/container.xml")
                ?: throw SourceException("不是有效的 EPUB 文件")
            val containerXml = zip.getInputStream(containerEntry).readBytes().toString(Charsets.UTF_8)
            val opfPath = Regex("""full-path\s*=\s*["']([^"']+)["']""")
                .find(containerXml)?.groupValues?.get(1)
                ?: throw SourceException("EPUB 缺少 OPF 文件")
            val opfEntry = zip.getEntry(opfPath)
                ?: throw SourceException("EPUB 缺少 OPF 文件")
            val opf = zip.getInputStream(opfEntry).readBytes().toString(Charsets.UTF_8)

            val title = Regex("""<dc:title[^>]*>(.*?)</dc:title>""", setOf(
                RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL
            )).find(opf)?.groupValues?.get(1)?.let(::stripTags)
                ?.ifBlank { null } ?: fallbackTitle.ifBlank { "本地书籍" }
            val author = Regex("""<dc:creator[^>]*>(.*?)</dc:creator>""", setOf(
                RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL
            )).find(opf)?.groupValues?.get(1)?.let(::stripTags)
                ?.ifBlank { "本地导入" } ?: "本地导入"

            val baseDir = opfPath.substringBeforeLast('/', "")
            val manifest = mutableMapOf<String, String>()
            val mediaTypes = mutableMapOf<String, String>()
            val properties = mutableMapOf<String, String>()
            Regex("""<item\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(opf).forEach { match ->
                val attrs = parseAttributes(match.value)
                val itemId = attrs["id"] ?: return@forEach
                val href = attrs["href"] ?: return@forEach
                val path = resolveZipPath(baseDir, href)
                manifest[itemId] = path
                mediaTypes[path] = attrs["media-type"].orEmpty()
                properties[path] = attrs["properties"].orEmpty()
            }

            val spine = Regex("""<itemref\b[^>]*>""", RegexOption.IGNORE_CASE)
                .findAll(opf)
                .mapNotNull { parseAttributes(it.value)["idref"] }
                .toList()
            val orderedPaths = spine.mapNotNull { manifest[it] }
                .ifEmpty {
                    manifest.values.filter {
                        it.endsWith(".xhtml", true) || it.endsWith(".html", true) || it.endsWith(".htm", true)
                    }
                }
                .distinct()

            val chapters = ArrayList<Pair<String, String>>()
            orderedPaths.forEach { path ->
                if (properties[path]?.contains("nav") == true) return@forEach
                val entry = zip.getEntry(path) ?: return@forEach
                val bytes = zip.getInputStream(entry).readBytes()
                val html = decodeText(bytes)
                val text = htmlToText(html)
                if (text.length < 80) return@forEach
                val heading = Regex(
                    """<h[1-3][^>]*>(.*?)</h[1-3]>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                ).find(html)?.groupValues?.get(1)?.let(::stripTags)
                val docTitle = Regex(
                    """<title[^>]*>(.*?)</title>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                ).find(html)?.groupValues?.get(1)?.let(::stripTags)
                val chapterTitle = heading?.takeIf { it.isNotBlank() }
                    ?: docTitle?.takeIf { it.isNotBlank() }
                    ?: path.substringAfterLast('/').substringBeforeLast('.')
                chapters.add(chapterTitle.take(80) to formatPlainText(text))
            }

            if (chapters.isEmpty()) throw SourceException("EPUB 中没有找到可阅读的文本")
            return ImportedBook(
                title = title,
                author = author,
                intro = "EPUB · 共 ${chapters.size} 章 · 已保存到本机",
                chapters = chapters,
                localPath = file.absolutePath,
                sourceKey = file.name,
                coverSeed = title.hashCode()
            )
        }
    }

    private fun parseAttributes(tag: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        Regex("""([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""").findAll(tag).forEach { match ->
            val name = match.groupValues[1].lowercase()
            val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
            result[name] = value
        }
        return result
    }

    private fun resolveZipPath(baseDir: String, href: String): String {
        val clean = href.substringBefore('#')
        val joined = if (clean.startsWith("/")) clean.drop(1) else "$baseDir/$clean"
        val stack = ArrayDeque<String>()
        joined.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(segment)
            }
        }
        return stack.joinToString("/")
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return null
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        val declaration = String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1)
        val charset = Regex("""encoding\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(declaration)?.groupValues?.get(1)
            ?.let { runCatching { Charset.forName(it) }.getOrNull() }
            ?: Charsets.UTF_8
        if (charset == Charsets.UTF_8) {
            try {
                val decoder = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                return decoder.decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: CharacterCodingException) {
                return String(bytes, Charset.forName("GB18030"))
            }
        }
        return String(bytes, charset)
    }
}
