package com.moyue.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyue.reader.data.ReaderPrefs
import com.moyue.reader.data.ReaderTheme
import kotlin.math.roundToInt

@Composable
fun ReaderSettingsSheet(
    prefs: ReaderPrefs,
    onUpdate: ((ReaderPrefs) -> ReaderPrefs) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(bottom = 20.dp)
    ) {
        Text(
            text = "阅读设置",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(18.dp))
        Text("背景", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ReaderTheme.entries.forEach { theme ->
                ThemeSwatch(
                    theme = theme,
                    selected = prefs.theme == theme,
                    onClick = { onUpdate { it.copy(theme = theme) } }
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        SettingSlider(
            title = "字号",
            value = prefs.fontSizeSp,
            valueRange = 14f..30f,
            display = "${prefs.fontSizeSp.roundToInt()} sp",
            onChange = { value -> onUpdate { it.copy(fontSizeSp = value) } }
        )
        SettingSlider(
            title = "行距",
            value = prefs.lineSpacing,
            valueRange = 1.2f..2.3f,
            display = String.format("%.1f", prefs.lineSpacing),
            onChange = { value -> onUpdate { it.copy(lineSpacing = value) } }
        )
        SettingSlider(
            title = "页边距",
            value = prefs.marginDp.toFloat(),
            valueRange = 12f..36f,
            display = "${prefs.marginDp} dp",
            onChange = { value -> onUpdate { it.copy(marginDp = value.roundToInt()) } }
        )

        Spacer(Modifier.height(4.dp))
        SwitchRow(
            title = "翻页动画",
            subtitle = "滑动翻页时显示跟手动画",
            checked = prefs.pageAnimation,
            onCheckedChange = { value -> onUpdate { it.copy(pageAnimation = value) } }
        )
        SwitchRow(
            title = "阅读时保持亮屏",
            subtitle = "阅读界面不会自动息屏",
            checked = prefs.keepScreenOn,
            onCheckedChange = { value -> onUpdate { it.copy(keepScreenOn = value) } }
        )
    }
}

@Composable
private fun ThemeSwatch(
    theme: ReaderTheme,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = theme.palette()
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.background)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) borderColor else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "文",
                color = palette.text,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                fontSize = 18.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = theme.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(display, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
