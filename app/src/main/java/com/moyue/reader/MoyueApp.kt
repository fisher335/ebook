package com.moyue.reader

import android.app.Application
import android.content.Context
import com.moyue.reader.data.AppDatabase
import com.moyue.reader.data.BookRepository
import com.moyue.reader.data.SettingsRepository
import com.moyue.reader.source.ApiClient
import com.moyue.reader.source.BookSource
import com.moyue.reader.source.CustomSourceStore
import com.moyue.reader.source.LocalImporter
import com.moyue.reader.source.SourceRegistry
import com.moyue.reader.source.WikisourceSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private const val DEFAULT_SOURCE_URL = "https://legado.aoaostar.com/sources/71e56d4f.json"
private const val REMOTE_SOURCE_VERSION = 3
private const val EXTRA_SOURCES_JSON = """
[
  {
    "id": "shucheng",
    "name": "无忧书城",
    "tagline": "补充书源 · 无忧书城",
    "baseUrl": "https://www.51shucheng.net",
    "searchUrl": "/search?q={{key}}",
    "searchList": ".search_result ul li",
    "searchTitle": "h3",
    "searchIntro": "p",
    "searchLink": "a@href",
    "tocUrl": "{bookUrl}",
    "tocLinkSelector": "#toc-list a",
    "tocSort": "auto",
    "contentSelector": "#neirong",
    "contentRemove": "script,style"
  }
]
"""

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.get(context)
    val settings = SettingsRepository(context)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val api = ApiClient(httpClient)
    val wikisource = WikisourceSource(api)
    val bookSources: List<BookSource> = listOf(wikisource)

    val customSourceStore = CustomSourceStore(context.applicationContext)
    val sourceRegistry = SourceRegistry(bookSources, customSourceStore, api)

    suspend fun importRemoteSources() {
        if (settings.remoteSourcesVersion >= REMOTE_SOURCE_VERSION) return
        var complete = true
        runCatching {
            customSourceStore.importJson(api.getText(DEFAULT_SOURCE_URL), replaceExisting = true)
        }.onFailure { complete = false }
        runCatching {
            customSourceStore.importJson(EXTRA_SOURCES_JSON, replaceExisting = true)
        }.onFailure { complete = false }
        if (complete) settings.markRemoteSourcesVersion(REMOTE_SOURCE_VERSION)
    }

    val repository = BookRepository(
        database = database,
        sourceRegistry = sourceRegistry,
        localImporter = LocalImporter(context.applicationContext)
    )
}

class MoyueApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            runCatching { container.importRemoteSources() }
            runCatching {
                if (!container.settings.guideSeeded) {
                    container.repository.seedGuideIfEmpty()
                    container.settings.markGuideSeeded()
                }
            }
        }
    }
}
