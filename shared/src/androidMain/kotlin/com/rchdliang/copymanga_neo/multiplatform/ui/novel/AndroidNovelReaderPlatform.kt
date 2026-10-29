package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.Html
import android.view.WindowManager
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Locale

/** Preserves the original Android novel-reader window and text-conversion behavior. */
class AndroidNovelReaderPlatform(private val context: Context) : NovelReaderPlatform {
    init {
        require(context.novelActivity() != null) {
            "AndroidNovelReaderPlatform requires an Activity or Activity-wrapping Context"
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun statusBarControlInsets(): WindowInsets = WindowInsets.statusBarsIgnoringVisibility

    override fun plainTextFromHtml(html: String): String =
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()

    override fun formatLineHeight(value: Float): String =
        String.format(context.resources.configuration.locales[0] ?: Locale.getDefault(), "%.2f 倍", value)

    override val supportsSystemFonts = true
    override suspend fun systemFonts() = AndroidNovelFonts.list()
    override fun systemFontFamily(id: String, weight: Int) = AndroidNovelFonts.family(id, weight)

    @Composable
    override fun ReaderWindow(keepScreenOn: Boolean, controlsVisible: Boolean, lightBars: Boolean) {
        val activity = context.novelActivity()
        DisposableEffect(activity) {
            val window = activity?.window
            val previous = window?.attributes?.softInputMode
            // 输入仅影响弹层；阅读区不随键盘重排，退出阅读器时再恢复原窗口策略。
            window?.setSoftInputMode(
                ((previous ?: 0) and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            )
            onDispose {
                if (previous != null) window.setSoftInputMode(previous)
            }
        }
        DisposableEffect(activity, keepScreenOn) {
            val window = activity?.window
            val wasAwake = ((window?.attributes?.flags
                ?: 0) and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
            if (keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                if (wasAwake) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        DisposableEffect(activity) {
            val controller =
                activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            val status = controller?.isAppearanceLightStatusBars
            val navigation = controller?.isAppearanceLightNavigationBars
            val behavior = controller?.systemBarsBehavior
            val insets = activity?.window?.decorView?.let(ViewCompat::getRootWindowInsets)
            val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
            val navigationVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
            onDispose {
                if (statusVisible) controller?.show(WindowInsetsCompat.Type.statusBars())
                else controller?.hide(WindowInsetsCompat.Type.statusBars())
                if (navigationVisible) controller?.show(WindowInsetsCompat.Type.navigationBars())
                else controller?.hide(WindowInsetsCompat.Type.navigationBars())
                behavior?.let { controller.systemBarsBehavior = it }
                status?.let { controller.isAppearanceLightStatusBars = it }
                navigation?.let { controller.isAppearanceLightNavigationBars = it }
            }
        }
        LaunchedEffect(activity, controlsVisible, lightBars) {
            activity?.window?.let {
                WindowCompat.getInsetsController(it, it.decorView).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (controlsVisible) show(WindowInsetsCompat.Type.systemBars())
                    else hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }
}

private tailrec fun Context.novelActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.novelActivity()
    else -> null
}
