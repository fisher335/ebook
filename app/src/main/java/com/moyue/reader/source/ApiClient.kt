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
                    if (!it.isSuccessful) {
                        continuation.resumeWithException(
                            SourceException("网络请求失败（${it.code}）", null, it.code)
                        )
                    } else {
                        continuation.resume(it.body?.string().orEmpty())
                    }
                }
            }
        })
    }

    companion object {
        const val USER_AGENT = "MoyueReader/1.0 (Android; public-domain ebook reader)"
    }
}

fun HttpUrl.Builder.query(vararg pairs: Pair<String, String>): HttpUrl.Builder {
    pairs.forEach { addQueryParameter(it.first, it.second) }
    return this
}
