package com.moyue.reader.source

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiClient(private val client: OkHttpClient) {

    suspend fun getText(url: String, retries: Int = 3): String {
        var lastError: Exception? = null
        repeat(retries) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .build()
                return execute(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SourceException) {
                lastError = e
                if (e.httpCode != 0 && e.httpCode != 429 && e.httpCode < 500) throw e
                delay(1200L * (attempt + 1))
            } catch (e: Exception) {
                lastError = e
                delay(800L * (attempt + 1))
            }
        }
        throw SourceException("网络开小差了，请稍后重试", lastError)
    }

    suspend fun postText(url: String, body: String, retries: Int = 3): String {
        var lastError: Exception? = null
        repeat(retries) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                    .build()
                return execute(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SourceException) {
                lastError = e
                if (e.httpCode != 0 && e.httpCode != 429 && e.httpCode < 500) throw e
                delay(1200L * (attempt + 1))
            } catch (e: Exception) {
                lastError = e
                delay(800L * (attempt + 1))
            }
        }
        throw SourceException("网络开小差了，请稍后重试", lastError)
    }

    suspend fun getJson(url: String, retries: Int = 3): JsonObject {
        val text = getText(url, retries)
        return try {
            JsonParser.parseString(text).asJsonObject
        } catch (e: Exception) {
            throw SourceException("数据解析失败", e)
        }
    }

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!continuation.isActive) return
                    try {
                        if (!it.isSuccessful) {
                            throw SourceException("网络请求失败（${it.code}）", null, it.code)
                        }
                        continuation.resume(it.readText())
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            }
        })
    }

    private fun Response.readText(): String {
        val body = body ?: return ""
        val length = body.contentLength()
        if (length > MAX_RESPONSE_BYTES) {
            throw SourceException("响应内容过大（${length / 1024} KB）", null, code)
        }
        val bytes = body.bytes()
        if (bytes.size > MAX_RESPONSE_BYTES) {
            throw SourceException("响应内容过大（${bytes.size / 1024} KB）", null, code)
        }
        // Do not fall back to UTF-8 here: an absent charset should let the
        // meta tag / strict-decoding fallback decide.
        return decodeBody(bytes, body.contentType()?.charset(null))
    }

    /**
     * Many Chinese book sites omit the charset header or declare UTF-8 while
     * actually serving GBK. Prefer an explicit charset, then a meta tag, then
     * strict UTF-8 with a GB18030 fallback.
     */
    private fun decodeBody(bytes: ByteArray, declared: Charset?): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        val charset = declared ?: metaCharset(bytes) ?: Charsets.UTF_8
        if (charset != Charsets.UTF_8) return String(bytes, charset)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("GB18030"))
        }
    }

    private fun metaCharset(bytes: ByteArray): Charset? {
        val head = String(bytes, 0, minOf(bytes.size, 1024), Charsets.ISO_8859_1)
        val name = META_CHARSET.find(head)?.groupValues?.get(1) ?: return null
        return runCatching { Charset.forName(name) }.getOrNull()
    }

    companion object {
        const val USER_AGENT = "MoyueReader/1.0 (Android; public-domain ebook reader)"
        private const val MAX_RESPONSE_BYTES = 16 * 1024 * 1024
        private val META_CHARSET = Regex(
            "charset\\s*=\\s*[\"']?([A-Za-z0-9_\\-]+)",
            RegexOption.IGNORE_CASE
        )
    }
}

fun HttpUrl.Builder.query(vararg pairs: Pair<String, String>): HttpUrl.Builder {
    pairs.forEach { addQueryParameter(it.first, it.second) }
    return this
}
