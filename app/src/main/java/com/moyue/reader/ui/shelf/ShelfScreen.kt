package com.moyue.reader.ui.shelf

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyue.reader.AppContainer
import com.moyue.reader.data.BookEntity
import com.moyue.reader.ui.appViewModel
import com.moyue.reader.ui.components.BookCover
import com.moyue.reader.ui.theme.Gold
import com.moyue.reader.ui.theme.Ink
import com.moyue.reader.ui.theme.InkDeep
import com.moyue.reader.ui.theme.Moss
import com.moyue.reader.ui.theme.MossLight
import com.moyue.reader.ui.theme.Terracotta
import java.util.Calendar
import kotlin.math.roundToInt

@Composable
fun ShelfScreen(
    container: AppContainer,
    onOpenBook: (String) -> Unit,
    onDiscover: () -> Unit
) {
    val viewModel: ShelfViewModel = appViewModel { ShelfViewModel(it.repository) }
    val books by viewModel.books.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var bookToDelete by remember { mutableStateOf<BookEntity?>(null) }
    var sortByRecent by rememberSaveable { mutableStateOf(true) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.importBook(uri)
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val sortedBooks = remember(books, sortByRecent) {
        if (sortByRecent) {
            books.sortedWith(
                compareByDescending<BookEntity> { if (it.lastReadAt == 0L) it.addedAt else it.lastReadAt }
            )
        } else {
            books.sortedBy { it.title }
        }
    }
    val continueBook = books.filter { it.lastReadAt > 0L }.maxByOrNull { it.lastReadAt }
        ?: books.maxByOrNull { it.addedAt }
    val readingCount = books.count { it.progress > 0.005f && it.progress < 0.995f }
    val finishedCount = books.count { it.progress >= 0.995f }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ShelfHeader(
                    onSearch = onDiscover,
                    onImport = {
                        importLauncher.launch(
                            arrayOf("text/plain", "application/epub+zip", "application/octet-stream")
                        )
                    }
                )
            }

            if (books.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    StatsStrip(
                        total = books.size,
                        reading = readingCount,
                        finished = finishedCount
                    )
                }
                if (continueBook != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ContinueReadingCard(
                            book = continueBook,
                            onClick = { onOpenBook(continueBook.id) }
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "我的书架",
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamily.Serif,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${books.size} 本藏书 · 慢慢读",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { sortByRecent = !sortByRecent }) {
                            Text(
                                text = if (sortByRecent) "最近阅读" else "按书名",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                items(sortedBooks, key = { it.id }) { book ->
                    ShelfBookItem(
                        book = book,
                        onClick = { onOpenBook(book.id) },
                        onLongClick = { bookToDelete = book }
                    )
                }
                item {
                    AddBookTile(
                        onClick = {
                            importLauncher.launch(
                                arrayOf("text/plain", "application/epub+zip", "application/octet-stream")
                            )
                        }
                    )
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyShelf(onDiscover = onDiscover)
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

    bookToDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text("移出书架？") },
            text = { Text("《${book.title}》的阅读进度与书签也会一并删除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBook(book)
                    bookToDelete = null
                }) {
                    Text("移出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (importing) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            title = { Text("正在导入") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text("正在解析 TXT / EPUB 文件…")
                }
            }
        )
    }
}

@Composable
private fun ShelfHeader(onSearch: () -> Unit, onImport: () -> Unit) {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..10 -> "早上好"
            in 11..13 -> "中午好"
            in 14..17 -> "下午好"
            in 18..22 -> "晚上好"
            else -> "夜深了"
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp)
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(150.dp)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Terracotta.copy(alpha = 0.16f), Color.Transparent)
                ),
                radius = size.minDimension / 2f
            )
            drawCircle(
                color = Gold.copy(alpha = 0.11f),
                radius = size.minDimension * 0.22f,
                center = Offset(size.width * 0.68f, size.height * 0.42f)
            )
        }
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "墨阅",
                        fontFamily = FontFamily.Serif,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "$greeting，在文字里安放片刻安静",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onImport) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "导入本地书籍",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = onSearch) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "搜索书籍",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsStrip(total: Int, reading: Int, finished: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatChip("藏书", total, Modifier.weight(1f))
        StatChip("在读", reading, Modifier.weight(1f))
        StatChip("读完", finished, Modifier.weight(1f))
    }
}

@Composable
private fun StatChip(label: String, value: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = value.toString(),
            fontFamily = FontFamily.Serif,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ContinueReadingCard(book: BookEntity, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(184.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(InkDeep, Moss)))
            .clickable(onClick = onClick)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = size.height * 0.86f,
                center = Offset(size.width * 0.9f, size.height * 0.1f)
            )
            drawCircle(
                color = Gold.copy(alpha = 0.14f),
                radius = size.height * 0.28f,
                center = Offset(size.width * 0.78f, size.height * 0.78f)
            )
            for (i in 0..3) {
                drawLine(
                    color = Color.White.copy(alpha = 0.055f),
                    start = Offset(size.width * 0.6f, size.height * (0.3f + i * 0.1f)),
                    end = Offset(size.width * 0.98f, size.height * (0.3f + i * 0.1f)),
                    strokeWidth = 1.4f
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCover(
                title = book.title,
                author = book.author,
                seed = book.coverSeed,
                modifier = Modifier
                    .height(128.dp)
                    .width(91.dp)
            )
            Spacer(Modifier.width(18.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "继续阅读",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.66f),
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = book.title,
                        fontFamily = FontFamily.Serif,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF7F1E5),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (book.progress > 0f) "已读 ${(book.progress * 100).roundToInt()}%"
                        else "刚刚加入书架",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(book.progress.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(Gold)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "接着读",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFF7F1E5)
                            )
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Outlined.ArrowForward,
                                contentDescription = null,
                                tint = Color(0xFFF7F1E5),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfBookItem(
    book: BookEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        BookCover(
            title = book.title,
            author = book.author,
            seed = book.coverSeed,
            modifier = Modifier.fillMaxWidth(),
            footer = when (book.sourceId) {
                "local" -> "本地"
                "wikisource" -> "维基文库"
                else -> null
            }
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(book.progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Terracotta)
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = when {
                book.progress >= 0.995f -> "已读完"
                book.progress > 0f -> "已读 ${(book.progress * 100).roundToInt()}%"
                else -> "未开始"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun AddBookTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "导入书籍",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "导入",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "本地书籍",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyShelf(onDiscover: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 44.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(RoundedCornerShape(95.dp))
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(130.dp)) {
                val w = size.width
                val h = size.height
                val left = Offset(w * 0.15f, h * 0.28f)
                val right = Offset(w * 0.85f, h * 0.28f)
                val bottom = h * 0.78f
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(left.x, left.y)
                    quadraticBezierTo(w * 0.35f, h * 0.2f, w * 0.5f, h * 0.34f)
                    quadraticBezierTo(w * 0.65f, h * 0.2f, right.x, right.y)
                    lineTo(right.x, bottom)
                    quadraticBezierTo(w * 0.65f, h * 0.68f, w * 0.5f, h * 0.82f)
                    quadraticBezierTo(w * 0.35f, h * 0.68f, left.x, bottom)
                    close()
                }
                drawPath(
                    path = path,
                    color = Moss.copy(alpha = 0.18f)
                )
                drawLine(
                    color = Moss.copy(alpha = 0.45f),
                    start = Offset(w * 0.5f, h * 0.34f),
                    end = Offset(w * 0.5f, h * 0.82f),
                    strokeWidth = 2f
                )
                for (i in 0..4) {
                    val y = h * (0.43f + i * 0.075f)
                    drawLine(
                        color = MossLight.copy(alpha = 0.55f),
                        start = Offset(w * 0.25f, y),
                        end = Offset(w * 0.44f, y),
                        strokeWidth = 1.5f
                    )
                    drawLine(
                        color = MossLight.copy(alpha = 0.55f),
                        start = Offset(w * 0.56f, y),
                        end = Offset(w * 0.75f, y),
                        strokeWidth = 1.5f
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "书架还是空的",
            fontFamily = FontFamily.Serif,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "去书城挑一本经典，或导入自己的 TXT / EPUB",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onDiscover)
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "去书城看看",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Outlined.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}
