package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavHostController
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalPredictiveBackEnabled
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalUiPlatform

/** A nested graph can own back only while its parent destination is active. */
@Composable
internal fun PredictiveNavigationHost(controller: NavHostController, content: @Composable () -> Unit) {
    val dispatcher = requireNotNull(LocalUiPlatform.current).backDispatcher
    val active by rememberUpdatedState(LocalRouteActive.current)
    val previewEnabled by rememberUpdatedState(LocalPredictiveBackEnabled.current)
    val bridge = remember(controller, dispatcher) {
        NavHostBackBridge(dispatcher, { previewEnabled }, isActive = { active }) {
            controller.currentBackStackEntry?.id
        }
    }
    DisposableEffect(bridge) { onDispose { bridge.dispose() } }
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides bridge, content = content)
}
