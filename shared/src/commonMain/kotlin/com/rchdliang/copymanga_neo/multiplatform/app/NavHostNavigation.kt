package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Private saved-instance state only. Never pass this payload to browser navigation/history. */
@Serializable
internal data class SavedNavigation(val tab: MainTab, val stack: List<AppRoute>)
internal data class MainTabBackMotion(val source: MainTab, val fromLeft: Boolean)
@Serializable
internal data object MainRouteKey
@Serializable
internal data class ContentRouteKey(val id: String)

/**
 * NavController is the sole live stack. The restored list is a one-shot seed, cleared after graph
 * initialization; the registry holds payloads, not navigation order. Existing AppRoute IDs remain
 * model/saveable-state keys even when the framework generates new back-stack entry IDs on restore.
 */
internal class NavHostNavigation(saved: SavedNavigation = SavedNavigation(MainTab.HOME, emptyList())) : AppNavigation {
    override var tab by mutableStateOf(saved.tab)
        private set
    var homePreviewProgress by mutableStateOf<Float?>(null)
        private set
    var homeBackMotion by mutableStateOf<MainTabBackMotion?>(null)
        private set
    private var tabAtGestureStart: MainTab? = null
    private var seed: List<AppRoute>? = saved.stack
    private val payloads = mutableStateMapOf<String, AppRoute>().apply { putAll(saved.stack.associateBy { it.id }) }
    private var controller: NavHostController? = null
    var ready by mutableStateOf(false)
        private set

    fun attach(controller: NavHostController) {
        if (ready) { check(this.controller === controller); return }
        this.controller = controller
        // Called once after NavHost sets its graph. No destination effects run before ready.
        val restoredIds = controller.currentBackStack.value.mapNotNull(::routeId)
        if (restoredIds.isEmpty()) {
            seed.orEmpty().forEach { controller.navigate(ContentRouteKey(it.id)) }
        } else {
            // rememberNavController restored its own entries: never replay a second stack.
            restoredIds.forEach(::payload)
        }
        seed = null
        ready = true
    }

    /** Snapshot state read also invalidates cleanup for visits opened/popped within one frame. */
    val payloadIds: Set<String> get() = payloads.keys.toSet()

    fun payload(id: String): AppRoute = checkNotNull(payloads[id]) { "Missing private route payload: $id" }
    fun routeId(entry: NavBackStackEntry): String? =
        if (entry.destination.hasRoute<ContentRouteKey>()) entry.toRoute<ContentRouteKey>().id else null
    fun stack(): List<AppRoute> = seed ?: controller?.currentBackStack?.value.orEmpty()
        .mapNotNull(::routeId).map(::payload)
    fun snapshot() = SavedNavigation(tab, stack())

    fun startTabBack(progress: Float, fromLeft: Boolean, previewEnabled: Boolean) {
        tabAtGestureStart = tab
        homeBackMotion = if (previewEnabled) MainTabBackMotion(tab, fromLeft) else null
        homePreviewProgress = if (previewEnabled) progress.coerceIn(0f, 1f) else null
    }

    fun progressTabBack(progress: Float) {
        if (homePreviewProgress != null && tabAtGestureStart == tab) {
            homePreviewProgress = progress.coerceIn(0f, 1f)
        }
    }

    fun cancelTabBack() {
        homePreviewProgress = null
        tabAtGestureStart = null
    }

    fun finishTabMotion(motion: MainTabBackMotion) {
        if (homeBackMotion == motion && homePreviewProgress == null) homeBackMotion = null
    }

    fun finishTabBack() {
        val sameTab = tabAtGestureStart == null || tabAtGestureStart == tab
        cancelTabBack()
        if (sameTab && controller?.previousBackStackEntry == null) back()
    }

    override fun open(route: AppRoute) {
        check(ready) { "Navigation is not restored yet" }
        check(route.id !in payloads) { "Each visit must have a distinct route ID" }
        homePreviewProgress = null
        homeBackMotion = null
        payloads[route.id] = route
        try { checkNotNull(controller).navigate(ContentRouteKey(route.id)) }
        catch (error: Throwable) { payloads.remove(route.id); throw error }
    }
    override fun back(): Boolean {
        if (!ready) return false
        homePreviewProgress = null
        val nav = checkNotNull(controller)
        return when {
            nav.previousBackStackEntry != null -> nav.popBackStack()
            tab != MainTab.HOME -> { tab = MainTab.HOME; true }
            else -> false
        }
    }
    override fun select(tab: MainTab) {
        check(ready) { "Navigation is not restored yet" }
        homePreviewProgress = null
        homeBackMotion = null
        checkNotNull(controller).popBackStack<MainRouteKey>(inclusive = false)
        this.tab = tab
    }

    /** Call only with IDs retained by the live stack or unfinished framework transitions. */
    fun retain(ids: Set<String>): Set<String> {
        if (!ready) return emptySet()
        val removed = payloads.keys.toSet() - ids
        removed.forEach(payloads::remove)
        return removed
    }

    companion object {
        val saver = Saver<NavHostNavigation, String>(
            save = { Json.encodeToString(SavedNavigation.serializer(), it.snapshot()) },
            restore = { NavHostNavigation(Json.decodeFromString(SavedNavigation.serializer(), it)) }
        )
    }
}
