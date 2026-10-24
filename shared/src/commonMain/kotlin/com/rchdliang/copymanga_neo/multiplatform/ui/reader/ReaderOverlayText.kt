package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 填充与细描边使用同一文本布局；不绘制覆盖漫画的背景。 */
@Composable
internal fun ReaderOverlayText(text: String, modifier: Modifier = Modifier,
    color: Color = Color.White, style: TextStyle = MaterialTheme.typography.labelSmall,
    maxLines: Int = 1, overflow: TextOverflow = TextOverflow.Ellipsis) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(text, modifier.drawBehind {
        layout?.let { drawText(it, color = Color.Black, drawStyle = Stroke(width = 1.5.dp.toPx())) }
    }, color = color, style = style, maxLines = maxLines, overflow = overflow, onTextLayout = { layout = it })
}

@Composable
internal fun ReaderRestingOverlay(chapter: String, pages: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ReaderOverlayText(chapter, Modifier.weight(1f))
        ReaderOverlayText(pages)
    }
}
