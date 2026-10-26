package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.Image
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal enum class ReaderRestoreMode(val label: String, val asset: String) {
    OFF("关闭", ""),
    RESTORE("Restore S", "Anime4K_Restore_CNN_S.glsl"),
    SOFT("Restore Soft S", "Anime4K_Restore_CNN_Soft_S.glsl"),
}

internal enum class ReaderQualityChoice(val label: String, val restore: ReaderRestoreMode) {
    ORIGINAL("原图", ReaderRestoreMode.OFF),
    SHARPEN("锐化", ReaderRestoreMode.OFF),
    RESTORE("Restore S", ReaderRestoreMode.RESTORE),
    SOFT("Restore Soft S", ReaderRestoreMode.SOFT),
}

internal enum class ReaderRestoreStage { ORIGINAL, PROCESSING, READY, UNAVAILABLE }
internal data class ReaderRestoreStatus(val stage: ReaderRestoreStage, val text: String)
internal data class ReaderRestoreOutput(val image: ImageBitmap, val elapsedMillis: Long, val cacheHit: Boolean)
internal class ReaderRestoreUnavailable(message: String) : Exception(message)

/** 首版只处理有界的单张静态图，不改变输入尺寸，不把长图偷偷缩小。 */
internal fun readerRestoreSizeAllowed(width: Int, height: Int): Boolean =
    width in 1..4096 && height in 1..4096 && width.toLong() * height <= 2_097_152L

internal interface ReaderRestoreEngine {
    val unavailableReason: String?
    suspend fun restore(source: Image, mode: ReaderRestoreMode, pageKey: String): ReaderRestoreOutput
    fun clear()
    fun close()
}

internal class UnsupportedReaderRestoreEngine(override val unavailableReason: String) : ReaderRestoreEngine {
    override suspend fun restore(source: Image, mode: ReaderRestoreMode, pageKey: String): ReaderRestoreOutput =
        throw ReaderRestoreUnavailable(unavailableReason)
    override fun clear() = Unit
    override fun close() = Unit
}

@Composable
internal expect fun rememberReaderRestoreEngine(): ReaderRestoreEngine

internal val LocalReaderRestoreEngine = staticCompositionLocalOf<ReaderRestoreEngine?> { null }
internal val LocalReaderRestoreMode = staticCompositionLocalOf { ReaderRestoreMode.OFF }
internal val LocalReaderRestoreStatus = staticCompositionLocalOf<((String, ReaderRestoreStatus) -> Unit)?> { null }

/** 原图先显示；请求失效后拒收旧结果。翻页与切换模型不会修改原图、下载或历史记录。 */
@Composable
internal fun readerRestorePainter(original: Painter, source: Image, pageKey: String, active: Boolean): Painter {
    val engine = LocalReaderRestoreEngine.current
    val mode = LocalReaderRestoreMode.current
    val report = LocalReaderRestoreStatus.current
    var output by remember(engine, source, pageKey, mode) { mutableStateOf<ReaderRestoreOutput?>(null) }
    var status by remember(engine, source, pageKey, mode) {
        mutableStateOf(ReaderRestoreStatus(ReaderRestoreStage.ORIGINAL, "原图"))
    }
    LaunchedEffect(engine, source, pageKey, mode, active) {
        if (!active || mode == ReaderRestoreMode.OFF || engine == null) return@LaunchedEffect
        output = null
        engine.unavailableReason?.let {
            status = ReaderRestoreStatus(ReaderRestoreStage.UNAVAILABLE, it)
            return@LaunchedEffect
        }
        status = ReaderRestoreStatus(ReaderRestoreStage.PROCESSING, "修复中，先显示原图")
        try {
            val result = engine.restore(source, mode, pageKey)
            currentCoroutineContext().ensureActive()
            output = result
            status = ReaderRestoreStatus(ReaderRestoreStage.READY,
                if (result.cacheHit) "已修复 · 使用缓存" else "已修复 · ${result.elapsedMillis} ms")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ReaderRestoreUnavailable) {
            status = ReaderRestoreStatus(ReaderRestoreStage.UNAVAILABLE, error.message ?: "修复不可用，显示原图")
        } catch (_: Exception) {
            status = ReaderRestoreStatus(ReaderRestoreStage.UNAVAILABLE, "修复失败，显示原图")
        }
    }
    // 在组合中读取，确保测量或异步状态改变时重新报告。
    val currentStatus = status
    DisposableEffect(pageKey, source, engine, mode, active) {
        onDispose {
            if (active && mode != ReaderRestoreMode.OFF) {
                report?.invoke(pageKey, ReaderRestoreStatus(ReaderRestoreStage.ORIGINAL, "等待原图"))
            }
        }
    }
    SideEffect { if (active && mode != ReaderRestoreMode.OFF) report?.invoke(pageKey, currentStatus) }
    val result = output
    return if (active && mode != ReaderRestoreMode.OFF && result != null) {
        remember(result.image) { BitmapPainter(result.image) }
    } else original
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderRestoreControls(
    mode: ReaderRestoreMode,
    unavailableReason: String?,
    status: String?,
    onMode: (ReaderRestoreMode) -> Unit,
    onCompare: () -> Unit,
    compareAvailable: Boolean = true,
) {
    Text("画质修复", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    Text(unavailableReason ?: "实验 · 保持图片尺寸，修复时不叠加锐化。",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReaderRestoreMode.entries.forEach { option ->
            FilterChip(selected = mode == option, onClick = { onMode(option) },
                enabled = option == ReaderRestoreMode.OFF || unavailableReason == null,
                label = { Text(option.label, color = MaterialTheme.colorScheme.onSurface) },
                modifier = Modifier.testTag("reader-restore-${option.name}"))
        }
    }
    status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    TextButton(onClick = onCompare, enabled = compareAvailable) {
        Text("对比原图、锐化与修复", color = if (compareAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderQualityComparison(
    choice: ReaderQualityChoice,
    restoreAvailable: Boolean,
    onChoice: (ReaderQualityChoice) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderQualityChoice.entries.forEach { option ->
                    FilterChip(selected = choice == option, onClick = { onChoice(option) },
                        enabled = option.restore == ReaderRestoreMode.OFF || restoreAvailable,
                        label = { Text(option.label, color = MaterialTheme.colorScheme.onSurface) },
                        modifier = Modifier.testTag("reader-quality-${option.name}"))
                }
            }
            TextButton(onClick = onClose) { Text("完成对比", color = MaterialTheme.colorScheme.primary) }
        }
    }
}
