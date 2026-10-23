package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.rchdliang.copymanga_neo.domain.reading.*

/** 双叶暂不推理；连续模式只选当前原图，单叶含封面/横图沿用单任务。 */
internal fun readerAllowsSuperResolution(continuous: Boolean, leafCount: Int): Boolean =
    continuous || leafCount == 1

@Composable
internal expect fun rememberReaderSrPlatform(loader: ImageLoader): ReaderSrPlatform

@Composable
internal fun rememberReaderSrSession(loader: ImageLoader): ReaderSrSession {
    val platform = rememberReaderSrPlatform(loader)
    val scope = rememberCoroutineScope()
    val session = remember(platform, scope) { ReaderSrSession(scope, platform) }
    DisposableEffect(session) { onDispose { session.close() } }
    return session
}

@Composable
internal fun readerSrPainter(session: ReaderSrSession?, pageId: String, revision: Int, original: Painter): Painter {
    if (session == null) return original
    LaunchedEffect(session, pageId, revision) { session.originalReady(pageId, revision) }
    val settings by session.settings.collectAsState()
    if (!settings.enabled) return original
    val display by session.display.collectAsState()
    val ready = display?.takeIf { it.first == pageId }?.second as? SrStatus.Ready ?: return original
    val context = LocalPlatformContext.current
    val request = remember(context, ready.artifact) {
        ImageRequest.Builder(context).data(ready.artifact.png)
            .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()
    }
    // Original remains visible until the verified result is decoded; no replacement of its cache entry.
    return rememberAsyncImagePainter(request, placeholder = original, error = original)
}

@Composable
internal fun ReaderSuperResolutionOptions(session: ReaderSrSession) {
    val settings by session.settings.collectAsState()
    val display by session.display.collectAsState()
    val preparation by session.preparationMessage.collectAsState()
    val detail by session.detail.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("超分辨率 2×", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(!settings.enabled, { session.settings.value = settings.copy(enabled = false) }, { Text("关闭") })
            for ((mode, label) in listOf(SrExecutionMode.AUTO to "自动", SrExecutionMode.CPU to "CPU", SrExecutionMode.GPU to "GPU")) {
                FilterChip(settings.enabled && settings.mode == mode,
                    { session.settings.value = settings.copy(enabled = true, mode = mode) }, { Text(label) },
                    enabled = session.unavailableReason == null)
            }
        }
        if (settings.enabled && settings.mode == SrExecutionMode.AUTO) {
            ReaderSettingSwitch("GPU 不可用时尝试 CPU", settings.cpuFallback) {
                session.settings.value = settings.copy(cpuFallback = it)
            }
        }
        val status = when (val state = display?.second) {
            is SrStatus.Unavailable -> state.reason
            SrStatus.Processing -> "正在后台处理，先显示原图"
            SrStatus.Failed -> "处理失败，继续显示原图"
            is SrStatus.Ready -> "当前页已增强 · $detail"
            else -> preparation ?: session.unavailableReason ?: if (settings.enabled) detail else "默认关闭；仅处理当前单页，不上传图片"
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
    }
}
