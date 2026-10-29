package com.moyue.reader.ui.reader

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.moyue.reader.MoyueApp
import com.moyue.reader.data.AppThemeMode
import com.moyue.reader.data.BookmarkEntity
import com.moyue.reader.data.ChapterEntity
import com.moyue.reader.ui.appViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit
) {
    val viewModel: ReaderViewModel = appViewModel(key = bookId) {
        ReaderViewModel(bookId, it.repository, it.settings)
    }
    val state by viewModel.state.collectAsState()
    val palette = state.prefs.theme.palette()
    val context = LocalContext.current
    val activity = context as? Activity
    val appThemeMode by (context.applicationContext as MoyueApp)
        .container.settings.themeMode.collectAsState()
    val appDarkTheme = when (appThemeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val currentAppDarkTheme by rememberUpdatedState(appDarkTheme)

    var controlsVisible by rememberSaveable { mutableStateOf(false) }
    var showToc by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showBookmarks by rememberSaveable { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    var controlsHeightPx by remember { mutableStateOf(0) }
    val viewRef = remember { mutableStateOf<ReaderView?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val density = LocalDensity.current
    val statusInsetDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationInsetDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusInsetPx = with(density) { statusInsetDp.toPx() }
    val navigationInsetPx = with(density) { navigationInsetDp.toPx() }

    val book = state.book
    val style = ReaderStyle(
        backgroundColor = palette.background.toArgb(),
        backgroundColorAlt = palette.backgroundAlt.toArgb(),
        textColor = palette.text.toArgb(),
        secondaryColor = palette.secondary.toArgb(),
        accentColor = palette.accent.toArgb(),
        fontSp = state.prefs.fontSizeSp,
        lineSpacing = state.prefs.lineSpacing,
        marginDp = state.prefs.marginDp.toFloat(),
        bookTitle = book?.title.orEmpty(),
        chapterTitle = state.chapterTitle,
        chapterIndex = state.chapterIndex,
        totalChapters = book?.totalChapters ?: 1,
        statusBarInsetPx = statusInsetPx.toInt(),
        navigationBarInsetPx = navigationInsetPx.toInt(),
        bottomOverlayPx = if (controlsVisible) {
            (controlsHeightPx - navigationInsetPx.toInt()).coerceAtLeast(0)
        } else {
            0
        },
        pageAnimation = state.prefs.pageAnimation
    )

    LaunchedEffect(state.currentPage, state.pageCount, state.chapterIndex, state.chapters.size) {
        val total = state.chapters.size.coerceAtLeast(1)
        sliderValue = ((state.chapterIndex + (state.currentPage + 1f) / state.pageCount) / total)
            .coerceIn(0f, 1f)
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(
        controlsVisible,
        showToc,
        showSettings,
        showBookmarks,
        state.loading,
        state.error
    ) {
        if (controlsVisible && !showToc && !showSettings && !showBookmarks &&
            !state.loading && state.error == null
        ) {
            delay(3500)
            controlsVisible = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.saveNow() }
    }

    DisposableEffect(state.prefs.keepScreenOn) {
        val window = activity?.window
        if (state.prefs.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(palette.isLight) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = palette.isLight
            controller.isAppearanceLightNavigationBars = palette.isLight
        }
        onDispose {
            val window = activity?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !currentAppDarkTheme
                controller.isAppearanceLightNavigationBars = !currentAppDarkTheme
            }
        }
    }

    BackHandler {
        when {
            showSettings -> showSettings = false
            showToc -> showToc = false
            showBookmarks -> showBookmarks = false
            controlsVisible -> controlsVisible = false
            else -> {
                viewModel.saveNow()
                onBack()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        AndroidView(
            factory = { context ->
                ReaderView(context).also { viewRef.value = it }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.updateStyle(style)
                view.setChromeVisible(!controlsVisible)
                view.onPageChanged = { page, count ->
                    viewModel.onPageChanged(page, count)
                }
                view.onCenterTap = {
                    controlsVisible = !controlsVisible
                }
                view.onNextChapter = { viewModel.nextChapter() }
                view.onPreviousChapter = { viewModel.previousChapter() }
                val contentKey = state.contentKey
                if (state.content.isNotBlank() && (view.tag as? Int) != contentKey) {
                    view.tag = contentKey
                    view.setChapter(
                        title = state.chapterTitle,
                        text = state.content,
                        initialPage = state.currentPage
                    )
                }
            }
        )

        AnimatedVisibility(
            visible = controlsVisible && !state.loading && state.error == null,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                palette.background.copy(alpha = 0.98f),
                                palette.background.copy(alpha = 0.88f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    viewModel.saveNow()
                    onBack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = palette.text
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = book?.title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.chapterTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val bookmarked = state.bookmarks.any {
                    it.chapterIndex == state.chapterIndex && it.page == state.currentPage
                }
                IconButton(
                    onClick = {
                        val snippet = viewRef.value?.currentPageSnippet().orEmpty()
                        viewModel.toggleBookmark(snippet)
                    }
                ) {
                    Icon(
                        imageVector = if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "书签",
                        tint = if (bookmarked) palette.accent else palette.text
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && !state.loading && state.error == null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                palette.background.copy(alpha = 0.9f),
                                palette.background
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 2.dp)
                    .onGloballyPositioned { controlsHeightPx = it.size.height }
            ) {
                Slider(
                    value = sliderValue.coerceIn(0f, 1f),
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        val totalChapters = state.chapters.size.coerceAtLeast(1)
                        if (totalChapters <= 1) {
                            val targetPage = (sliderValue * (state.pageCount - 1).coerceAtLeast(1))
                                .roundToInt()
                            viewRef.value?.goToPage(targetPage)
                        } else {
                            val targetChapter = (sliderValue * totalChapters)
                                .toInt()
                                .coerceIn(0, totalChapters - 1)
                            viewModel.openChapter(targetChapter, 0)
                        }
                    },
                    valueRange = 0f..1f,
                    enabled = state.chapters.size > 1 || state.pageCount > 1,
                    colors = SliderDefaults.colors(
                        thumbColor = palette.accent,
                        activeTrackColor = palette.accent,
                        inactiveTrackColor = palette.secondary.copy(alpha = 0.25f)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ReaderControl(Icons.AutoMirrored.Outlined.List, "目录", palette.text) {
                        showToc = true
                    }
                    ReaderControl(Icons.Outlined.Bookmarks, "书签", palette.text) {
                        showBookmarks = true
                    }
                    ReaderControl(Icons.Outlined.Tune, "设置", palette.text) {
                        showSettings = true
                    }
                    ReaderControl(Icons.Outlined.ChevronLeft, "上一章", palette.text) {
                        viewModel.previousChapter()
                    }
                    ReaderControl(Icons.Outlined.ChevronRight, "下一章", palette.text) {
                        viewModel.nextChapter()
                    }
                }
            }
        }

        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.background.copy(alpha = 0.94f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = palette.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "正在铺开书页…",
                        color = palette.secondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (!state.loading && state.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.background.copy(alpha = 0.97f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "暂时没有打开这一页",
                        fontFamily = FontFamily.Serif,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.text
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.secondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.retry() },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                        ) {
                            Text("重新加载")
                        }
                        TextButton(onClick = {
                            viewModel.saveNow()
                            onBack()
                        }) {
                            Text("返回", color = palette.secondary)
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (controlsVisible) 154.dp else 16.dp)
        )
    }

    if (showToc) {
        ModalBottomSheet(
            onDismissRequest = { showToc = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            TocContent(
                chapters = state.chapters,
                currentChapter = state.chapterIndex,
                onSelect = { index ->
                    showToc = false
                    controlsVisible = false
                    viewModel.openChapter(index, 0)
                }
            )
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ReaderSettingsSheet(
                prefs = state.prefs,
                onUpdate = { transform -> viewModel.updatePrefs(transform) }
            )
        }
    }

    if (showBookmarks) {
        ModalBottomSheet(
            onDismissRequest = { showBookmarks = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            BookmarksContent(
                bookmarks = state.bookmarks,
                onOpen = { bookmark ->
                    showBookmarks = false
                    controlsVisible = false
                    viewModel.openBookmark(bookmark)
                },
                onDelete = { bookmark -> viewModel.removeBookmark(bookmark) }
            )
        }
    }
}

@Composable
private fun ReaderControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint.copy(alpha = 0.8f))
    }
}

@Composable
private fun TocContent(
    chapters: List<ChapterEntity>,
    currentChapter: Int,
    onSelect: (Int) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(chapters, query) {
        if (query.isBlank()) chapters else chapters.filter { it.title.contains(query, true) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(currentChapter) {
        val index = filtered.indexOfFirst { it.chapterIndex == currentChapter }
        if (index >= 0) listState.scrollToItem(index)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.86f)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("目录", style = MaterialTheme.typography.titleLarge)
                Text(
                    "共 ${chapters.size} 章",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索章节") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = "清空")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(filtered, key = { _, chapter -> chapter.chapterIndex }) { _, chapter ->
                val selected = chapter.chapterIndex == currentChapter
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .clickable { onSelect(chapter.chapterIndex) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = (chapter.chapterIndex + 1).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(38.dp)
                    )
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Text(
                            text = "阅读中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarksContent(
    bookmarks: List<BookmarkEntity>,
    onOpen: (BookmarkEntity) -> Unit,
    onDelete: (BookmarkEntity) -> Unit
) {
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.72f)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Text("书签", style = MaterialTheme.typography.titleLarge)
        Text(
            "共 ${bookmarks.size} 条批注",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        if (bookmarks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Outlined.Bookmarks,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(38.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "还没有书签",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "在阅读界面点击书签图标即可保存当前页",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .clickable { onOpen(bookmark) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                bookmark.chapterTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                bookmark.snippet.ifBlank { "第 ${bookmark.page + 1} 页" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${formatter.format(Date(bookmark.createdAt))} · 第 ${bookmark.page + 1} 页",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        IconButton(onClick = { onDelete(bookmark) }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "删除书签",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}


