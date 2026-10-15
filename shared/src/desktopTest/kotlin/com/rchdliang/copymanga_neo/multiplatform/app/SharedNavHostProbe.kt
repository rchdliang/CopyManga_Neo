package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackDispatcher
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rchdliang.copymanga_neo.multiplatform.ui.components.ComicSharedTransitionProvider
import com.rchdliang.copymanga_neo.multiplatform.ui.components.rememberComicSharedTransitionState

/**
 * Compile-only compatibility probe, deliberately not mounted by any production entry point.
 * Uses the existing cover scope provider and a single NavController-owned stack. No repository,
 * reader, platform effect or production ViewModel is created by these placeholder destinations.
 */
@Composable
internal fun SharedNavHostProbe(
    backDispatcher: UiBackDispatcher,
    onControllerReady: (NavHostController) -> Unit = {}
) {
    val controller = rememberNavController()
    val bridge = remember(controller, backDispatcher) {
        NavHostBackBridge(backDispatcher) { controller.currentBackStackEntry?.id }
    }
    DisposableEffect(bridge) { onDispose { bridge.dispose() } }
    SideEffect { onControllerReady(controller) }
    val covers = rememberComicSharedTransitionState()
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides bridge) {
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        val sharedScope = this
        NavHost(
            navController = controller,
            modifier = Modifier.fillMaxSize(),
            startDestination = "probe/main",
            enterTransition = { fadeIn(tween(150)) },
            exitTransition = { fadeOut(tween(100)) },
            popEnterTransition = { fadeIn(tween(150)) },
            popExitTransition = { fadeOut(tween(0)) },
            predictivePopExitTransition = { fadeOut(tween(0)) },
        ) {
            composable("probe/main") {
                ComicSharedTransitionProvider(sharedScope, this, covers) {
                    Button(onClick = { controller.navigate("probe/detail") }) { Text("Detail") }
                }
            }
            composable("probe/detail") {
                ComicSharedTransitionProvider(sharedScope, this, covers) {
                    Column {
                        Button(onClick = { controller.navigate("probe/reader") }) { Text("Reader") }
                        Button(onClick = { controller.popBackStack() }) { Text("Back") }
                    }
                }
            }
            composable("probe/reader") {
                ComicSharedTransitionProvider(sharedScope, this, covers) {
                    Button(onClick = { controller.popBackStack() }) { Text("Back") }
                }
            }
        }
    }
}
}
