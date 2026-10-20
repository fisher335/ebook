package com.moyue.reader.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyue.reader.AppContainer
import com.moyue.reader.source.SourceBook
import com.moyue.reader.ui.appViewModel
import com.moyue.reader.ui.components.BookCover
import com.moyue.reader.ui.theme.Gold
import com.moyue.reader.ui.theme.InkDeep
import com.moyue.reader.ui.theme.Moss
import com.moyue.reader.ui.theme.MossLight
import com.moyue.reader.ui.theme.Terracotta

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    container: AppContainer,
    onOpenBook: (String) -> Unit
) {
    val viewModel: DiscoverViewModel = appViewModel {
        DiscoverViewModel(it.repository, it.sourceRegistry)
    }
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }
    var selectedCategory by rememberSaveable { mutableStateOf("全部") }
    var showSourceManager by rememberSaveable { mutableStateOf(false) }

    val categories = remember(state.catalog) {
        listOf("全部") + state.catalog.map { it.category }.distinct()
    }

    LaunchedEffect(state.sourceId) {
        selectedCategory = "全部"
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(state.addedBookId) {
        val id = state.addedBookId
        if (id != null) {
            viewModel.consumeAddedBook()
            viewModel.closeBook()
            onOpenBook(id)
        }
    }

    LaunchedEffect(state.searchActive) {
        if (state.searchActive) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "书城",
                                fontFamily = FontFamily.Serif,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "公共领域经典 · 免费阅读 · 无广告",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showSourceManager = true }) {
                            Icon(
                                Icons.Outlined.Tune,
                                contentDescription = "书源管理",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (state.searchActive) {
                            IconButton(onClick = { viewModel.closeSearch() }) {
                                Icon(Icons.Outlined.Close, contentDescription = "关闭搜索")
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = { viewModel.setQuery(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                if (it.isFocused) viewModel.activateSearch()
                            },
                        placeholder = { Text("搜索书名、作者或关键词") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "清空")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.search() })
                    )
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(state.sources, key = { it.id }) { source ->
                        FilterChip(
                            selected = state.sourceId == source.id,
                            onClick = { viewModel.selectSource(source.id) },
                            label = { Text(source.name) },
                            leadingIcon = if (state.sourceId == source.id) {
                                {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            item {
                Text(
                    text = state.sources.firstOrNull { it.id == state.sourceId }?.tagline.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }

            if (state.searchActive) {
                if (state.searchLoading) {
                    item { LoadingRow("正在全源搜索…") }
                } else if (state.searchError != null) {
                    item {
                        ErrorCard(
                            message = state.searchError.orEmpty(),
                            onRetry = { viewModel.search() }
                        )
                    }
                } else if (state.results.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 54.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (state.query.isBlank()) "输入书名或关键词开始搜索"
                                else "没有找到相关结果",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "找到 ${state.results.size} 个结果",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(state.results, key = { it.sourceId + it.key }) { book ->
                        DiscoverBookRow(
                            book = book,
                            sourceName = state.sources.firstOrNull { it.id == book.sourceId }?.name,
                            onClick = { viewModel.openBook(book) }
                        )
                    }
                }
            } else {
                item {
                    FeaturedBanner()
                }
                if (state.catalogLoading) {
                    item { LoadingRow("正在整理书单…") }
                } else if (state.catalogError != null) {
                    item {
                        ErrorCard(
                            message = state.catalogError.orEmpty(),
                            onRetry = { viewModel.refreshCatalog() }
                        )
                    }
                } else {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(categories, key = { it }) { category ->
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = { selectedCategory = category },
                                    label = { Text(category) },
                                    shape = RoundedCornerShape(11.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                )
                            }
                        }
                    }
                    val filtered = if (selectedCategory == "全部") {
                        state.catalog
                    } else {
                        state.catalog.filter { it.category == selectedCategory }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedCategory == "全部") "典藏精选" else selectedCategory,
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamily.Serif
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${filtered.size} 部",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            if (state.catalog.isEmpty()) {
                                SourceEmptyHint()
                            } else {
                                LoadingRow("这个分类下暂无书籍")
                            }
                        }
                    } else {
                        items(filtered, key = { it.sourceId + it.key }) { book ->
                            DiscoverBookRow(book = book, onClick = { viewModel.openBook(book) })
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
    }

    state.selected?.let { book ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeBook() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            BookDetailSheet(
                book = book,
                sourceName = state.sources.firstOrNull { it.id == book.sourceId }?.name.orEmpty(),
                chapterCount = state.preview?.chapters?.size,
                previewLoading = state.previewLoading,
                previewError = state.previewError,
                adding = state.adding,
                onAdd = { viewModel.addSelected() },
                onClose = { viewModel.closeBook() }
            )
        }
    }

    if (showSourceManager) {
        ModalBottomSheet(
            onDismissRequest = { showSourceManager = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SourceManagerSheet(
                sources = state.sources,
                customSources = state.customSources,
                onAdd = { json -> viewModel.addCustomSource(json) },
                onRemove = { id -> viewModel.removeCustomSource(id) },
                onDismiss = { showSourceManager = false }
            )
        }
    }
}

@Composable
private fun SourceEmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            modifier = Modifier.size(38.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "这个书源没有推荐书单",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "在上方搜索框输入书名，或换一个书源试试",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun FeaturedBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(146.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(InkDeep, Moss)))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.62f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "典藏经典",
                fontFamily = FontFamily.Serif,
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF8F1E3)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "从三国到红楼，\n把中文经典装进口袋。",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = 19.sp
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                "三國演義" to 101,
                "紅樓夢" to 202,
                "西遊記" to 303
            ).forEachIndexed { index, (title, seed) ->
                BookCover(
                    title = title,
                    author = "",
                    seed = seed,
                    modifier = Modifier
                        .width(60.dp)
                        .height(84.dp)
                        .graphicsLayer {
                            rotationZ = when (index) {
                                0 -> -5f
                                2 -> 5f
                                else -> 0f
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun DiscoverBookRow(
    book: SourceBook,
    sourceName: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookCover(
            title = book.title,
            author = book.author,
            seed = book.coverSeed,
            modifier = Modifier
                .width(62.dp)
                .height(87.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Serif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = book.author.ifBlank { "佚名" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = book.intro,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Outlined.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun BookDetailSheet(
    book: SourceBook,
    sourceName: String,
    chapterCount: Int?,
    previewLoading: Boolean,
    previewError: String?,
    adding: Boolean,
    onAdd: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(bottom = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            BookCover(
                title = book.title,
                author = book.author,
                seed = book.coverSeed,
                modifier = Modifier
                    .width(96.dp)
                    .height(136.dp)
            )
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    fontFamily = FontFamily.Serif,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 30.sp
                )
                Spacer(Modifier.height(5.dp))
                Text(
                text = buildString {
                    append(book.author.ifBlank { "佚名" })
                    if (!sourceName.isNullOrBlank()) {
                        append(" · ")
                        append(sourceName)
                    }
                },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        sourceName.ifBlank { book.category },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (chapterCount != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "共 $chapterCount 章",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭")
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "简介",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = book.intro.ifBlank { "暂无简介" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 23.sp
        )
        if (previewLoading) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "正在读取目录…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (previewError != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = previewError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onAdd,
            enabled = !adding,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            if (adding) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text("正在加入…")
            } else {
                Text(
                    text = "加入书架并阅读",
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "内容来自公共领域书源，仅供阅读学习。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LoadingRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
            .padding(18.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onRetry) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("重试")
            }
        }
    }
}
