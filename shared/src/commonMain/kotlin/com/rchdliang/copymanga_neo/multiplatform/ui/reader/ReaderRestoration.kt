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
import kotlinx.coroutines.delay
import androidx.compose.foundation.selection.selectableGroup
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import kotlin.math.roundToInt

internal enum class ReaderRestoreMode(val label: String) {
    OFF("关"), RESTORE("Restore"), SOFT("RestoreSoft")
}

internal enum class ReaderRestoreLevel { S, M, L, VL, UL }

internal data class ReaderRestoreSelection(val mode: ReaderRestoreMode, val level: ReaderRestoreLevel = ReaderRestoreLevel.M) {
    val modelId: Int get() = (mode.ordinal - 1) * 5 + level.ordinal
    val pack: String get() = (if (mode == ReaderRestoreMode.RESTORE) "restore_" else "soft_") + level.name.lowercase() + ".akv"
}

internal fun ReaderToolsSettings.restoreSelection(): ReaderRestoreSelection {
    val mode = ReaderRestoreMode.entries.firstOrNull { it.name == restorationMode } ?: ReaderRestoreMode.OFF
    val rememberedMode = if (mode == ReaderRestoreMode.OFF) restorationLastMode else mode.name
    val value = if (rememberedMode == ReaderRestoreMode.SOFT.name) restoreSoftLevel else restoreLevel
    return ReaderRestoreSelection(mode, ReaderRestoreLevel.entries.firstOrNull { it.name == value } ?: ReaderRestoreLevel.M)
}

internal fun ReaderToolsSettings.withRestoreMode(mode: ReaderRestoreMode) = copy(
    restorationMode = mode.name,
    restorationLastMode = if (mode == ReaderRestoreMode.OFF) restorationLastMode else mode.name
)

internal fun ReaderToolsSettings.withRestoreLevel(level: ReaderRestoreLevel): ReaderToolsSettings =
    if (restorationMode == ReaderRestoreMode.SOFT.name ||
        (restorationMode == ReaderRestoreMode.OFF.name && restorationLastMode == ReaderRestoreMode.SOFT.name)) {
        copy(restoreSoftLevel = level.name)
    } else copy(restoreLevel = level.name)

internal enum class ReaderRestoreStage { ORIGINAL, PROCESSING, READY, UNAVAILABLE }
internal data class ReaderRestoreStatus(val stage: ReaderRestoreStage, val text: String)
internal data class ReaderRestoreOutput(val image: ImageBitmap, val elapsedMillis: Long, val cacheHit: Boolean)
internal class ReaderRestoreUnavailable(message: String) : Exception(message)

/** 首版只处理有界的单张静态图，不改变输入尺寸，不把长图偷偷缩小。 */
internal fun readerRestoreSizeAllowed(width: Int, height: Int): Boolean =
    width in 1..16384 && height in 1..16384 && width.toLong() * height <= 4_194_304L

internal interface ReaderRestoreEngine {
    val unavailableReason: String?
    suspend fun restore(source: Image, selection: ReaderRestoreSelection, pageKey: String): ReaderRestoreOutput
    fun clear()
    fun close()
}

internal class UnsupportedReaderRestoreEngine(override val unavailableReason: String) : ReaderRestoreEngine {
    override suspend fun restore(source: Image, selection: ReaderRestoreSelection, pageKey: String): ReaderRestoreOutput =
        throw ReaderRestoreUnavailable(unavailableReason)
    override fun clear() = Unit
    override fun close() = Unit
}

@Composable
internal expect fun rememberReaderRestoreEngine(): ReaderRestoreEngine

internal val LocalReaderRestoreEngine = staticCompositionLocalOf<ReaderRestoreEngine?> { null }
internal val LocalReaderRestoreSelection = staticCompositionLocalOf { ReaderRestoreSelection(ReaderRestoreMode.OFF) }
internal val LocalReaderRestoreStatus = staticCompositionLocalOf<((String, ReaderRestoreStatus) -> Unit)?> { null }

/** 原图先显示；请求失效后拒收旧结果。翻页与切换模型不会修改原图、下载或历史记录。 */
@Composable
internal fun readerRestorePainter(original: Painter, source: Image, pageKey: String, active: Boolean): Painter {
    val engine = LocalReaderRestoreEngine.current
    val selection = LocalReaderRestoreSelection.current
    val mode = selection.mode
    val report = LocalReaderRestoreStatus.current
    var output by remember(engine, source, pageKey, selection) { mutableStateOf<ReaderRestoreOutput?>(null) }
    var status by remember(engine, source, pageKey, selection) {
        mutableStateOf(ReaderRestoreStatus(ReaderRestoreStage.ORIGINAL, "原图"))
    }
    LaunchedEffect(engine, source, pageKey, selection, active) {
        if (!active || mode == ReaderRestoreMode.OFF || engine == null) return@LaunchedEffect
        output = null
        engine.unavailableReason?.let {
            status = ReaderRestoreStatus(ReaderRestoreStage.UNAVAILABLE, it)
            return@LaunchedEffect
        }
        status = ReaderRestoreStatus(ReaderRestoreStage.PROCESSING, "修复中，先显示原图")
        try {
            // 拖动档位时合并短时间内的请求，只处理最后选中的模型。
            delay(150)
            val result = engine.restore(source, selection, pageKey)
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
    DisposableEffect(pageKey, source, engine, selection, active) {
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


/** 模式与档位直接作用于阅读画面；两种模式的档位由调用者分别持久化。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReaderRestoreControls(
    selection: ReaderRestoreSelection,
    unavailableReason: String?,
    status: String?,
    onMode: (ReaderRestoreMode) -> Unit,
    onLevel: (ReaderRestoreLevel) -> Unit,
    enabled: Boolean = true,
) {
    Text("画质修复", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        ReaderRestoreMode.entries.forEachIndexed { index, option ->
            ToggleButton(
                checked = selection.mode == option,
                onCheckedChange = { if (it && selection.mode != option) onMode(option) },
                enabled = option == ReaderRestoreMode.OFF || (enabled && unavailableReason == null),
                modifier = Modifier.weight(when (option) { ReaderRestoreMode.OFF -> .7f; ReaderRestoreMode.RESTORE -> 1f; ReaderRestoreMode.SOFT -> 1.3f })
                    .testTag("reader-restore-${option.name}"),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    2 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
            ) { Text(option.label, color = LocalContentColor.current, maxLines = 1) }
        }
    }
    val canAdjust = enabled && selection.mode != ReaderRestoreMode.OFF && unavailableReason == null
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("模型档位", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(selection.level.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
    }
    Slider(
        value = selection.level.ordinal.toFloat(),
        onValueChange = {
            val level = ReaderRestoreLevel.entries[it.roundToInt().coerceIn(0, 4)]
            if (level != selection.level) onLevel(level)
        },
        valueRange = 0f..4f, steps = 3, enabled = canAdjust,
        modifier = Modifier.fillMaxWidth().testTag("reader-restore-level")
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        ReaderRestoreLevel.entries.forEach { level ->
            Text(level.name, color = if (canAdjust && selection.level == level) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
    val message = unavailableReason ?: if (!enabled) "返回图片页后可调节" else status
    message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
}

@Composable
internal fun ReaderRestoreAdjustmentPanel(
    selection: ReaderRestoreSelection,
    unavailableReason: String?,
    status: String?,
    onMode: (ReaderRestoreMode) -> Unit,
    onLevel: (ReaderRestoreLevel) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ReaderRestoreControls(selection, unavailableReason, status, onMode, onLevel)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("拖动画面查看细节", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onClose, modifier = Modifier.testTag("reader-restore-finish")) {
                    Text("完成", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
