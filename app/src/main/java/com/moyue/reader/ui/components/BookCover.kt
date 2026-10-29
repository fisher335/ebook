package com.moyue.reader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class CoverPalette(
    val start: Color,
    val end: Color,
    val ink: Color
)

private val coverPalettes = listOf(
    CoverPalette(Color(0xFF35594C), Color(0xFF1A2D26), Color(0xFFF5EEDF)),
    CoverPalette(Color(0xFF7C4340), Color(0xFF3B1F1D), Color(0xFFF6E7DA)),
    CoverPalette(Color(0xFF3E4E70), Color(0xFF1D2640), Color(0xFFEAE5D6)),
    CoverPalette(Color(0xFF8B6A3E), Color(0xFF46311B), Color(0xFFF7EFDF)),
    CoverPalette(Color(0xFF5D4B70), Color(0xFF2C213B), Color(0xFFF1E8DD)),
    CoverPalette(Color(0xFF356C6B), Color(0xFF173A39), Color(0xFFE9F1EC)),
    CoverPalette(Color(0xFF6E4B37), Color(0xFF352319), Color(0xFFF4E9DC)),
    CoverPalette(Color(0xFF4C5C3B), Color(0xFF242F1C), Color(0xFFF1EEDA)),
    CoverPalette(Color(0xFF704A52), Color(0xFF351F25), Color(0xFFF4E6E4)),
    CoverPalette(Color(0xFF41606B), Color(0xFF1C2E35), Color(0xFFEAF0F0))
)

fun coverPalette(seed: Int): CoverPalette {
    val index = ((seed.toLong() % coverPalettes.size + coverPalettes.size) % coverPalettes.size).toInt()
    return coverPalettes[index]
}

@Composable
fun BookCover(
    title: String,
    author: String,
    seed: Int,
    modifier: Modifier = Modifier,
    footer: String? = null
) {
    val palette = coverPalette(seed)
    val shape = RoundedCornerShape(10.dp)
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(0.7f)
            .clip(shape)
            .background(Brush.linearGradient(listOf(palette.start, palette.end)))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
    ) {
        val compact = maxWidth < 70.dp
        val horizontalPadding = if (compact) 7.dp else 12.dp
        val verticalPadding = if (compact) 8.dp else 13.dp
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                color = Color.White.copy(alpha = 0.055f),
                radius = w * 0.52f,
                center = Offset(w * 0.92f, h * 0.12f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.04f),
                radius = w * 0.36f,
                center = Offset(w * 0.02f, h * 0.88f)
            )
            for (i in 0..4) {
                val y = h * 0.72f + i * h * 0.035f
                drawLine(
                    color = Color.White.copy(alpha = 0.07f),
                    start = Offset(w * 0.12f, y),
                    end = Offset(w * 0.88f, y),
                    strokeWidth = 1.2f
                )
            }
            drawRect(
                color = Color.White.copy(alpha = 0.10f),
                topLeft = Offset(w * 0.07f, h * 0.06f),
                size = Size(w * 0.015f, h * 0.88f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    color = palette.ink,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = when {
                        compact -> 11.sp
                        title.length > 10 -> 14.sp
                        title.length > 6 -> 16.sp
                        else -> 18.sp
                    },
                    lineHeight = when {
                        compact -> 14.sp
                        title.length > 10 -> 19.sp
                        else -> 22.sp
                    },
                    maxLines = if (compact) 4 else 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(if (compact) 5.dp else 8.dp))
                Box(
                    modifier = Modifier
                        .width(if (compact) 16.dp else 22.dp)
                        .height(2.dp)
                        .background(palette.ink.copy(alpha = 0.55f))
                )
            }
            Column {
                if (author.isNotBlank()) {
                    Text(
                        text = author,
                        color = palette.ink.copy(alpha = 0.82f),
                        fontFamily = FontFamily.Serif,
                        fontSize = if (compact) 8.sp else 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!footer.isNullOrBlank()) {
                    Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
                    Text(
                        text = footer,
                        color = palette.ink.copy(alpha = 0.5f),
                        fontSize = if (compact) 7.sp else 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
