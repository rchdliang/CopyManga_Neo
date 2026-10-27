package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Native operations required by the original UI. Hosts provide real platform adapters. */
interface UiPlatform {
    val backDispatcher: UiBackDispatcher
    /** 默认沿用图片库的封面解码，仅由指定平台覆盖。 */
    val coverImageQuality: CoverImageQuality? get() = null
    /** Completion feedback for platform operations handled without a system share surface. */
    val toastMessages: Flow<String> get() = emptyFlow()
    suspend fun shareText(title: String, text: String): Result<Unit>
    fun formatDate(epochMilliseconds: Long): String

    /** Android must override with decorFitsSystemWindows = false. */
    fun edgeToEdgeDialogProperties(
        dismissOnBackPress: Boolean,
        dismissOnClickOutside: Boolean
    ): DialogProperties = DialogProperties(
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        usePlatformDefaultWidth = false
    )

    /** Keep platform-owned confirmation surfaces in the selected application palette. */
    @Composable
    fun ExternalUiThemeEffect(colors: ColorScheme) {}

    /** Null means that the OS does not expose dynamic colors; the app palette is retained. */
    @Composable
    fun dynamicColorScheme(darkTheme: Boolean): ColorScheme? = null
}

data class UiBackEvent(val progress: Float, val fromLeft: Boolean)

/** Last enabled handler wins; a predictive gesture pins that handler until commit/cancel. */
class UiBackDispatcher {
    private class Entry(
        val enabled: () -> Boolean,
        val onStarted: (UiBackEvent) -> Unit,
        val onProgressed: (UiBackEvent) -> Unit,
        val onCancelled: () -> Unit,
        val handler: () -> Boolean
    )
    private val entries = mutableStateListOf<Entry>()
    private var gestureEntry: Entry? = null
    var isGestureInProgress by mutableStateOf(false)
        private set
    val hasEnabledHandlers: Boolean get() = entries.any { it.enabled() }

    fun register(
        enabled: () -> Boolean = { true },
        onStarted: (UiBackEvent) -> Unit = {},
        onProgressed: (UiBackEvent) -> Unit = {},
        onCancelled: () -> Unit = {},
        handler: () -> Boolean
    ): () -> Unit {
        val entry = Entry(enabled, onStarted, onProgressed, onCancelled, handler)
        entries += entry
        return {
            entries.remove(entry)
            if (gestureEntry === entry) {
                gestureEntry = null
                entry.onCancelled()
                // Keep the gesture consumed; never commit it against a different screen.
            }
        }
    }

    fun dispatchBack(): Boolean = entries.toList().asReversed().any { it.enabled() && it.handler() }

    fun startGesture(event: UiBackEvent) {
        cancelGesture()
        isGestureInProgress = true
        gestureEntry = entries.lastOrNull { it.enabled() }
        gestureEntry?.onStarted?.invoke(event)
    }

    fun progressGesture(event: UiBackEvent) {
        if (isGestureInProgress) gestureEntry?.onProgressed?.invoke(event)
    }

    fun cancelGesture() {
        val entry = gestureEntry
        gestureEntry = null
        val wasStarted = isGestureInProgress
        isGestureInProgress = false
        if (wasStarted) entry?.onCancelled?.invoke()
    }

    fun commitGesture(): Boolean {
        if (!isGestureInProgress) return false
        val entry = gestureEntry
        gestureEntry = null
        isGestureInProgress = false
        if (entry == null) return true
        if (entry in entries && entry.enabled()) entry.handler() else entry.onCancelled()
        return true
    }
}

val LocalUiPlatform = staticCompositionLocalOf<UiPlatform?> { null }

/** Outgoing animated destinations must never consume input for the visible destination. */
internal val LocalRouteActive = staticCompositionLocalOf { true }

@Composable
fun BackHandler(
    enabled: Boolean = true,
    onBackStarted: (UiBackEvent) -> Unit = {},
    onBackProgressed: (UiBackEvent) -> Unit = {},
    onBackCancelled: () -> Unit = {},
    onBack: () -> Unit
) {
    if (LocalInspectionMode.current) return
    val dispatcher = requireNotNull(LocalUiPlatform.current) {
        "Provide UiPlatform at the application root before rendering shared screens."
    }.backDispatcher
    val currentEnabled by rememberUpdatedState(enabled && LocalRouteActive.current)
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnStarted by rememberUpdatedState(onBackStarted)
    val currentOnProgressed by rememberUpdatedState(onBackProgressed)
    val currentOnCancelled by rememberUpdatedState(onBackCancelled)
    DisposableEffect(dispatcher) {
        val unregister = dispatcher.register(
            enabled = { currentEnabled },
            onStarted = { currentOnStarted(it) },
            onProgressed = { currentOnProgressed(it) },
            onCancelled = { currentOnCancelled() }
        ) {
            currentOnBack()
            true
        }
        onDispose(unregister)
    }
}

@Composable
fun formattedReadingDate(epochMilliseconds: Long): String {
    val platform = LocalUiPlatform.current
    if (platform != null) return platform.formatDate(epochMilliseconds)
    check(LocalInspectionMode.current) { "Provide UiPlatform to format reading-history dates." }
    return kotlin.time.Instant.fromEpochMilliseconds(epochMilliseconds).toString().take(10)
}

/** Common console logging retains diagnostics on every target without Android Log dependencies. */
internal object UiLog {
    fun d(tag: String, message: String, error: Throwable? = null) = write("D", tag, message, error)
    fun w(tag: String, message: String, error: Throwable? = null) = write("W", tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = write("E", tag, message, error)

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        println("$level/$tag: $message")
        error?.let { println(it.stackTraceToString()) }
    }
}

/** Full-window dialog configuration; platform hosts retain native edge-to-edge behavior. */
@Composable
fun edgeToEdgeDialogProperties(
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = false
): DialogProperties = LocalUiPlatform.current?.edgeToEdgeDialogProperties(
    dismissOnBackPress, dismissOnClickOutside
) ?: DialogProperties(
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = dismissOnClickOutside,
    usePlatformDefaultWidth = false
)
