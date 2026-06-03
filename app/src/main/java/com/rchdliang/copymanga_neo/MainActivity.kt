package com.rchdliang.copymanga_neo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rchdliang.copymanga_neo.ui.detail.DetailScreen
import com.rchdliang.copymanga_neo.ui.downloads.DownloadsScreen
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
    object Main : Screen("main")
    object Home : Screen("home")
    object Detail : Screen("detail/{pathWord}") {
        fun createRoute(pathWord: String) = "detail/$pathWord"
    }

    object Reader :
        Screen("reader/{comicPathWord}/{chapterPathWord}/{chapterName}/{groupPathWord}") {
        fun createRoute(
            comicPathWord: String,
            chapterPathWord: String,
            chapterName: String,
            groupPathWord: String = "default"
        ) =
            "reader/$comicPathWord/$chapterPathWord/${chapterName.replace("/", "_")}/$groupPathWord"
    }

    object Search : Screen("search")
    object Favorites : Screen("favorites")
    object Downloads : Screen("downloads")
    object Profile : Screen("profile")
}

@ExperimentalMaterial3ExpressiveApi
@Composable
fun CopyManga_NeoApp(
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Main.route,
        ) {
            // 主界面（包含底部导航栏）
            composable(Screen.Main.route) {
                Box(modifier = Modifier.statusBarsPadding()) {
                    val isLoggedIn by mainViewModel.isLoggedIn.collectAsState()
                    MainScreen(
                        isLoggedIn = isLoggedIn,
                        onNavigateToDetail = { pathWord ->
                            navController.navigate(Screen.Detail.createRoute(pathWord))
                        },
                        onNavigateToSearch = {
                            navController.navigate(Screen.Search.route)
                        }
                    )
                }
            }

            // 搜索页（全屏覆盖，无底部导航）
            composable(Screen.Search.route) {
                SearchScreen(
                    onComicClick = { pathWord ->
                        navController.navigate(Screen.Detail.createRoute(pathWord))
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 详情页（脱离底部导航栏）
            composable(
                route = Screen.Detail.route,
                arguments = listOf(navArgument("pathWord") { type = NavType.StringType })
            ) { backStackEntry ->
                val pathWord = backStackEntry.arguments?.getString("pathWord") ?: return@composable
                Box(modifier = Modifier.statusBarsPadding()) {
                    DetailScreen(
                        pathWord = pathWord,
                        onChapterClick = { chapterPathWord, comicPathWord, chapterName, groupPathWord ->
                            navController.navigate(
                                Screen.Reader.createRoute(
                                    comicPathWord,
                                    chapterPathWord,
                                    chapterName,
                                    groupPathWord
                                )
                            )
                        },
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToProfile = { navController.popBackStack() },
                        onDownloadClick = { /* TODO: 下载功能 */ }
                    )
                }
            }

            // 阅读器（全屏，无状态栏Padding，由 ReaderScreen 自己控制系统UI）
            composable(
                route = Screen.Reader.route,
                arguments = listOf(
                    navArgument("comicPathWord") { type = NavType.StringType },
                    navArgument("chapterPathWord") { type = NavType.StringType },
                    navArgument("chapterName") { type = NavType.StringType },
                    navArgument("groupPathWord") {
                        type = NavType.StringType; defaultValue = "default"
                    }
                )
            ) { backStackEntry ->
                val comicPathWord = backStackEntry.arguments?.getString("comicPathWord") ?: ""
                val chapterPathWord = backStackEntry.arguments?.getString("chapterPathWord") ?: ""
                val chapterName = backStackEntry.arguments?.getString("chapterName") ?: ""
                val groupPathWord =
                    backStackEntry.arguments?.getString("groupPathWord") ?: "default"
                ReaderScreen(
                    chapterPathWord = chapterPathWord,
                    comicPathWord = comicPathWord,
                    chapterName = chapterName,
                    groupPathWord = groupPathWord,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@ExperimentalMaterial3ExpressiveApi
@Composable
fun MainScreen(
    isLoggedIn: Boolean,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSearch: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val currentDestination = when (currentRoute) {
        Screen.Home.route -> AppDestinations.HOME
        Screen.Downloads.route -> AppDestinations.DOWNLOADS
        Screen.Favorites.route -> AppDestinations.BOOKSHELF
        Screen.Profile.route -> AppDestinations.PROFILE
        else -> AppDestinations.HOME
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                item(
                    icon = {
                        Icon(
                            imageVector = if (currentDestination == destination) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) },
                    selected = currentDestination == destination,
                    onClick = {
                        val targetRoute = when (destination) {
                            AppDestinations.HOME -> Screen.Home.route
                            AppDestinations.DOWNLOADS -> Screen.Downloads.route
                            AppDestinations.BOOKSHELF -> {
                                if (!isLoggedIn) Screen.Profile.route
                                else Screen.Favorites.route
                            }
                            AppDestinations.PROFILE -> Screen.Profile.route
                        }
                        if (currentRoute != targetRoute) {
                            navController.navigate(targetRoute) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        },
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            shortNavigationBarContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            navigationBarContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            navigationRailContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            navigationDrawerContainerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onComicClick = onNavigateToDetail,
                        onNavigateToSearch = onNavigateToSearch
                    )
                }
                composable(Screen.Downloads.route) {
                    DownloadsScreen(onComicClick = onNavigateToDetail)
                }
                composable(Screen.Favorites.route) {
                    FavoritesScreen(
                        onComicClick = onNavigateToDetail,
                        onNavigateToProfile = {
                            navController.navigate(Screen.Profile.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
                composable(Screen.Profile.route) {
                    ProfileScreen()
                }
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
    DOWNLOADS("下载", Icons.Filled.Download, Icons.Outlined.Download),
    BOOKSHELF("书架", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    PROFILE("账号", Icons.Filled.AccountBox, Icons.Outlined.AccountBox),
}