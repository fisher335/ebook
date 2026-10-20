package com.moyue.reader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val key: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        fun from(key: String?): AppThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class ReaderTheme(val key: String, val label: String) {
    PAPER("paper", "宣纸"),
    SEPIA("sepia", "羊皮"),
    GREEN("green", "护眼"),
    NIGHT("night", "夜间");

    companion object {
        fun from(key: String?): ReaderTheme = entries.firstOrNull { it.key == key } ?: PAPER
    }
}

data class ReaderPrefs(
    val fontSizeSp: Float = 19f,
    val lineSpacing: Float = 1.7f,
    val marginDp: Int = 22,
    val theme: ReaderTheme = ReaderTheme.PAPER,
    val pageAnimation: Boolean = true,
    val keepScreenOn: Boolean = true
)

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("moyue_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(AppThemeMode.from(prefs.getString("theme_mode", null)))
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _readerPrefs = MutableStateFlow(
        ReaderPrefs(
            fontSizeSp = prefs.getFloat("reader_font_size", 19f),
            lineSpacing = prefs.getFloat("reader_line_spacing", 1.7f),
            marginDp = prefs.getInt("reader_margin", 22),
            theme = ReaderTheme.from(prefs.getString("reader_theme", null)),
            pageAnimation = prefs.getBoolean("reader_page_animation", true),
            keepScreenOn = prefs.getBoolean("reader_keep_screen_on", true)
        )
    )
    val readerPrefs: StateFlow<ReaderPrefs> = _readerPrefs.asStateFlow()

    val guideSeeded: Boolean
        get() = prefs.getBoolean("guide_seeded", false)

    fun markGuideSeeded() {
        prefs.edit().putBoolean("guide_seeded", true).apply()
    }

    val remoteSourcesVersion: Int
        get() = prefs.getInt("remote_sources_version", 0)

    fun markRemoteSourcesVersion(version: Int) {
        prefs.edit().putInt("remote_sources_version", version).apply()
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString("theme_mode", mode.key).apply()
        _themeMode.value = mode
    }

    fun updateReaderPrefs(transform: (ReaderPrefs) -> ReaderPrefs) {
        val next = transform(_readerPrefs.value)
        prefs.edit()
            .putFloat("reader_font_size", next.fontSizeSp)
            .putFloat("reader_line_spacing", next.lineSpacing)
            .putInt("reader_margin", next.marginDp)
            .putString("reader_theme", next.theme.key)
            .putBoolean("reader_page_animation", next.pageAnimation)
            .putBoolean("reader_keep_screen_on", next.keepScreenOn)
            .apply()
        _readerPrefs.value = next
    }
}
