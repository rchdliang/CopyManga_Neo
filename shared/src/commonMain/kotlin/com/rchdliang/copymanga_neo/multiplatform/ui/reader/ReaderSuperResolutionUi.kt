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
internal fun ReaderSuperResolutionOptions(session: ReaderSrSession, singlePage: Boolean = true) {
    val settings by session.settings.collectAsState()
    val display by session.display.collectAsState()
    val preparation by session.preparationMessage.collectAsState()
    val detail by session.detail.collectAsState()
    val modelState = session.models?.state?.collectAsState()?.value ?: SrModelState.Ready
    var advanced by remember { mutableStateOf(false) }
    ReaderSrModelControls(session) { enable, manage ->
        ReaderSettingSwitch("超分辨率", settings.enabled, enabled = session.unavailableReason == null) { selected ->
            if (selected) enable(settings.mode) else session.settings.value = settings.copy(enabled = false)
        }
        Text("本次阅读 · 放大至 2 倍，图片在本机处理", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        session.unavailableReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        val processing = when (display?.second) {
            SrStatus.Processing -> "正在增强当前页…"
            is SrStatus.Ready -> "当前页已增强"
            SrStatus.Failed -> "增强失败，继续显示原图"
            is SrStatus.Unavailable -> (display?.second as SrStatus.Unavailable).reason
            else -> preparation ?: "等待当前页加载"
        }
        if (settings.enabled && modelState == SrModelState.Ready) Text(if (singlePage) processing else "当前双页使用原图", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "收起处理方式" else "处理方式") }
        if (advanced) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((mode, label) in listOf(SrExecutionMode.AUTO to "自动", SrExecutionMode.CPU to "CPU", SrExecutionMode.GPU to "GPU")) {
                    FilterChip(settings.mode == mode, { session.settings.value = settings.copy(mode = mode) }, { Text(label) }, enabled = session.unavailableReason == null)
                }
            }
            if (settings.mode == SrExecutionMode.AUTO) ReaderSettingSwitch("GPU 不可用时使用 CPU", settings.cpuFallback) {
                session.settings.value = settings.copy(cpuFallback = it)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = manage, enabled = session.models != null) { Text("模型管理") }
    }
}
