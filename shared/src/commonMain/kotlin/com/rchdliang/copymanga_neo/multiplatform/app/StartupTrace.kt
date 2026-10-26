package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import kotlin.time.TimeSource

/** 启动探针由宿主注入；发布版本使用空实现，不采样时钟、不添加绘制监听。 */
interface StartupTrace {
    val enabled: Boolean get() = false
    fun mark(phase: String, durationMillis: Long? = null) {}
    fun palette(screen: String, primary: ULong, surface: ULong) {}
    /** 同线程同步片段，Debug 宿主同时记录墙钟时间和线程 CPU 时间。 */
    fun begin(phase: String): (() -> Unit)? = null

    object None : StartupTrace
}

val LocalStartupTrace = staticCompositionLocalOf<StartupTrace> { StartupTrace.None }

internal inline fun <T> StartupTrace.measureSync(phase: String, block: () -> T): T {
    val finish = begin(phase)
    return try { block() } finally { finish?.invoke() }
}

/** 独立阶段耗时与从进程启动起的累计耗时分别记录。 */
internal suspend fun <T> StartupTrace.measure(phase: String, block: suspend () -> T): T {
    if (!enabled) return block()
    val start = TimeSource.Monotonic.markNow()
    return block().also { mark(phase, start.elapsedNow().inWholeMilliseconds) }
}

/** 记录 Compose 绘制回调完成，不把它冒充 GPU 呈现或图片全部加载完成。 */
fun Modifier.startupDraw(trace: StartupTrace, phase: String): Modifier =
    if (!trace.enabled) this else drawWithContent {
        val finish = trace.begin("${phase}_work")
        drawContent()
        finish?.invoke()
        trace.mark(phase)
    }

/** 包含子树的测量/放置；嵌套阶段不可相加。发布版本不插入额外布局节点。 */
fun Modifier.startupLayout(trace: StartupTrace, phase: String): Modifier =
    if (!trace.enabled) this else layout { measurable, constraints ->
        val placeable = trace.measureSync("${phase}_measure") { measurable.measure(constraints) }
        layout(placeable.width, placeable.height) {
            trace.measureSync("${phase}_place") { placeable.place(0, 0) }
        }
    }
