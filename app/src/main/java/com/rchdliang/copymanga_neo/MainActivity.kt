package com.rchdliang.copymanga_neo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.SaveableStateHolder
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.SubcomposeAsyncImage
import com.google.gson.Gson
import com.rchdliang.copymanga_neo.data.download.DownloadNotifications
import com.rchdliang.copymanga_neo.data.export.ComicExportService
import com.rchdliang.copymanga_neo.domain.model.AppThemeMode
import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import com.rchdliang.copymanga_neo.domain.model.QueryType
import com.rchdliang.copymanga_neo.domain.model.Theme
import com.rchdliang.copymanga_neo.domain.repository.ComicExportRepository
import com.rchdliang.copymanga_neo.domain.repository.RiskControlRepository
import com.rchdliang.copymanga_neo.ui.components.AppToastHost
import com.rchdliang.copymanga_neo.ui.components.ComicSharedTransitionProvider
import com.rchdliang.copymanga_neo.ui.components.rememberComicSharedTransitionState
import com.rchdliang.copymanga_neo.ui.detail.DetailScreen
import com.rchdliang.copymanga_neo.ui.downloads.DownloadNotificationPermission
import com.rchdliang.copymanga_neo.ui.downloads.DownloadsScreen
import com.rchdliang.copymanga_neo.ui.explore.ComicExploreScreen
import com.rchdliang.copymanga_neo.ui.export.ComicExportDialogs
import com.rchdliang.copymanga_neo.ui.export.ComicExportViewModel
import com.rchdliang.copymanga_neo.ui.favorites.FavoritesScreen
import com.rchdliang.copymanga_neo.ui.favorites.FavoritesViewModel
import com.rchdliang.copymanga_neo.ui.home.HomeScreen
import com.rchdliang.copymanga_neo.ui.novel.NovelImagePositionDebugDialog
import com.rchdliang.copymanga_neo.ui.novel.NovelScreen
import com.rchdliang.copymanga_neo.ui.profile.ProfileScreen
import com.rchdliang.copymanga_neo.ui.reader.ReaderScreen
import com.rchdliang.copymanga_neo.ui.search.SearchScreen
import com.rchdliang.copymanga_neo.ui.theme.CopyManga_NeoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var riskControl: RiskControlRepository

    @Inject
    lateinit var exports: ComicExportRepository
    private var openDownloads by mutableStateOf(false)
    private var retryExportId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retryExportId =
            if (savedInstanceState != null) savedInstanceState.getString("retryExportId")
            else intent.takeIf { it.action == ComicExportService.RETRY }
                ?.getStringExtra(ComicExportService.TASK_ID)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                exports.messages.collect { message ->
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
            }
        }
        riskControl.resetForAppLaunch(isFreshActivity = savedInstanceState == null)
        openDownloads = savedInstanceState?.getBoolean("openDownloads")
            ?: (intent.action == DownloadNotifications.OPEN_DOWNLOADS)
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
            val mainViewModel: MainViewModel = hiltViewModel()
            val settings by mainViewModel.appSettings.collectAsStateWithLifecycle()
            val preferences = settings ?: return@setContent
            val darkTheme = when (preferences.themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            SideEffect {
                val style = if (darkTheme) SystemBarStyle.dark(Color.Transparent.toArgb())
                else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            com.rchdliang.copymanga_neo.ui.components.MetadataTextHost(preferences.simplifyChinese, mainViewModel.chineseDictionary) {
                CopyManga_NeoTheme(darkTheme = darkTheme) {
                    val exportViewModel: ComicExportViewModel = hiltViewModel()
                    val exportState by exports.state.collectAsStateWithLifecycle()
                    LaunchedEffect(retryExportId) {
                        retryExportId?.let { exportViewModel.retry(it); retryExportId = null }
                    }
                    val presentation: com.rchdliang.copymanga_neo.ui.components.ComicPresentationViewModel =
                        hiltViewModel()
                    val novelPreview: com.rchdliang.copymanga_neo.ui.components.NovelPreviewViewModel = hiltViewModel()
                    com.rchdliang.copymanga_neo.ui.components.ComicPresentationHost(presentation, novelPreview) {
                        CopyManga_NeoApp(
                            mainViewModel = mainViewModel,
                            openDownloads = openDownloads,
                            hasExports = exportState.running,
                            onDownloadsOpened = { openDownloads = false })
                    }
                    ComicExportDialogs(exportViewModel)
                    if (BuildConfig.DEBUG) {
                        val samples by mainViewModel.novelImagePositionDebugSamples.collectAsStateWithLifecycle()
                        samples.firstOrNull()?.let { sample ->
                            androidx.compose.runtime.key(sample.id) {
                                NovelImagePositionDebugDialog(sample) {
                                    mainViewModel.dismissNovelImagePositionDebugSample(sample.id)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        riskControl.resumeIfExpired()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == DownloadNotifications.OPEN_DOWNLOADS) openDownloads = true
        if (intent.action == ComicExportService.RETRY)
            retryExportId = intent.getStringExtra(ComicExportService.TASK_ID)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("openDownloads", openDownloads)
        outState.putString("retryExportId", retryExportId)
        super.onSaveInstanceState(outState)
    }
}

/**
 * 导航路由定义
 */
sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Home : Screen("home")
    object Novel : Screen("novel?path={path}&resume={resume}") {
        fun createRoute(path: String? = null, resume: LocalReadingRecord? = null) =
            "novel?path=${Uri.encode(path.orEmpty())}&resume=${
                Uri.encode(resume?.let {
                    Gson().toJson(
                        it
                    )
                }.orEmpty())
            }"
    }

    object Detail : Screen("detail/{pathWord}") {
        fun createRoute(pathWord: String) = "detail/$pathWord"
    }

    object OriginalWork : Screen("original?originalPath={originalPath}&originalName={originalName}") {
        fun createRoute(path: String, name: String) =
            "original?originalPath=${Uri.encode(path)}&originalName=${Uri.encode(name)}"
    }

    object Reader :
        Screen("reader/{comicPathWord}/{chapterPathWord}/{chapterName}/{groupPathWord}?page={page}&fraction={fraction}") {
        fun createRoute(
            comicPathWord: String,
            chapterPathWord: String,
            chapterName: String,
            groupPathWord: String = "default",
            page: Int = -1,
            fraction: Double = 0.0
        ) =
            "reader/${Uri.encode(comicPathWord)}/${Uri.encode(chapterPathWord)}/${
                Uri.encode(
                    chapterName.ifBlank { "阅读章节" })
            }/${Uri.encode(groupPathWord)}?page=$page&fraction=$fraction"
    }

    object Search :
        Screen("search?themePathWord={themePathWord}&themeName={themeName}&query={query}&queryType={queryType}") {
        fun createRoute(theme: Theme? = null): String = if (theme == null) "search" else
            "search?themePathWord=${Uri.encode(theme.pathWord.orEmpty())}" +
                    "&themeName=${Uri.encode(theme.name)}"

        fun createAuthorRoute(name: String): String =
            "search?query=${Uri.encode(name)}&queryType=${QueryType.AUTHOR.value}"
    }

    object Explore :
        Screen("explore/{section}?theme={theme}&top={top}&audience={audience}&period={period}&topic={topic}&topicTitle={topicTitle}") {
        fun createRoute(
            section: String,
            theme: String? = null,
            top: String? = null,
            topic: String? = null,
            topicTitle: String? = null
        ): String {
            val filters = listOfNotNull(
                theme?.let { "theme=${Uri.encode(it)}" },
                top?.let { "top=${Uri.encode(it)}" },
                topic?.let { "topic=${Uri.encode(it)}" },
                topicTitle?.let { "topicTitle=${Uri.encode(it)}" })
            return "explore/$section" + if (filters.isEmpty()) "" else filters.joinToString(
                "&",
                prefix = "?"
            )
        }
    }

    object Favorites : Screen("favorites")
    object Downloads : Screen("downloads")
    object Profile : Screen("profile")
}

@ExperimentalMaterial3ExpressiveApi
@Composable
fun CopyManga_NeoApp(
    mainViewModel: MainViewModel = hiltViewModel(),
    openDownloads: Boolean = false,
    hasExports: Boolean = false,
    onDownloadsOpened: () -> Unit = {}
) {
    val navController = rememberNavController()
    var mainDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    val downloads by mainViewModel.downloadProgress.collectAsStateWithLifecycle()
    DownloadNotificationPermission(hasDownloads = downloads.isNotEmpty() || hasExports)
    LaunchedEffect(openDownloads) {
        if (openDownloads) {
            mainDestination = AppDestinations.DOWNLOADS
            navController.popBackStack(Screen.Main.route, inclusive = false)
            onDownloadsOpened()
        }
    }
    val comicSharedTransitionState = rememberComicSharedTransitionState()
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
            SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                val sharedTransitionScope = this
                NavHost(
                    navController = navController,
                    startDestination = Screen.Main.route,
                    enterTransition = {
                        fadeIn(tween(durationMillis = 150, easing = EaseInOut))
                    },
                    exitTransition = {
                        fadeOut(tween(durationMillis = 100, easing = EaseInOut))
                    },
                    popEnterTransition = {
                        fadeIn(tween(durationMillis = 150, easing = EaseInOut))
                    },
                    popExitTransition = {
                        fadeOut(tween(durationMillis = 0))
                    },
                    predictivePopExitTransition = {
                        // 所有页面返回时立即退场；封面仍由共享元素浮层独立绘制。
                        fadeOut(tween(durationMillis = 0))
                    },
                ) {
                    // 主界面（包含底部导航栏）
                    composable(Screen.Main.route) {
                        ComicSharedTransitionProvider(
                            sharedTransitionScope,
                            this,
                            comicSharedTransitionState
                        ) {
                            Box(modifier = Modifier.statusBarsPadding()) {
                                val isLoggedIn by mainViewModel.isLoggedIn.collectAsStateWithLifecycle()
                                val avatar by mainViewModel.avatar.collectAsStateWithLifecycle()
                                MainScreen(
                                    onNavigateToNovel = { path, resume ->
                                        navController.navigate(
                                            Screen.Novel.createRoute(
                                                path,
                                                resume
                                            )
                                        )
                                    },
                                    onReadComic = { path, chapter, name, group, page, fraction ->
                                        navController.navigate(
                                            Screen.Reader.createRoute(
                                                path,
                                                chapter,
                                                name,
                                                group,
                                                page,
                                                fraction
                                            )
                                        )
                                    },
                                    currentDestination = mainDestination,
                                    onDestinationSelected = { mainDestination = it },
                                    isLoggedIn = isLoggedIn,
                                    avatar = avatar,
                                    onNavigateToDetail = { pathWord ->
                                        navController.navigate(Screen.Detail.createRoute(pathWord))
                                    },
                                    onNavigateToSearch = {
                                        navController.navigate(Screen.Search.createRoute())
                                    },
                                    onNavigateToTopic = { pathWord, title ->
                                        navController.navigate(
                                            Screen.Explore.createRoute(
                                                "topics",
                                                topic = pathWord,
                                                topicTitle = title
                                            )
                                        )
                                    },
                                    onNavigateToExplore = { section ->
                                        navController.navigate(Screen.Explore.createRoute(section))
                                    }
                                )
                            }
                        }
                    }

                    // 小说页与漫画详情同级，独立覆盖底栏和宽屏侧栏。
                    composable(
                        route = Screen.Novel.route,
                        arguments = listOf(
                            navArgument("path") { type = NavType.StringType; defaultValue = "" },
                            navArgument("resume") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { entry ->
                        val initialPath =
                            entry.arguments?.getString("path")?.takeIf { it.isNotBlank() }
                        val initialResume = remember(entry) {
                            entry.arguments?.getString("resume")?.takeIf { it.isNotBlank() }?.let {
                                runCatching {
                                    Gson().fromJson(
                                        it,
                                        LocalReadingRecord::class.java
                                    )
                                }.getOrNull()
                            }
                        }
                        ComicSharedTransitionProvider(
                            sharedTransitionScope,
                            this,
                            comicSharedTransitionState
                        ) {
                            NovelScreen(
                                onBack = { navController.popBackStack() },
                                initialPath = initialPath, initialResume = initialResume
                            )
                        }
                    }

                    composable(
                        route = Screen.OriginalWork.route,
                        arguments = listOf(
                            navArgument("originalPath") { type = NavType.StringType },
                            navArgument("originalName") { type = NavType.StringType }
                        )
                    ) {
                        ComicSharedTransitionProvider(sharedTransitionScope, this, comicSharedTransitionState) {
                            com.rchdliang.copymanga_neo.ui.search.OriginalWorkScreen(
                                onComic = { navController.navigate(Screen.Detail.createRoute(it)) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }

                    // 搜索页（全屏覆盖，无底部导航）
                    composable(
                        route = Screen.Search.route,
                        arguments = listOf(
                            navArgument("themePathWord") {
                                type = NavType.StringType; nullable = true; defaultValue = null
                            },
                            navArgument("themeName") {
                                type = NavType.StringType; nullable = true; defaultValue = null
                            },
                            navArgument("query") {
                                type = NavType.StringType; nullable = true; defaultValue = null
                            },
                            navArgument("queryType") {
                                type = NavType.StringType; nullable = true; defaultValue = null
                            }
                        )
                    ) {
                        ComicSharedTransitionProvider(
                            sharedTransitionScope,
                            this,
                            comicSharedTransitionState
                        ) {
                            SearchScreen(
                                onComicClick = { pathWord ->
                                    navController.navigate(Screen.Detail.createRoute(pathWord))
                                },
                                onNavigateToExplore = { theme, top ->
                                    navController.navigate(
                                        Screen.Explore.createRoute(
                                            "filter",
                                            theme,
                                            top
                                        )
                                    )
                                },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }

                    composable(
                        route = Screen.Explore.route,
                        arguments = listOf(
                            navArgument("section") { type = NavType.StringType },
                            navArgument("theme") {
                                type = NavType.StringType; nullable = true; defaultValue = null
                            },
                            navArgument("top") {
                                type = NavType.StringType; nullable = true; defaultValue = null
                            },
                            navArgument("audience") {
                                type = NavType.StringType; nullable = true; defaultValue = null
                            },
                            navArgument("period") {
                                type = NavType.StringType; nullable = true; defaultValue = null
                            },
                            navArgument("topic") {
                                type = NavType.StringType; nullable = true; defaultValue = null
                            },
                            navArgument("topicTitle") {
                                type = NavType.StringType; nullable = true; defaultValue = null
                            }
                        )
                    ) {
                        ComicSharedTransitionProvider(
                            sharedTransitionScope,
                            this,
                            comicSharedTransitionState
                        ) {
                            ComicExploreScreen(
                                onComicClick = { pathWord ->
                                    navController.navigate(
                                        Screen.Detail.createRoute(
                                            pathWord
                                        )
                                    )
                                },
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }

                    // 详情页（脱离底部导航栏）
                    composable(
                        route = Screen.Detail.route,
                        popExitTransition = { fadeOut(tween(durationMillis = 0)) },
                        arguments = listOf(navArgument("pathWord") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val pathWord =
                            backStackEntry.arguments?.getString("pathWord") ?: return@composable
                        ComicSharedTransitionProvider(
                            sharedTransitionScope,
                            this,
                            comicSharedTransitionState
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                DetailScreen(
                                    pathWord = pathWord,
                                    onStartChapterClick = { chapter, comic, name, group, page ->
                                        navController.navigate(
                                            Screen.Reader.createRoute(
                                                comic,
                                                chapter,
                                                name,
                                                group,
                                                page = page
                                            )
                                        )
                                    },
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
                                    onNavigateToProfile = {
                                        mainDestination = AppDestinations.PROFILE
                                        navController.popBackStack(
                                            Screen.Main.route,
                                            inclusive = false
                                        )
                                    },
                                    onThemeClick = { theme ->
                                        navController.navigate(Screen.Search.createRoute(theme))
                                    },
                                    onOriginalWorkClick = { work ->
                                        work.pathWord?.takeIf(String::isNotBlank)?.let { path ->
                                            navController.navigate(Screen.OriginalWork.createRoute(path, work.name))
                                        }
                                    },
                                    onAuthorClick = { author ->
                                        navController.navigate(
                                            Screen.Search.createAuthorRoute(
                                                author.name
                                            )
                                        )
                                    }
                                )
                            }
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
                            },
                            navArgument("page") { type = NavType.IntType; defaultValue = -1 },
                            navArgument("fraction") {
                                type = NavType.StringType; defaultValue = "0.0"
                            }
                        )
                    ) { backStackEntry ->
                        val comicPathWord =
                            backStackEntry.arguments?.getString("comicPathWord") ?: ""
                        val chapterPathWord =
                            backStackEntry.arguments?.getString("chapterPathWord") ?: ""
                        val chapterName = backStackEntry.arguments?.getString("chapterName") ?: ""
                        val groupPathWord =
                            backStackEntry.arguments?.getString("groupPathWord") ?: "default"
                        ReaderScreen(
                            chapterPathWord = chapterPathWord,
                            comicPathWord = comicPathWord,
                            chapterName = chapterName,
                            groupPathWord = groupPathWord,
                            onNavigateBack = { navController.popBackStack() },
                            initialPage = backStackEntry.arguments?.getInt("page") ?: -1,
                            initialFraction = backStackEntry.arguments?.getString("fraction")
                                ?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..1.0 }
                                ?: 0.0
                        )
                    }
                } // NavHost
            } // SharedTransitionLayout
        } // AppToastHost
    }
}

@ExperimentalMaterial3ExpressiveApi
@Composable
fun MainScreen(
    isLoggedIn: Boolean,
    avatar: String?,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToExplore: (String) -> Unit,
    onNavigateToTopic: (String, String) -> Unit,
    currentDestination: AppDestinations,
    onDestinationSelected: (AppDestinations) -> Unit,
    favoritesViewModel: FavoritesViewModel = hiltViewModel(),
    onReadComic: (String, String, String, String, Int, Double) -> Unit = { _, _, _, _, _, _ -> },
    onNavigateToNovel: (String?, LocalReadingRecord?) -> Unit = { _, _ -> }
) {
    // 主界面创建共享书架状态，登录后即可在导航栏提示更新。
    val favoritesState by favoritesViewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        favoritesViewModel.onMainScreenResumed()
    }
    android.util.Log.d("MainScreen", "Recompose: isLoggedIn=$isLoggedIn")
    fun animateToDestination(destination: AppDestinations) {
        // 书架自行呈现登录状态；顶层导航不能用冷启动期间的瞬时 false 将用户改道到“我的”。
        onDestinationSelected(destination)
    }

    BackHandler(
        enabled = currentDestination != AppDestinations.HOME
    ) {
        onDestinationSelected(AppDestinations.HOME)
    }

    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val windowWidthDp = with(density) { containerSize.width.toDp() }
    val isExpanded = windowWidthDp >= 840.dp

    // 状态容器独立于手机/宽屏布局，尺寸变化后仍恢复各目的地的位置。
    val destinationStateHolder = rememberSaveableStateHolder()
    if (isExpanded) {
        WideScreenLayout(
            destinationStateHolder = destinationStateHolder,
            isLoggedIn = isLoggedIn,
            avatar = avatar,
            favoritesViewModel = favoritesViewModel,
            updatedComicCount = favoritesState.updatedComicCount,
            currentDestination = currentDestination,
            onDestinationSelected = ::animateToDestination,
            onNavigateToDetail = onNavigateToDetail,
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToExplore = onNavigateToExplore,
            onNavigateToTopic = onNavigateToTopic,
            onNavigateToProfile = { onDestinationSelected(AppDestinations.PROFILE) },
            onReadComic = onReadComic,
            onNavigateToNovel = onNavigateToNovel
        )
    } else {
        NavigationSuiteScaffold(
            layoutType = NavigationSuiteType.NavigationBar,
            navigationSuiteItems = {
                AppDestinations.entries.forEach { destination ->
                    item(
                        icon = {
                            DestinationIcon(
                                destination = destination,
                                selected = currentDestination == destination,
                                updatedComicCount = favoritesState.updatedComicCount
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
                MainDestinationContent(
                    saveableStateHolder = destinationStateHolder,
                    currentDestination = currentDestination,
                    favoritesViewModel = favoritesViewModel,
                    modifier = Modifier.fillMaxSize(),
                    onNavigateToDetail = onNavigateToDetail,
                    onNavigateToSearch = onNavigateToSearch,
                    onNavigateToExplore = onNavigateToExplore,
                    onNavigateToTopic = onNavigateToTopic,
                    onNavigateToProfile = { onDestinationSelected(AppDestinations.PROFILE) },
                    onNavigateToDownloads = { onDestinationSelected(AppDestinations.DOWNLOADS) },
                    onReadComic = onReadComic,
                    onNavigateToNovel = onNavigateToNovel
                )
            }
        }
    }
}

@Composable
private fun WideScreenLayout(
    destinationStateHolder: SaveableStateHolder,
    isLoggedIn: Boolean,
    avatar: String?,
    favoritesViewModel: FavoritesViewModel,
    updatedComicCount: Int,
    currentDestination: AppDestinations,
    onDestinationSelected: (AppDestinations) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToExplore: (String) -> Unit,
    onNavigateToTopic: (String, String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onReadComic: (String, String, String, String, Int, Double) -> Unit,
    onNavigateToNovel: (String?, LocalReadingRecord?) -> Unit
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
                        DestinationIcon(
                            destination = destination,
                            selected = currentDestination == destination,
                            updatedComicCount = updatedComicCount
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
            MainDestinationContent(
                saveableStateHolder = destinationStateHolder,
                currentDestination = currentDestination,
                favoritesViewModel = favoritesViewModel,
                modifier = Modifier.fillMaxSize(),
                onNavigateToDetail = onNavigateToDetail,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToExplore = onNavigateToExplore,
                onNavigateToTopic = onNavigateToTopic,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToDownloads = { onDestinationSelected(AppDestinations.DOWNLOADS) },
                onReadComic = onReadComic,
                onNavigateToNovel = onNavigateToNovel
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainDestinationContent(
    saveableStateHolder: SaveableStateHolder,
    currentDestination: AppDestinations,
    favoritesViewModel: FavoritesViewModel,
    modifier: Modifier = Modifier,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToExplore: (String) -> Unit,
    onNavigateToTopic: (String, String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onReadComic: (String, String, String, String, Int, Double) -> Unit,
    onNavigateToNovel: (String?, LocalReadingRecord?) -> Unit
) {
    // 顶层目的地必须直接过渡，避免 Pager 的跨页滚动短暂显示中间页面。
    AnimatedContent(
        targetState = currentDestination,
        modifier = modifier,
        transitionSpec = {
            val direction = if (targetState.ordinal > initialState.ordinal) {
                AnimatedContentTransitionScope.SlideDirection.Left
            } else {
                AnimatedContentTransitionScope.SlideDirection.Right
            }
            (slideIntoContainer(
                towards = direction,
                animationSpec = tween(durationMillis = 300, easing = EaseInOut)
            ) + fadeIn(
                animationSpec = tween(durationMillis = 300, easing = EaseInOut)
            )).togetherWith(
                slideOutOfContainer(
                    towards = direction,
                    animationSpec = tween(durationMillis = 300, easing = EaseInOut)
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 300, easing = EaseInOut)
                )
            )
        },
        label = "main destination transition"
    ) { destination ->
        saveableStateHolder.SaveableStateProvider(destination.name) {
            when (destination) {
                AppDestinations.HOME -> HomeScreen(
                    onNavigateToNovel = { onNavigateToNovel(it, null) },
                    onComicClick = onNavigateToDetail,
                    onNavigateToSearch = onNavigateToSearch,
                    onNavigateToExplore = onNavigateToExplore,
                    onNavigateToTopic = onNavigateToTopic,
                    onNavigateToDownloads = onNavigateToDownloads
                )

                AppDestinations.DOWNLOADS -> DownloadsScreen(
                    onComicClick = onNavigateToDetail,
                    onNovelClick = { onNavigateToNovel(it, null) }
                )

                AppDestinations.BOOKSHELF -> FavoritesScreen(
                    viewModel = favoritesViewModel,
                    onComicClick = onNavigateToDetail,
                    onNovelClick = { onNavigateToNovel(it, null) },
                    onNavigateToProfile = onNavigateToProfile
                )

                AppDestinations.PROFILE -> ProfileScreen(
                    onComicClick = onNavigateToDetail,
                    onHistoryCleared = favoritesViewModel::loadFavorites,
                    onReadComic = onReadComic,
                    onNavigateToNovel = onNavigateToNovel
                )
            }
        }
    }
}

/** 手机底栏和宽屏侧栏共用书架更新角标。 */
@Composable
private fun DestinationIcon(
    destination: AppDestinations,
    selected: Boolean,
    updatedComicCount: Int
) {
    val showBadge = destination == AppDestinations.BOOKSHELF && updatedComicCount > 0
    BadgedBox(
        badge = {
            if (showBadge) {
                Badge(containerColor = MaterialTheme.colorScheme.error) {
                    Text(
                        text = if (updatedComicCount > 99) "99+" else updatedComicCount.toString(),
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            }
        },
        modifier = Modifier.semantics {
            if (showBadge) stateDescription = "$updatedComicCount 部漫画有新内容"
        }
    ) {
        Icon(
            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = destination.label
        )
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
