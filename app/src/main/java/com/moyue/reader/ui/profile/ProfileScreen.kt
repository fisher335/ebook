package com.moyue.reader.ui.profile

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyue.reader.AppContainer
import com.moyue.reader.BuildConfig
import com.moyue.reader.data.AppThemeMode
import com.moyue.reader.ui.appViewModel
import com.moyue.reader.ui.theme.Gold
import com.moyue.reader.ui.theme.InkDeep
import com.moyue.reader.ui.theme.Moss
import com.moyue.reader.ui.theme.Terracotta

@Composable
fun ProfileScreen(container: AppContainer) {
    val viewModel: ProfileViewModel = appViewModel {
        ProfileViewModel(it.repository, it.settings)
    }
    val stats by viewModel.stats.collectAsState()
    val message by viewModel.message.collectAsState()
    val themeMode by container.settings.themeMode.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearShelf by remember { mutableStateOf(false) }
    var showClearCache by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Box(Modifier.background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(Modifier.padding(top = 4.dp)) {
                    Text(
                        text = "我的",
                        fontFamily = FontFamily.Serif,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "阅读，是一场安静的远行",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(InkDeep, Moss)))
                        .padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.AutoStories,
                                    contentDescription = null,
                                    tint = Color(0xFFF7F1E5),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "墨阅书友",
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFF7F1E5)
                                )
                                Text(
                                    text = "把喜欢的文字，留在身边",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.66f)
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                            ProfileStat(stats.total.toString(), "藏书")
                            ProfileStat(stats.reading.toString(), "在读")
                            ProfileStat(stats.finished.toString(), "读完")
                            ProfileStat(stats.bookmarks.toString(), "书签")
                        }
                    }
                }
            }

            item {
                SettingsCard(title = "外观") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.label) },
                                shape = RoundedCornerShape(11.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "阅读界面的纸张、字号与行距，可在阅读时点击「设置」调整。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )
                }
            }

            item {
                SettingsCard(title = "数据管理") {
                    SettingRow(
                        icon = Icons.Outlined.Storage,
                        title = "清理在线阅读缓存",
                        subtitle = "释放空间，书架与进度不受影响",
                        onClick = { showClearCache = true }
                    )
                    SettingRow(
                        icon = Icons.Outlined.DeleteOutline,
                        title = "清空书架",
                        subtitle = "删除全部书籍、进度与书签",
                        onClick = { showClearShelf = true }
                    )
                }
            }

            item {
                SettingsCard(title = "关于书源") {
                    Text(
                        text = "维基文库 · 自定义书源",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "维基文库内容遵循 CC BY-SA 协议；自定义书源由你自行添加和维护。本应用不内置任何盗版站点。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "墨阅 v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "吹灭读书灯，一身都是月。",
                        fontFamily = FontFamily.Serif,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "—— 清 · 张潮《幽梦影》",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
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

    if (showClearCache) {
        AlertDialog(
            onDismissRequest = { showClearCache = false },
            title = { Text("清理阅读缓存？") },
            text = { Text("已缓存的在线阅读章节会被清除，爱读的书再次打开时需要联网加载。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearCache = false
                    viewModel.clearCache()
                }) { Text("清理") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCache = false }) { Text("取消") }
            }
        )
    }

    if (showClearShelf) {
        AlertDialog(
            onDismissRequest = { showClearShelf = false },
            title = { Text("清空书架？") },
            text = { Text("全部书籍、阅读进度与书签都会被删除，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearShelf = false
                    viewModel.clearShelf()
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearShelf = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ProfileStat(value: String, label: String) {
    Column {
        Text(
            text = value,
            fontFamily = FontFamily.Serif,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFF7F1E5)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.62f)
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(15.dp)
                    .clip(CircleShape)
                    .background(Terracotta)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(21.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp)
        )
    }
}
