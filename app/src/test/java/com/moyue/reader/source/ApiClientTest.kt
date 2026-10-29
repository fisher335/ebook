package com.moyue.reader.source

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.nio.charset.Charset

class ApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = ApiClient(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun decodesUtf8WhenServerOmitsCharset() = runBlocking {
        val body = "中文正文"
        server.enqueue(MockResponse().setBody(body))
        assertEquals(body, api.getText(server.url("/utf8").toString()))
    }

    @Test
    fun fallsBackToGb18030ForLegacyServers() = runBlocking {
        val body = "旧版编码的内容"
        val gbk = body.toByteArray(Charset.forName("GB18030"))
        server.enqueue(MockResponse().setBody(Buffer().write(gbk)))
        assertEquals(body, api.getText(server.url("/gbk").toString()))
    }
}
