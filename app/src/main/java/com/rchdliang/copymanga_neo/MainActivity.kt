package com.rchdliang.copymanga_neo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rchdliang.copymanga_neo.ui.detail.DetailScreen
import com.rchdliang.copymanga_neo.ui.favorites.FavoritesScreen
import com.rchdliang.copymanga_neo.ui.home.HomeScreen
import com.rchdliang.copymanga_neo.ui.profile.ProfileScreen
import com.rchdliang.copymanga_neo.ui.reader.ReaderScreen
import com.rchdliang.copymanga_neo.ui.search.SearchScreen
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme
import dagger.hilt.android.AndroidEntryPoint

@ExperimentalMaterial3ExpressiveApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isDarkTheme =
            (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        enableEdgeToEdge(
            navigationBarStyle = if (isDarkTheme) {
                SystemBarStyle.dark(Color.Transparent.toArgb())
            } else {
                SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
            }
        )
        setContent {
            CopyManga_NeoTheme {
                CopyManga_NeoApp()
            }
        }
    }
}

/**
 * 导航路由定义
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Detail : Screen("detail/{pathWord}") {
        fun createRoute(pathWord: String) = "detail/$pathWord"
    }

    object Reader : Screen("reader/{comicPathWord}/{chapterPathWord}/{chapterName}") {
        fun createRoute(comicPathWord: String, chapterPathWord: String, chapterName: String) =
            "reader/$comicPathWord/$chapterPathWord/${chapterName.replace("/", "_")}"
    }

    object Search : Screen("search")
    object Favorites : Screen("favorites")
    object Profile : Screen("profile")
}

@ExperimentalMaterial3ExpressiveApi
@PreviewScreenSizes
@Composable
fun CopyManga_NeoApp(
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 根据当前路由确定底部导航选中项
    val currentDestination = when {
        currentRoute == Screen.Home.route -> AppDestinations.HOME
        currentRoute == Screen.Search.route -> AppDestinations.SEARCH
        currentRoute == Screen.Favorites.route -> AppDestinations.BOOKSHELF
        currentRoute == Screen.Profile.route -> AppDestinations.PROFILE
        currentRoute?.startsWith(Screen.Detail.route) == true -> AppDestinations.HOME
        currentRoute?.startsWith(Screen.Reader.route) == true -> AppDestinations.HOME
        else -> AppDestinations.HOME
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                item(
                    icon = {
                        Icon(
                            imageVector = if (currentDestination == destination) {
                                destination.selectedIcon
                            } else {
                                destination.unselectedIcon
                            },
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) },
                    selected = currentDestination == destination,
                    onClick = {
                        when (destination) {
                            AppDestinations.HOME -> {
                                if (currentRoute != Screen.Home.route) {
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }

                            AppDestinations.SEARCH -> {
                                if (currentRoute != Screen.Search.route) {
                                    navController.navigate(Screen.Search.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }

                            AppDestinations.BOOKSHELF -> {
                                if (currentRoute != Screen.Favorites.route) {
                                    navController.navigate(Screen.Favorites.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }

                            AppDestinations.PROFILE -> {
                                if (currentRoute != Screen.Profile.route) {
                                    navController.navigate(Screen.Profile.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                    }
                )
            }
        },
//        containerColor = MaterialTheme.colorScheme.surfaceContainer,
//        contentColor = MaterialTheme.colorScheme.surface
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            shortNavigationBarContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            navigationBarContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            navigationRailContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            navigationDrawerContainerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onComicClick = { pathWord ->
                        navController.navigate(Screen.Detail.createRoute(pathWord))
                    }
                )
            }

            composable(
                route = Screen.Detail.route,
                arguments = listOf(
                    navArgument("pathWord") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val pathWord =
                    backStackEntry.arguments?.getString("pathWord") ?: return@composable
                DetailScreen(
                    pathWord = pathWord,
                    onChapterClick = { chapterPathWord, comicPathWord, chapterName ->
                        navController.navigate(
                            Screen.Reader.createRoute(
                                comicPathWord,
                                chapterPathWord,
                                chapterName
                            )
                        )
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = Screen.Reader.route,
                arguments = listOf(
                    navArgument("comicPathWord") { type = NavType.StringType },
                    navArgument("chapterPathWord") { type = NavType.StringType },
                    navArgument("chapterName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val comicPathWord = backStackEntry.arguments?.getString("comicPathWord") ?: ""
                val chapterPathWord =
                    backStackEntry.arguments?.getString("chapterPathWord") ?: ""
                val chapterName = backStackEntry.arguments?.getString("chapterName") ?: ""
                ReaderScreen(
                    chapterPathWord = chapterPathWord,
                    comicPathWord = comicPathWord,
                    chapterName = chapterName,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    onComicClick = { pathWord ->
                        navController.navigate(Screen.Detail.createRoute(pathWord))
                    }
                )
            }

            composable(Screen.Favorites.route) {
                FavoritesScreen(
                    onComicClick = { pathWord ->
                        navController.navigate(Screen.Detail.createRoute(pathWord))
                    }
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen()
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME("首页", Icons.Filled.Home, Icons.Outlined.Home),
    SEARCH("搜索", Icons.Filled.Search, Icons.Outlined.Search),
    BOOKSHELF("书架", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    PROFILE("账号", Icons.Filled.AccountBox, Icons.Outlined.AccountBox),
}
