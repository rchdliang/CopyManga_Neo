package com.rchdliang.copymanga_neo.multiplatform

import android.os.Build
import android.view.ViewTreeObserver
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** 只在阅读器活动/重新取得焦点时隐藏；退出恢复进入前的窗口状态。 */
@Composable
internal fun ReaderImmersiveSystemBars(window: Window?) {
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(window, view, owner) {
        if (window == null) return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val insets = ViewCompat.getRootWindowInsets(window.decorView)
        val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars())
        val navigationVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars())
        val behavior = controller.systemBarsBehavior
        val contrast = if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced else null
        fun immerse() {
            if (ViewCompat.getRootWindowInsets(window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true) return
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        immerse()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) immerse() }
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (focused && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) immerse()
        }
        var imeWasVisible = ViewCompat.getRootWindowInsets(window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        val layout = ViewTreeObserver.OnGlobalLayoutListener {
            val imeVisible = ViewCompat.getRootWindowInsets(window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            if (imeWasVisible && !imeVisible && view.hasWindowFocus() && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) immerse()
            imeWasVisible = imeVisible
        }
        owner.lifecycle.addObserver(observer)
        view.viewTreeObserver.addOnWindowFocusChangeListener(focus)
        view.viewTreeObserver.addOnGlobalLayoutListener(layout)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(focus)
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnGlobalLayoutListener(layout)
            statusVisible?.let { if (it) controller.show(WindowInsetsCompat.Type.statusBars()) else controller.hide(WindowInsetsCompat.Type.statusBars()) }
            navigationVisible?.let { if (it) controller.show(WindowInsetsCompat.Type.navigationBars()) else controller.hide(WindowInsetsCompat.Type.navigationBars()) }
            controller.systemBarsBehavior = behavior
            if (Build.VERSION.SDK_INT >= 29 && contrast != null) window.isNavigationBarContrastEnforced = contrast
        }
    }
}
