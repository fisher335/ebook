package com.moyue.reader.ui.discover

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyue.reader.source.BookSource
import com.moyue.reader.source.CustomSource
import com.moyue.reader.source.SourceBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SourceManagerSheet(
    sources: List<BookSource>,
    customSources: List<CustomSource>,
    onAdd: suspend (String) -> Result<CustomSource>,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var json by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<BookSource?>(null) }
    val customIds = remember(customSources) { customSources.mapNotNull { it.id }.toSet() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalStateException("无法读取文件")
                }
            }.onSuccess {
                json = it
                error = null
                editing = true
            }.onFailure {
                error = it.message ?: "读取文件失败"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("书源管理", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${sources.size} 个书源 · 内置 ${sources.size - customIds.size} · 自定义 ${customIds.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!editing) {
                FilledTonalButton(
                    onClick = {
                        editing = true
                        error = null
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加")
                }
            } else {
                IconButton(onClick = { editing = false }) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭编辑")
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (editing) {
            Text(
                "粘贴 JSON 规则。支持 CSS 选择器、a@href 属性规则，也兼容阅读器书源格式。\n" +
                    "至少提供 explore 书单，或 searchUrl + searchList。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = json,
                onValueChange = {
                    json = it
                    error = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                ),
                placeholder = { Text("在此粘贴书源 JSON…") },
                shape = RoundedCornerShape(14.dp)
            )
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        filePicker.launch(
                            arrayOf("application/json", "text/plain", "application/octet-stream")
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Outlined.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("导入文件")
                }
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = {
                    json = SAMPLE_SOURCE_JSON
                    error = null
                }) {
                    Text("填入示例")
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (adding) return@Button
                        adding = true
                        error = null
                        scope.launch {
                            val result = onAdd(json)
                            adding = false
                            result.onSuccess {
                                editing = false
                                json = ""
                                error = null
                                onDismiss()
                            }.onFailure {
                                error = it.message ?: "书源校验失败"
                            }
                        }
                    },
                    enabled = !adding,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (adding) "正在验证…" else "验证并添加")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "提示：只添加你有权访问的书源。本应用不内置任何盗版站点。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(10.dp))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(sources, key = { it.id }) { source ->
                    val custom = source.id in customIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    source.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    if (custom) "自定义" else "内置",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (custom) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (custom) MaterialTheme.colorScheme.secondaryContainer
                                            else MaterialTheme.colorScheme.primaryContainer
                                        )
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                source.tagline,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (custom) {
                            IconButton(onClick = { deleteTarget = source }) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = "删除书源",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = "内置书源：维基文库。自定义书源由你自行维护。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }
    }

    deleteTarget?.let { source ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除书源？") },
            text = { Text("将移除「${source.name}」以及它的推荐书单。已加入书架的书会保留，但未缓存的章节将无法继续加载。") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(source.id)
                    deleteTarget = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            }
        )
    }
}

private const val SAMPLE_SOURCE_JSON = """
{
  "name": "示例书源",
  "tagline": "一个用于演示规则的自定义书源",
  "baseUrl": "https://example.com",
  "explore": [
    {
      "title": "示例书",
      "author": "作者",
      "intro": "这是一条推荐书单，可留空。",
      "category": "精选",
      "url": "/book/1/"
    }
  ],
  "searchUrl": "/search?q={query}",
  "searchList": ".book-item",
  "searchTitle": "h3",
  "searchAuthor": ".author",
  "searchIntro": ".intro",
  "searchLink": "a@href",
  "tocUrl": "{bookUrl}",
  "tocLinkSelector": "a[href]",
  "tocLinkRegex": "{bookPath}\\d+\\.html$",
  "tocSort": "auto",
  "contentSelector": ".chapter-content",
  "contentRemove": ".ads,script,style"
}
"""
