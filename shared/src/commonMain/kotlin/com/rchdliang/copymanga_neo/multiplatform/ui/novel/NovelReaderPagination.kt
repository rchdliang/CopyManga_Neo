/*
 * Portions adapted from Kixyu Book MeasuredReaderLayout.kt.
 * Copyright (c) 2026 kkyu9527. MIT License; see assets/licenses/kixyubook-MIT.txt.
 * https://github.com/kkyu9527/kixyubook
 */
package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rchdliang.copymanga_neo.domain.model.NovelReaderPreferences
import com.rchdliang.copymanga_neo.domain.reading.NovelPageBlock
import com.rchdliang.copymanga_neo.domain.reading.NovelReaderDocument
import com.rchdliang.copymanga_neo.domain.reading.NovelReaderEntry
import com.rchdliang.copymanga_neo.domain.reading.NovelReaderPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlinx.coroutines.withContext
import kotlin.math.ceil

internal fun novelFont(value: String): FontFamily = when (value) {
    "SERIF" -> FontFamily.Serif
    "SANS" -> FontFamily.SansSerif
    else -> FontFamily.Default
}

internal fun novelTextStyle(preferences: NovelReaderPreferences) = TextStyle(
    fontFamily = novelFont(preferences.font),
    fontSize = preferences.fontSize.sp,
    lineHeight = (preferences.fontSize * preferences.lineHeight).sp
)

internal data class NovelPagination(
    val pages: List<NovelReaderPage> = emptyList(),
    val complete: Boolean = false
)

/** 每次重排使用独立测量器；先发布可读页面，后台继续排版，并可随设置变更取消。 */
@Composable
internal fun rememberNovelPagination(
    document: NovelReaderDocument,
    preferences: NovelReaderPreferences,
    widthPx: Int,
    heightPx: Int
): NovelPagination {
    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    val direction = LocalLayoutDirection.current
    val measurer =
        remember(document, preferences, widthPx, heightPx, density, resolver, direction) {
            TextMeasurer(resolver, density, direction, cacheSize = 0)
        }
    val spacing = with(density) { preferences.paragraphSpacing.dp.roundToPx() }
    val style = novelTextStyle(preferences)
    val preview = LocalInspectionMode.current
    var result by remember(measurer) {
        mutableStateOf(
            if (preview && widthPx > 0 && heightPx > 0) {
                NovelPagination(runNovelPreview {
                    paginateNovel(
                        document,
                        measurer,
                        style,
                        widthPx,
                        heightPx,
                        spacing
                    )
                }, true)
            } else NovelPagination()
        )
    }
    LaunchedEffect(measurer, widthPx, heightPx) {
        if (preview || widthPx <= 0 || heightPx <= 0) return@LaunchedEffect
        val pages = withContext(Dispatchers.Default) {
            paginateNovel(document, measurer, style, widthPx, heightPx, spacing) { pages ->
                withContext(Dispatchers.Main) { result = NovelPagination(pages) }
            }
        }
        result = NovelPagination(pages, true)
    }
    return result
}

/** 改编 Kixyu 的真实文字测量分页；保留空行和全部字符，不删去原始行间空白。 */
internal suspend fun paginateNovel(
    document: NovelReaderDocument,
    measurer: TextMeasurer,
    style: TextStyle,
    widthPx: Int,
    heightPx: Int,
    spacingPx: Int,
    publish: suspend (List<NovelReaderPage>) -> Unit = {}
): List<NovelReaderPage> {
    val pages = mutableListOf<NovelReaderPage>()
    var blocks = mutableListOf<NovelPageBlock>()
    var used = 0
    suspend fun flush() {
        if (blocks.isEmpty()) return
        pages += NovelReaderPage(blocks.toList())
        blocks = mutableListOf()
        used = 0
        if (pages.size == 1 || pages.size % 16 == 0) publish(pages.toList())
    }
    for (entry in document.entries) {
        currentCoroutineContext().ensureActive()
        if (entry is NovelReaderEntry.Image) {
            val imageHeight = (widthPx * 1.15f).toInt().coerceIn(1, heightPx)
            if (used + imageHeight + spacingPx > heightPx && blocks.isNotEmpty()) flush()
            val gap = spacingPx.coerceAtMost((heightPx - imageHeight).coerceAtLeast(0))
            blocks += NovelPageBlock(entry, heightPx = imageHeight, spacingPx = gap)
            used += imageHeight + gap
            if (used >= heightPx) flush()
            continue
        }
        entry as NovelReaderEntry.Text
        val text = entry.text.ifEmpty { "\u00a0" }
        var offset = 0
        while (offset < text.length) {
            currentCoroutineContext().ensureActive()
            val available = (heightPx - used).coerceAtLeast(0)
            var end = (offset + 4096).coerceAtMost(text.length)
            if (end < text.length && text[end].isLowSurrogate() && text[end - 1].isHighSurrogate()) end--
            var layout = measurer.measure(
                text.substring(offset, end), style, overflow = TextOverflow.Clip,
                constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1))
            )
            while (end < text.length && layout.size.height <= available) {
                end = (offset + (end - offset) * 2).coerceAtMost(text.length)
                if (end < text.length && text[end].isLowSurrogate() && text[end - 1].isHighSurrogate()) end--
                layout = measurer.measure(
                    text.substring(offset, end), style, overflow = TextOverflow.Clip,
                    constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1))
                )
            }
            var fitting = -1
            for (line in 0 until layout.lineCount) {
                if (layout.getLineBottom(line) <= available) fitting = line else break
            }
            if (fitting < 0 && blocks.isNotEmpty()) {
                flush()
                continue
            }
            fitting = fitting.coerceAtLeast(0)
            var next = offset + layout.getLineEnd(fitting, visibleEnd = false)
            if (next < text.length && next > 0 && text[next].isLowSurrogate() && text[next - 1].isHighSurrogate()) next--
            if (next <= offset) {
                next =
                    offset + if (text[offset].isHighSurrogate() && offset + 1 < text.length && text[offset + 1].isLowSurrogate()) 2 else 1
            }
            val measuredHeight = ceil(layout.getLineBottom(fitting)).toInt().coerceAtLeast(1)
            val gap = if (next == text.length) spacingPx.coerceAtMost(
                (available - measuredHeight).coerceAtLeast(0)
            ) else 0
            blocks += NovelPageBlock(
                entry, if (entry.text.isEmpty()) 0 else offset,
                text.substring(offset, next), measuredHeight, gap
            )
            used += measuredHeight + gap
            offset = next
            if (offset < text.length || used >= heightPx) flush()
        }
    }
    flush()
    return pages.ifEmpty { listOf(NovelReaderPage(emptyList())) }
}

/** Preview pagination has no suspending publisher; execute it synchronously without JVM runBlocking. */
private fun <T> runNovelPreview(block: suspend () -> T): T {
    var completed: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) { completed = result }
    })
    return checkNotNull(completed) { "Preview pagination must not suspend" }.getOrThrow()
}
