package com.rchdliang.copymanga_neo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailColors
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.SubcomposeAsyncImage
import com.rchdliang.copymanga_neo.ui.components.AppToastHost
import com.rchdliang.copymanga_neo.ui.detail.DetailScreen
import com.rchdliang.copymanga_neo.ui.downloads.DownloadsScreen
import com.rchdliang.copymanga_neo.ui.favorites.FavoritesScreen
import com.rchdliang.copymanga_neo.ui.home.HomeScreen
import com.rchdliang.copymanga_neo.ui.profile.ProfileScreen
import com.rchdliang.copymanga_neo.ui.reader.ReaderScreen
import com.rchdliang.copymanga_neo.ui.search.SearchScreen
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val windowWidthDp = with(density) { containerSize.width.toDp() }
    val isExpanded = windowWidthDp >= 840.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isExpanded) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface)
    ) {
        AppToastHost {
            NavHost(
                navController = navController,
                startDestination = Screen.Main.route,
            ) {
            // 主界面（包含底部导航栏）
            composable(Screen.Main.route) {
                Box(modifier = Modifier.statusBarsPadding()) {
                    val isLoggedIn by mainViewModel.isLoggedIn.collectAsStateWithLifecycle()
                    val avatar by mainViewModel.avatar.collectAsStateWithLifecycle()
                    MainScreen(
                        isLoggedIn = isLoggedIn,
                        avatar = avatar,
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
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
                        onNavigateToProfile = { navController.popBackStack() }
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
            } // NavHost
        } // AppToastHost
    }
}

@ExperimentalMaterial3ExpressiveApi
@Composable
fun MainScreen(
    isLoggedIn: Boolean,
    avatar: String?,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSearch: () -> Unit
) {
    android.util.Log.d("MainScreen", "Recompose: isLoggedIn=$isLoggedIn")
    val pagerState = rememberPagerState(pageCount = { AppDestinations.entries.size })
    val scope = rememberCoroutineScope()
    var pageNavigationJob by remember { mutableStateOf<Job?>(null) }
    val currentIsLoggedIn by rememberUpdatedState(isLoggedIn)

    fun targetPageFor(destination: AppDestinations): Int {
        return when (destination) {
            AppDestinations.BOOKSHELF -> {
                val target =
                    if (currentIsLoggedIn) AppDestinations.BOOKSHELF.ordinal else AppDestinations.PROFILE.ordinal
                android.util.Log.d(
                    "MainScreen",
                    "targetPageFor(BOOKSHELF): isLoggedIn=$currentIsLoggedIn -> page=$target (BOOKSHELF=${AppDestinations.BOOKSHELF.ordinal}, PROFILE=${AppDestinations.PROFILE.ordinal})"
                )
                target
            }

            else -> destination.ordinal
        }
    }

    fun animateToPage(targetPage: Int) {
        if (targetPage == pagerState.targetPage) return

        pageNavigationJob?.cancel()
        pageNavigationJob = scope.launch {
            val distance = abs(targetPage - pagerState.currentPage).coerceAtLeast(2)
            pagerState.animateScrollToPage(
                page = targetPage,
                animationSpec = tween(
                    durationMillis = 100 * distance + 100,
                    easing = EaseInOut
                )
            )
        }
    }

    fun animateToDestination(destination: AppDestinations) {
        animateToPage(targetPageFor(destination))
    }

    val currentDestination = AppDestinations.entries.getOrElse(pagerState.targetPage) {
        AppDestinations.HOME
    }

    BackHandler(
        enabled = pagerState.targetPage != AppDestinations.HOME.ordinal ||
                pagerState.settledPage != AppDestinations.HOME.ordinal
    ) {
        animateToPage(AppDestinations.HOME.ordinal)
    }

    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val windowWidthDp = with(density) { containerSize.width.toDp() }
    val isExpanded = windowWidthDp >= 840.dp

    if (isExpanded) {
        WideScreenLayout(
            isLoggedIn = isLoggedIn,
            avatar = avatar,
            pagerState = pagerState,
            currentDestination = currentDestination,
            onDestinationSelected = ::animateToDestination,
            onNavigateToDetail = onNavigateToDetail,
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToProfile = { animateToPage(AppDestinations.PROFILE.ordinal) }
        )
    } else {
        NavigationSuiteScaffold(
            layoutType = NavigationSuiteType.NavigationBar,
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
                            animateToDestination(destination)
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
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                MainPagerContent(
                    pagerState = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    onNavigateToDetail = onNavigateToDetail,
                    onNavigateToSearch = onNavigateToSearch,
                    onNavigateToProfile = { animateToPage(AppDestinations.PROFILE.ordinal) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WideScreenLayout(
    isLoggedIn: Boolean,
    avatar: String?,
    pagerState: PagerState,
    currentDestination: AppDestinations,
    onDestinationSelected: (AppDestinations) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val railState = rememberWideNavigationRailState(
        initialValue = WideNavigationRailValue.Collapsed
    )
    val scope = rememberCoroutineScope()
    val railWindowInsets = WindowInsets.safeDrawing.only(
        WindowInsetsSides.Vertical + WindowInsetsSides.Start
    )

    Row(modifier = Modifier.fillMaxSize()) {
        WideNavigationRail(
            state = railState,
            windowInsets = railWindowInsets,
            colors = WideNavigationRailColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modalContainerColor = WideNavigationRailDefaults.colors().modalContainerColor,
                modalScrimColor = WideNavigationRailDefaults.colors().modalScrimColor,
                modalContentColor = WideNavigationRailDefaults.colors().modalContentColor
            ),
            header = {
                Column(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .padding(start = 24.dp)
                ) {
                    SubcomposeAsyncImage(
                        model = if (isLoggedIn) avatar else null,
                        contentDescription = "用户头像",
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onNavigateToProfile() },
                        loading = {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxSize()
                            )
                        },
                        error = {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    IconButton(
                        modifier = Modifier
                            .semantics {
                                stateDescription =
                                    if (railState.targetValue == WideNavigationRailValue.Expanded)
                                        "已展开" else "已折叠"
                            },
                        onClick = {
                            scope.launch {
                                if (railState.targetValue == WideNavigationRailValue.Expanded)
                                    railState.collapse()
                                else
                                    railState.expand()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (railState.targetValue == WideNavigationRailValue.Expanded)
                                Icons.AutoMirrored.Filled.MenuOpen
                            else
                                Icons.Filled.Menu,
                            contentDescription = if (railState.targetValue == WideNavigationRailValue.Expanded)
                                "折叠侧栏" else "展开侧栏"
                        )
                    }
                }
            }
        ) {
            AppDestinations.entries.forEach { destination ->
                WideNavigationRailItem(
                    railExpanded = railState.targetValue == WideNavigationRailValue.Expanded,
                    selected = currentDestination == destination,
                    onClick = {
                        onDestinationSelected(destination)
                    },
                    icon = {
                        Icon(
                            imageVector = if (currentDestination == destination)
                                destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) }
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            MainPagerContent(
                pagerState = pagerState,
                modifier = Modifier.fillMaxSize(),
                onNavigateToDetail = onNavigateToDetail,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToProfile = onNavigateToProfile
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainPagerContent(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    HorizontalPager(
        state = pagerState,
        userScrollEnabled = false,
        modifier = modifier
    ) { page ->
        when (AppDestinations.entries[page]) {
            AppDestinations.HOME -> HomeScreen(
                onComicClick = onNavigateToDetail,
                onNavigateToSearch = onNavigateToSearch
            )

            AppDestinations.DOWNLOADS -> DownloadsScreen(onComicClick = onNavigateToDetail)

            AppDestinations.BOOKSHELF -> FavoritesScreen(
                onComicClick = onNavigateToDetail,
                onNavigateToProfile = onNavigateToProfile
            )

            AppDestinations.PROFILE -> ProfileScreen()
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
    PROFILE("我的", Icons.Filled.AccountBox, Icons.Outlined.AccountBox),
}
