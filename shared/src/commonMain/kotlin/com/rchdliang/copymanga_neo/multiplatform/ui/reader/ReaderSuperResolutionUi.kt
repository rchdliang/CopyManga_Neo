package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.rchdliang.copymanga_neo.domain.reading.*
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

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

internal data class ReaderSrImage(val painter: Painter, val onDrawn: () -> Unit = {})
internal fun Modifier.readerSrDrawn(image: ReaderSrImage): Modifier = drawWithContent {
    drawContent()
    image.onDrawn()
}

@Composable
internal fun readerSrPainter(session: ReaderSrSession?, pageId: String, revision: Int, original: Painter): ReaderSrImage {
    if (session == null) return ReaderSrImage(original)
    LaunchedEffect(session, pageId, revision) { session.originalReady(pageId, revision) }
    val settings by session.settings.collectAsState()
    val frame by session.frame.collectAsState()
    val candidate = matchingSrFrame(frame, pageId, revision, settings)?.takeIf(session::isCurrentFrame)
        ?: return ReaderSrImage(original)
    val ready = candidate.status as? SrStatus.Ready ?: return ReaderSrImage(original)
    return key(candidate.renderId, ready.artifact) {
        val context = LocalPlatformContext.current
        val artifact = ready.artifact
        val request = remember(context, artifact) {
            ImageRequest.Builder(context).data(artifact.png)
                .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()
        }
        val enhanced = rememberAsyncImagePainter(request)
        val decoded by enhanced.state.collectAsState()
        val owner = remember { Any() }
        DisposableEffect(session, owner) { onDispose { session.imageDetached(owner) } }
        // A cached output or successful inference is insufficient: the same decoded output must be drawn.
        val success = (decoded as? AsyncImagePainter.State.Success)?.takeIf { it.result.request.data === artifact.png }
        val failed = (decoded as? AsyncImagePainter.State.Error)?.result?.request?.data === artifact.png
        val shown = when { success != null -> SrRenderedImage.ENHANCED; failed -> SrRenderedImage.DECODE_FAILED; else -> SrRenderedImage.ORIGINAL }
        ReaderSrImage(success?.painter ?: original) { session.imageDrawn(owner, candidate, artifact, shown) }
    }
}

@Composable
internal fun readerSrPresentation(session: ReaderSrSession, pageId: String?, singlePage: Boolean): SrPresentation {
    val settings by session.settings.collectAsState()
    val frame by session.frame.collectAsState()
    val receipt by session.presentationReceipt.collectAsState()
    val failed by session.preparationFailedPage.collectAsState()
    val originals by session.originalsReady.collectAsState()
    val model = session.models?.state?.collectAsState()?.value ?: SrModelState.Ready
    return readerSrPresentation(pageId, singlePage, settings, model, session.unavailableReason,
        frame?.takeIf(session::isCurrentFrame), receipt, failed == pageId && pageId != null,
        originalReady = pageId in originals)
}

@Composable
internal fun readerSrWaitSeconds(presentation: SrPresentation, requestId: Long?): Long {
    var elapsed by remember(presentation.pageId, presentation.phase, requestId) { mutableStateOf(0L) }
    LaunchedEffect(presentation.pageId, presentation.phase, requestId) {
        if (presentation.phase == SrPresentationPhase.PROCESSING) {
            val start = TimeSource.Monotonic.markNow()
            while (true) { delay(1000); elapsed = start.elapsedNow().inWholeSeconds }
        }
    }
    return elapsed
}

internal fun SrPresentation.shortLabel(waitSeconds: Long = 0): String? = when (phase) {
    SrPresentationPhase.ORIGINAL -> null
    SrPresentationPhase.MODEL_NOT_READY -> "AI 未应用 · 模型未就绪"
    SrPresentationPhase.WAITING_ORIGINAL -> "AI 未应用 · 等待原图"
    SrPresentationPhase.WAITING -> "AI 未应用 · 等待处理"
    SrPresentationPhase.PROCESSING -> "AI 未应用 · 处理中" + if (waitSeconds > 0) " ${waitSeconds}s" else ""
    SrPresentationPhase.DECODING -> "AI 未应用 · 载入中"
    SrPresentationPhase.ENHANCED -> "AI 已应用"
    SrPresentationPhase.FAILED -> "AI 未应用 · 处理失败"
    SrPresentationPhase.UNAVAILABLE -> "AI 未应用 · 此页不可用"
    SrPresentationPhase.SPREAD_ORIGINAL -> "双页 · AI 未应用"
}
internal fun SrPresentation.backendLabel(): String? = backend?.takeIf { it != SrExecutionMode.AUTO }?.name?.let {
    it + if (cpuFallback) " · 自动回退" else ""
}

@Composable
internal fun ReaderSuperResolutionOptions(session: ReaderSrSession, singlePage: Boolean = true,
    presentation: SrPresentation? = null, pageNumber: Int? = null, waitSeconds: Long = 0) {
    val settings by session.settings.collectAsState()
    val modelState = session.models?.state?.collectAsState()?.value ?: SrModelState.Ready
    val current = presentation ?: readerSrPresentation(session, session.frame.collectAsState().value?.pageId, singlePage)
    ReaderSrModelControls(session) { enable, manage, progress ->
        ReaderSrSettingsBody(settings, session.unavailableReason, modelState == SrModelState.Deleting,
            onEnable = { if (it) enable(settings.mode) else session.settings.value = settings.copy(enabled = false) },
            onSettings = { session.settings.value = it },
            currentPage = {
                current.shortLabel(waitSeconds)?.let { Text((pageNumber?.let { "第 $it 页 · " } ?: "") + it,
                    style = MaterialTheme.typography.bodyMedium) }
                current.backendLabel()?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                current.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }, model = {
                val value = when(modelState) {
                    SrModelState.Ready -> "已下载"
                    SrModelState.Missing -> "未下载 · ${modelMiB(session.models?.downloadBytes ?: 0)}"
                    SrModelState.Preparing -> "正在准备"
                    SrModelState.Deleting -> "正在删除"
                    is SrModelState.Downloading -> "正在下载"
                    SrModelState.Cancelled -> "已取消"
                    is SrModelState.Failed -> "操作失败"
                }
                ReaderSettingsLink(Icons.Outlined.Storage, "模型管理", value, enabled = session.models != null, onClick = manage)
                progress()
            })
    }
}
