package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BackHandler
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackDispatcher

internal data class RouteHostDiagnostics(
    val stack: Set<String>, val visible: Set<String>, val composed: Set<String>,
    val payloads: Set<String>, val completingUncomposed: Set<String>
)

/** Framework owns outer route order and seeking; existing tab/model ownership remains with the app. */
@Composable
internal fun AppRouteNavHost(
    navigation: NavHostNavigation,
    backDispatcher: UiBackDispatcher,
    onRetainRoutes: (Set<String>) -> Unit,
    onControllerReady: (NavHostController) -> Unit = {},
    onDiagnostics: (RouteHostDiagnostics) -> Unit = {},
    content: @Composable AnimatedContentScope.(AppRoute) -> Unit
) {
    val controller = rememberNavController()
    val current by controller.currentBackStackEntryAsState()
    val stack by controller.currentBackStack.collectAsState()
    val visible by controller.visibleEntries.collectAsState()
    val composed = remember(controller) { mutableStateMapOf<String, String>() }
    val currentRetain by rememberUpdatedState(onRetainRoutes)
    val currentDiagnostics by rememberUpdatedState(onDiagnostics)
    // Registered before the stable framework bridge: non-home main tabs are the root fallback.
    BackHandler(enabled = navigation.ready && controller.previousBackStackEntry == null && navigation.tab != MainTab.HOME) {
        navigation.back()
    }
    val bridge = remember(controller, backDispatcher) {
        NavHostBackBridge(backDispatcher) { controller.currentBackStackEntry?.id }
    }
    DisposableEffect(bridge) { onDispose { bridge.dispose() } }
    LaunchedEffect(controller, navigation) { navigation.attach(controller) }
    SideEffect { onControllerReady(controller) }

    val retainedIds = (stack + visible).mapNotNull(navigation::routeId).toSet() + composed.values
    val initialized = navigation.ready
    val registeredIds = navigation.payloadIds
    LaunchedEffect(initialized, retainedIds, registeredIds) {
        // Capture readiness: the initial empty-stack effect must not prune the restored payloads
        // if attach() runs earlier in this same effect batch. Read live entries at cleanup time.
        if (initialized) {
            val liveEntries = controller.currentBackStack.value
            val visibleEntries = controller.visibleEntries.value
            val liveEntryIds = liveEntries.map { it.id }.toSet()
            val orphaned = visibleEntries.filter { it.id !in liveEntryIds && it.id !in composed }
            currentDiagnostics(RouteHostDiagnostics(
                liveEntries.mapNotNull(navigation::routeId).toSet(),
                visibleEntries.mapNotNull(navigation::routeId).toSet(),
                composed.values.toSet(), navigation.payloadIds,
                orphaned.mapNotNull(navigation::routeId).toSet()
            ))
            // A push+pop before any frame can leave a popped entry in visibleEntries although
            // AnimatedContent never acquired it, so it cannot report animation completion.
            // Only acknowledge popped AND uncomposed entries. A displayed outgoing/preview
            // entry remains retained until its actual disposal; live stack entries are untouched.
            val navigator = controller.navigatorProvider.getNavigator<ComposeNavigator>("composable")
            orphaned.forEach(navigator::onTransitionComplete)
            currentRetain(
                (controller.currentBackStack.value + controller.visibleEntries.value)
                    .mapNotNull(navigation::routeId).toSet() + composed.values
            )
        }
    }
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides bridge) {
        NavHost(
            navController = controller,
            startDestination = MainRouteKey,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(150, easing = EaseInOut)) },
            exitTransition = { fadeOut(tween(100, easing = EaseInOut)) },
            popEnterTransition = { fadeIn(tween(150, easing = EaseInOut)) },
            popExitTransition = { fadeOut(tween(0)) },
            predictivePopExitTransition = { fadeOut(tween(0)) }
        ) {
            composable<MainRouteKey> { entry ->
                if (navigation.ready) {
                    RouteLifecycleScope(active = entry.id == current?.id) {
                        content(AppRoute("main", id = "main"))
                    }
                }
            }
            composable<ContentRouteKey> { entry ->
                val id = entry.toRoute<ContentRouteKey>().id
                DisposableEffect(entry) {
                    composed[entry.id] = id
                    onDispose { composed.remove(entry.id) }
                }
                if (navigation.ready) {
                    RouteLifecycleScope(active = entry.id == current?.id) { content(navigation.payload(id)) }
                }
            }
        }
    }
}
