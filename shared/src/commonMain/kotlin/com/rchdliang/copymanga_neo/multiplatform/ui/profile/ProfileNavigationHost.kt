package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rchdliang.copymanga_neo.multiplatform.app.PredictiveNavigationHost
import com.rchdliang.copymanga_neo.multiplatform.app.RouteLifecycleScope

internal enum class ProfilePage {
    MAIN, HISTORY, READING, DOWNLOADS, BLACKLIST, NETWORK, NETWORK_ADVANCED,
    ABOUT, THIRD_PARTY_LIBRARIES, DATA, DIAGNOSTIC_LOGS, LOCAL_LIBRARY
}

@Composable
internal fun ProfileNavigationHost(
    content: @Composable (page: ProfilePage, open: (ProfilePage) -> Unit, back: () -> Unit) -> Unit
) {
    val controller = rememberNavController()
    val current by controller.currentBackStackEntryAsState()
    val latestContent by rememberUpdatedState(content)
    val pageState = rememberSaveableStateHolder()
    PredictiveNavigationHost(controller) {
        NavHost(
            navController = controller,
            startDestination = ProfilePage.MAIN.name,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(150, easing = EaseInOut)) },
            exitTransition = { fadeOut(tween(100, easing = EaseInOut)) }
        ) {
            ProfilePage.entries.forEach { page ->
                composable(page.name) { entry ->
                    RouteLifecycleScope(active = current?.id == entry.id) {
                        pageState.SaveableStateProvider(page.name) {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                                latestContent(page, { target ->
                                    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED && target != page) {
                                        if (target == ProfilePage.MAIN) controller.popBackStack(ProfilePage.MAIN.name, false)
                                        else controller.navigate(target.name) { launchSingleTop = true }
                                    }
                                }, {
                                    if (controller.currentBackStackEntry?.id == entry.id && controller.previousBackStackEntry != null) {
                                        controller.popBackStack()
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}
