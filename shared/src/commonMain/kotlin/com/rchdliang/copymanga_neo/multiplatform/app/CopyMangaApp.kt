package com.rchdliang.copymanga_neo.multiplatform.app

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.savedstate.SavedState
import androidx.savedstate.read
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import com.rchdliang.copymanga_neo.multiplatform.ui.components.*
import com.rchdliang.copymanga_neo.multiplatform.ui.home.*
import com.rchdliang.copymanga_neo.multiplatform.ui.search.*
import com.rchdliang.copymanga_neo.multiplatform.ui.detail.*
import com.rchdliang.copymanga_neo.multiplatform.ui.explore.*
import com.rchdliang.copymanga_neo.multiplatform.ui.favorites.*
import com.rchdliang.copymanga_neo.multiplatform.ui.history.*
import com.rchdliang.copymanga_neo.multiplatform.ui.library.*
import com.rchdliang.copymanga_neo.multiplatform.ui.novel.*
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.*
import com.rchdliang.copymanga_neo.multiplatform.ui.downloads.*
import com.rchdliang.copymanga_neo.multiplatform.ui.comments.*
import com.rchdliang.copymanga_neo.multiplatform.ui.export.*
import com.rchdliang.copymanga_neo.multiplatform.ui.profile.*
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Host-retained owner, not composition lifetime: rotation cannot clear reader/picker state. */
private class RouteModels(private val savedState: SavedStateHandle) : ViewModel() {
    private val stores = mutableMapOf<String, ViewModelStoreOwner>()
    private val handles = mutableMapOf<String, SavedStateHandle>()
    private fun owner(key: String) = stores.getOrPut(key) {
        object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    }
    private fun handle(route: String, key: String, defaults: Map<String, Any?>): SavedStateHandle {
        val stateKey = "route:${route.length}:$route:${key.length}:$key"
        return handles.getOrPut(stateKey) {
            // The root handle comes from the host's real saved-state CreationExtras on Android.
            // Child providers are snapshotted into that handle, including system-picker continuation metadata.
            val restored: SavedState? = savedState[stateKey]
            val restoredValues = restored?.read { toMap() }.orEmpty()
            SavedStateHandle(defaults + restoredValues).also { handle ->
                savedState.setSavedStateProvider(stateKey) { handle.savedStateProvider().saveState() }
            }
        }
    }
    inline fun <reified T : ViewModel> get(
        route: String,
        key: String = T::class.simpleName.orEmpty(),
        defaults: Map<String, Any?> = emptyMap(),
        noinline factory: (SavedStateHandle) -> T
    ): T = ViewModelProvider.create(owner(route), viewModelFactory {
        initializer { factory(handle(route, key, defaults)) }
    })[key, T::class]

    fun retain(keys: Set<String>) {
        (stores.keys - keys).forEach { route ->
            stores.remove(route)?.viewModelStore?.clear()
            val prefix = "route:${route.length}:$route:"
            handles.keys.filter { it.startsWith(prefix) }.forEach {
                handles.remove(it)
                savedState.clearSavedStateProvider(it)
                savedState.remove<SavedState>(it)
            }
        }
    }
    override fun onCleared() {
        stores.values.forEach { it.viewModelStore.clear() }
        stores.clear(); handles.clear()
    }
}

/** Original startup, session-expiry and avatar behavior, retained with the application's model store. */
internal class MainSessionModel(private val users: UserRepository, private val startupTrace: StartupTrace = StartupTrace.None) : ViewModel() {
    private val loggedIn = MutableStateFlow(users.isLoggedInSync())
    val isLoggedIn = loggedIn.asStateFlow()
    val avatar = users.getAvatar().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private var triedAutoLoginAfterExpiry = false
    private var started = false
    private var autoLoginJob: Job? = null
    private val errors = CoroutineExceptionHandler { _, error -> UiLog.e("MainSession", "会话刷新失败", error) }

    /** 缓存身份立即可用；网络刷新在首帧后启动，且不在 UI 线程构建/解析请求。 */
    fun start() {
        if (started) return
        started = true
        startupTrace.mark("session_refresh_start")
        viewModelScope.launch(errors) {
            users.isAuthCacheReady().first { it }
            users.sessionChanges().collectLatest {
                val current = users.isLoggedInSync()
                val previous = loggedIn.value
                loggedIn.value = current
                if (!current && previous && users.getAutoLoginSync() && !triedAutoLoginAfterExpiry) {
                    triedAutoLoginAfterExpiry = true
                    requestAutoLogin()
                } else if (current) {
                    withContext(Dispatchers.Default) {
                        startupTrace.mark("member_request_worker")
                        startupTrace.measure("member_refresh") { users.getMemberInfo() }
                    }
                    triedAutoLoginAfterExpiry = false
                }
            }
        }
        viewModelScope.launch(errors) {
            users.isAuthCacheReady().first { it }
            if (!users.isLoggedInSync() && users.getAutoLoginSync()) requestAutoLogin()
        }
    }

    /** 延后启动期间发生会话失效时，初始登录和失效补登只能启动同一个请求。 */
    private fun requestAutoLogin() {
        if (autoLoginJob?.isActive == true) return
        autoLoginJob = viewModelScope.launch(errors, start = CoroutineStart.LAZY) { autoLogin() }
        autoLoginJob?.start()
    }
    private suspend fun autoLogin() = withContext(Dispatchers.Default) {
        val generation = users.sessionVersionSync()
        val username = users.getSavedUsernameSync() ?: return@withContext
        val password = users.getSavedPasswordSync() ?: return@withContext
        if (!users.getAutoLoginSync() || users.sessionVersionSync() != generation) return@withContext
        users.login(username, password, expectedGeneration = generation).onSuccess { users.getMemberInfo() }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CopyMangaApp(
    repositories: AppRepositories,
    platforms: AppPlatforms,
    viewModelStoreOwner: ViewModelStoreOwner? = LocalViewModelStoreOwner.current,
    openDownloads: Boolean = false,
    onDownloadsOpened: () -> Unit = {},
    retryExportId: String? = null,
    onExportRetryConsumed: () -> Unit = {}
) {
    val startupTrace = repositories.startupTrace
    startupTrace.mark("app_enter")
    var shellDrawn by remember(repositories) { mutableStateOf(false) }
    LaunchedEffect(repositories, shellDrawn) {
        if (shellDrawn) {
            repositories.startBackground()
            repositories.appSettings.initializeChineseDefault(androidx.compose.ui.text.intl.Locale.current.toLanguageTag())
                .onFailure { UiLog.w("Main", "系统语言默认设置初始化失败", it) }
        }
    }
    val owner = requireNotNull(viewModelStoreOwner) {
        "Provide a window/activity ViewModelStoreOwner and clear it only when that host finishes."
    }
    val models = ViewModelProvider.create(owner, viewModelFactory {
        initializer {
            val handle = if (this[SAVED_STATE_REGISTRY_OWNER_KEY] != null && this[VIEW_MODEL_STORE_OWNER_KEY] != null)
                createSavedStateHandle() else SavedStateHandle()
            RouteModels(handle)
        }
    })["copymanga:routes", RouteModels::class]
    val navigation = rememberSaveable(saver = NavHostNavigation.saver) { NavHostNavigation() }
    val stateHolder = rememberSaveableStateHolder()
    val permanentModelIds = remember { MainTab.entries.map { "tab:${it.name}" }.toSet() + "application" }
    val settingsFlow = remember(repositories.appSettings) {
        repositories.appSettings.settings.catch { error ->
            UiLog.e("Main", "应用设置读取失败", error)
            emit(AppSettings())
        }
    }
    val settings by settingsFlow.collectAsStateWithLifecycle(null)
    val preferences = settings ?: return
    startupTrace.mark("app_settings_observed")
    val finishComposition = startupTrace.begin("app_shell_compose")
    val dark = when (preferences.themeMode) { AppThemeMode.DARK -> true; AppThemeMode.LIGHT -> false; AppThemeMode.SYSTEM -> isSystemInDarkTheme() }
    val share = models.get("application") { startupTrace.measureSync("share_model") { ShareViewModel(repositories.shares) } }
    val session = models.get("application") { startupTrace.measureSync("session_model") { MainSessionModel(repositories.users, startupTrace) } }
    LaunchedEffect(session, shellDrawn) { if (shellDrawn) session.start() }
    val updateModel = models.get("application", "automatic-update") { AboutViewModel(repositories.appInfo) }
    val updateState by updateModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(updateModel, shellDrawn) {
        if (shellDrawn) updateModel.checkAutomatically(platforms.buildInfo.versionName, preferences.autoCheckUpdates)
    }
    val loggedIn by session.isLoggedIn.collectAsStateWithLifecycle()
    val avatar by session.avatar.collectAsStateWithLifecycle()
    val favorites = models.get("tab:FAVORITES") { startupTrace.measureSync("favorites_model") { FavoritesViewModel(repositories.manga, repositories.users, repositories.readingProgress, repositories.novelShelf, autoStart = false) } }
    LaunchedEffect(favorites, shellDrawn) { if (shellDrawn) favorites.start() }
    val favoritesState by favorites.uiState.collectAsStateWithLifecycle()
    val exports = models.get("application", "export") { handle -> startupTrace.measureSync("export_model") { ComicExportViewModel(repositories.export, handle) } }
    val exportState by repositories.export.state.collectAsStateWithLifecycle()
    val downloads by repositories.downloads.getDownloadProgress().collectAsStateWithLifecycle()
    platforms.notifications?.let { DownloadNotificationPermission(downloads.isNotEmpty() || exportState.running, it) }
    LaunchedEffect(openDownloads, navigation.ready) {
        if (openDownloads && navigation.ready) { navigation.select(MainTab.DOWNLOADS); onDownloadsOpened() }
    }
    LaunchedEffect(retryExportId) {
        retryExportId?.let { exports.retry(it); onExportRetryConsumed() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { repositories.risk.resumeIfExpired() }
    val appImageLoader = remember(platforms.imageLoader) { startupTrace.measureSync("image_loader") { platforms.imageLoader(10) } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, LocalUiPlatform provides platforms.ui, LocalShareViewModel provides share,
        LocalAppImageLoader provides appImageLoader, LocalStartupTrace provides repositories.startupTrace,
        LocalNovelReaderPlatform provides platforms.novel, LocalComicExportPlatform provides platforms.export) {
        MetadataTextHost(preferences.simplifyChinese, repositories.chineseDictionary) {
            CopyManga_NeoTheme(darkTheme = dark) {
                val colors = MaterialTheme.colorScheme
                SideEffect { repositories.startupTrace.palette("shell", colors.primary.value, colors.surface.value) }
                AppToastHost {
                    updateState.update?.let { release ->
                        val uri = androidx.compose.ui.platform.LocalUriHandler.current
                        val updateToast = LocalToastHost.current
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = updateModel::dismissUpdate,
                            title = { Text("发现新版本", color = MaterialTheme.colorScheme.onSurface) },
                            text = { Text("最新版本：${release.version}", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                                runCatching { uri.openUri(release.pageUrl) }.onFailure { updateToast.showToast("无法打开浏览器") }
                                updateModel.dismissUpdate()
                            }) { Text("查看发布页", color = MaterialTheme.colorScheme.primary) } },
                            dismissButton = { androidx.compose.material3.TextButton(onClick = updateModel::dismissUpdate) {
                                Text("稍后", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } }
                        )
                    }
                    val toast = LocalToastHost.current
                    LaunchedEffect(platforms.ui) {
                        platforms.ui.toastMessages.collect { toast.showToast(it) }
                    }
                    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
                    LaunchedEffect(repositories.export, lifecycle) {
                        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                            repositories.export.messages.collect { toast.showToast(it) }
                        }
                    }
                    val presentation = models.get("application") { startupTrace.measureSync("presentation_model") { ComicPresentationViewModel(repositories.comicMetadata, repositories.blacklist) } }
                    val novelPreview = models.get("application") { startupTrace.measureSync("novel_preview_model") { NovelPreviewViewModel(repositories.novels, repositories.sessions, repositories.network) } }
                    ComicPresentationHost(presentation, novelPreview) {
                    BoxWithConstraints(Modifier.fillMaxSize().startupLayout(startupTrace, "root").startupDraw(startupTrace, "shell_draw")) {
                        val finishRoot = startupTrace.begin("root_subcompose")
                        val wide = maxWidth >= 840.dp
                        Box(Modifier.fillMaxSize().background(if (wide) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface)) {
                            val transitionState = rememberComicSharedTransitionState()
                            SharedTransitionLayout(Modifier.fillMaxSize()) {
                                val sharedScope = this
                                AppRouteNavHost(
                                    navigation = navigation,
                                    backDispatcher = platforms.ui.backDispatcher,
                                    onRetainRoutes = { retained ->
                                        navigation.retain(retained).forEach(stateHolder::removeState)
                                        models.retain(retained + permanentModelIds)
                                    }
                                ) { route ->
                                    ComicSharedTransitionProvider(sharedScope, this, transitionState) {
                                        if (route.destination == "main") {
                                            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { favorites.onMainScreenResumed() }
                                            Box(Modifier.statusBarsPadding()) {
                                                MainNavigationShell(navigation.tab, navigation::select, loggedIn, avatar, favoritesState.updatedComicCount) {
                                                    AnimatedContent(
                                                        navigation.tab, label = "main destination transition",
                                                        transitionSpec = {
                                                            val direction = if (targetState.ordinal > initialState.ordinal)
                                                                AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right
                                                            (slideIntoContainer(direction, tween(300, easing = EaseInOut)) + fadeIn(tween(300, easing = EaseInOut))) togetherWith
                                                                (slideOutOfContainer(direction, tween(300, easing = EaseInOut)) + fadeOut(tween(300, easing = EaseInOut)))
                                                        }
                                                    ) { tab ->
                                                        RouteLifecycleScope(active = tab == navigation.tab) {
                                                            stateHolder.SaveableStateProvider("tab:${tab.name}") {
                                                                RouteContent(AppRoute("tab", "tab:${tab.name}"), navigation, repositories, platforms, models, favorites) { if (!shellDrawn) shellDrawn = true }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } else stateHolder.SaveableStateProvider(route.id) {
                                            RouteContent(route, navigation, repositories, platforms, models, favorites) { if (!shellDrawn) shellDrawn = true }
                                        }
                                    }
                                }
                            }
                        }
                        finishRoot?.invoke()
                    }
                    }
                    ComicExportDialogs(exports)
                    if (platforms.buildInfo.debug) repositories.novelImagePositionDebug?.let { debug ->
                        val samples by debug.samples.collectAsStateWithLifecycle()
                        samples.firstOrNull()?.let { sample ->
                            key(sample.id) { NovelImagePositionDebugDialog(sample) { debug.dismiss(sample.id) } }
                        }
                    }
                }
            }
        }
    }
    finishComposition?.invoke()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MainNavigationShell(
    current: MainTab, onSelect: (MainTab) -> Unit,
    loggedIn: Boolean, avatar: String?, updates: Int, content: @Composable () -> Unit
) {
    val trace = LocalStartupTrace.current
    val finish = trace.begin("navigation_shell_compose")
    BoxWithConstraints(Modifier.fillMaxSize().startupLayout(trace, "navigation_shell")) {
        val finishSubcompose = trace.begin("navigation_shell_subcompose")
        val expanded = maxWidth >= 840.dp
        if (expanded) {
            val rail = rememberWideNavigationRailState(initialValue = WideNavigationRailValue.Collapsed)
            val scope = rememberCoroutineScope()
            val railInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
            val insetPadding = railInsets.asPaddingValues()
            val railHeight = maxHeight - insetPadding.calculateTopPadding() - insetPadding.calculateBottomPadding()
            val canExpand = railHeight >= 480.dp
            LaunchedEffect(canExpand) { if (!canExpand) rail.collapse() }
            val density = LocalDensity.current
            val labelHeight = with(density) { MaterialTheme.typography.labelMedium.lineHeight.toDp() }
            val itemHeight = maxOf(80.dp, 48.dp + labelHeight)
            // 正常高度保留头像、展开按钮和标准留白；紧凑高度只保留导航与最小边距。
            val reservedHeight = if (canExpand) 248.dp else 16.dp
            val visibleCount = with(density) {
                navigationRailVisibleCount(MainTab.entries.size,
                    (railHeight - reservedHeight).roundToPx(), itemHeight.roundToPx())
            }
            val visibleTabs = MainTab.entries.take(visibleCount)
            val overflowTabs = MainTab.entries.drop(visibleCount)
            var overflowOpen by remember(overflowTabs) { mutableStateOf(false) }
            Row(Modifier.fillMaxSize()) {
                WideNavigationRail(
                    state = rail,
                    windowInsets = railInsets,
                    colors = WideNavigationRailColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modalContainerColor = WideNavigationRailDefaults.colors().modalContainerColor,
                        modalScrimColor = WideNavigationRailDefaults.colors().modalScrimColor,
                        modalContentColor = WideNavigationRailDefaults.colors().modalContentColor
                    ),
                    contentPadding = if (canExpand) PaddingValues(top = 60.dp, bottom = 44.dp) else PaddingValues(vertical = 8.dp),
                    header = if (canExpand) {
                        {
                            Column(Modifier.padding(start = 24.dp)) {
                                NavigationAvatar(
                                    loggedIn = loggedIn, avatar = avatar,
                                    modifier = Modifier.size(48.dp).clickable { onSelect(MainTab.PROFILE) }.padding(6.dp).clip(CircleShape)
                                )
                                Spacer(Modifier.height(8.dp))
                                IconButton(
                                    modifier = Modifier.semantics { stateDescription = if (rail.targetValue == WideNavigationRailValue.Expanded) "已展开" else "已折叠" },
                                    onClick = { scope.launch { if (rail.targetValue == WideNavigationRailValue.Expanded) rail.collapse() else rail.expand() } }
                                ) { Icon(if (rail.targetValue == WideNavigationRailValue.Expanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Filled.Menu,
                                    if (rail.targetValue == WideNavigationRailValue.Expanded) "折叠侧栏" else "展开侧栏") }
                            }
                        }
                    } else null
                ) {
                        visibleTabs.forEach { tab ->
                            key(tab) {
                            // 与 Android 同步使用目标状态，让图标、文字和侧栏宽度同时过渡。
                            WideNavigationRailItem(railExpanded = rail.targetValue == WideNavigationRailValue.Expanded,
                                selected = current == tab, onClick = { onSelect(tab) },
                                icon = { TabIcon(tab, current == tab, updates) },
                                label = { Text(tab.title, color = if (current == tab)
                                    MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) })
                            }
                        }
                        if (overflowTabs.isNotEmpty()) Box {
                            WideNavigationRailItem(
                                railExpanded = rail.targetValue == WideNavigationRailValue.Expanded,
                                selected = current in overflowTabs,
                                onClick = { overflowOpen = true },
                                icon = {
                                    BadgedBox(badge = {
                                        if (MainTab.FAVORITES in overflowTabs && updates > 0) Badge {
                                            Text(updates.toString(), color = MaterialTheme.colorScheme.onError)
                                        }
                                    }) { Icon(Icons.Filled.MoreVert, "更多导航") }
                                },
                                label = { Text("更多", color = if (current in overflowTabs)
                                    MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
                            )
                            DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                                overflowTabs.forEach { tab ->
                                    DropdownMenuItem(
                                        text = { Text(tab.title, color = MaterialTheme.colorScheme.onSurface) },
                                        leadingIcon = { TabIcon(tab, current == tab, updates) },
                                        trailingIcon = { if (current == tab) Icon(Icons.Filled.Check, "当前页面") },
                                        onClick = { overflowOpen = false; onSelect(tab) }
                                    )
                                }
                            }
                        }
                }
                Box(Modifier.weight(1f).fillMaxSize().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface)) { content() }
            }
        } else Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.surface)) { content() }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(selected = current == tab, onClick = { onSelect(tab) },
                        icon = { TabIcon(tab, current == tab, updates) }, label = { Text(tab.title) })
                }
            }
        }
        finishSubcompose?.invoke()
    }
    finish?.invoke()
}

@Composable
private fun TabIcon(tab: MainTab, selected: Boolean, updates: Int) {
    val icon: ImageVector = when (tab) {
        MainTab.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
        MainTab.DOWNLOADS -> if (selected) Icons.Filled.Download else Icons.Outlined.Download
        MainTab.FAVORITES -> if (selected) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
        MainTab.PROFILE -> if (selected) Icons.Filled.AccountBox else Icons.Outlined.AccountBox
    }
    val showBadge = tab == MainTab.FAVORITES && updates > 0
    BadgedBox(badge = {
        if (showBadge) Badge(containerColor = MaterialTheme.colorScheme.error) {
            Text(if (updates > 99) "99+" else updates.toString(), color = MaterialTheme.colorScheme.onError)
        }
    }, modifier = Modifier.semantics { if (showBadge) stateDescription = "$updates 部漫画有新内容" }) {
        Icon(icon, tab.title)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RouteContent(route: AppRoute, nav: AppNavigation, r: AppRepositories, p: AppPlatforms, models: RouteModels, favorites: FavoritesViewModel, onDrawn: () -> Unit) {
    // 导航容器空帧不算主页就绪；目的页面真正绘制后才启动非首屏预热。
    Box(Modifier.fillMaxSize().drawWithContent { drawContent(); onDrawn() }) {
        RouteDestination(route, nav, r, p, models, favorites)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RouteDestination(route: AppRoute, nav: AppNavigation, r: AppRepositories, p: AppPlatforms, models: RouteModels, favorites: FavoritesViewModel) {
    val profile = { nav.select(MainTab.PROFILE) }
    val toast = LocalToastHost.current
    val back = { nav.back(); Unit }
    fun comments(key: String) = models.get(route.id, "comments:$key") { CommentsViewModel(r.comments, r.users, r.manga, r.downloads) }
    fun export() = models.get(route.id, "export") { ComicExportViewModel(r.export, it) }
    when (route.destination) {
        "export-chapters" -> ComicExportSelectionScreen(
            viewModel = models.get(route.id, defaults = route.arguments) { ComicExportSelectionViewModel(r.export, it) },
            exportViewModel = export(), onBack = back)
        "detail" -> DetailScreen(route.path,
            models.get(route.id) { DetailViewModel(r.manga, r.users, r.downloads, r.blacklist, r.readingProgress, r.localReading) },
            commentsDialog = { target, title, allowed, dismiss, login -> CommentsDialog(target, title, dismiss, allowed, login, route.path, comments(target.toString())) },
            onChapterClick = { chapter, path, name, group -> nav.reader(path, chapter, name, group) }, onNavigateBack = back, onNavigateToProfile = profile,
            onStartChapterClick = { chapter, path, name, group, page -> nav.reader(path, chapter, name, group, page = page) },
            onThemeClick = { theme -> nav.open(AppRoute("search", arguments = mapOf(
                "themePathWord" to theme.pathWord.orEmpty(), "themeName" to theme.name))) },
            onAuthorClick = { author -> nav.open(AppRoute("search", arguments = mapOf("query" to author.name, "queryType" to QueryType.AUTHOR.value))) },
            onOriginalWorkClick = { work ->
                work.pathWord?.takeIf(String::isNotBlank)?.let { path ->
                    nav.open(AppRoute("original", arguments = mapOf("originalPath" to path, "originalName" to work.name)))
                }
            })
        "original" -> OriginalWorkScreen(
            models.get(route.id, defaults = route.arguments) { OriginalWorkViewModel(r.discovery, r.network, r.appSettings, it) },
            onComic = nav::comic, onBack = back)
        "search" -> SearchScreen(onComicClick = nav::comic, onNavigateBack = back,
            onNavigateToExplore = { theme, top -> nav.open(AppRoute("explore", arguments = buildMap {
                put("section", "filter"); theme?.let { put("theme", it) }; top?.let { put("top", it) }
            })) },
            viewModel = models.get(route.id, defaults = route.arguments) { SearchViewModel(r.manga, r.discovery, it, r.appSettings, autoStart = false) })
        "explore" -> ComicExploreScreen(nav::comic, back,
            models.get(route.id, defaults = route.arguments) { ComicExploreViewModel(r.discovery, it, autoStart = false) })
        "novel" -> NovelScreen(onBack = back, initialResume = route.resume, initialPath = route.path.takeIf { it.isNotBlank() },
            viewModel = models.get(route.id) { NovelViewModel(r.novels, r.novelShelf, r.localReading, r.readingProgress, r.network, r.sessions, r.downloads, r.novelPreferences, r.annotations) },
            commentsViewModel = comments("novel"),
            illustrations = models.get(route.id, "novel-illustrations") { NovelIllustrationsViewModel(r.novelPreferences, r.appSettings, r.novelIllustrations, it) },
            readerPlatform = p.reader)
        "reader" -> ReaderScreen(chapterPathWord = route.chapter, comicPathWord = route.path, chapterName = route.chapterName,
            groupPathWord = route.group, onNavigateBack = back, initialPage = route.page, initialFraction = route.fraction,
            viewModel = models.get(route.id) { ReaderViewModel(r.manga, r.readerPreferences, r.downloads, r.readingProgress, r.readerTools, r.localReading, r.sessions) },
            services = ReaderUiServices(p.reader, p.imageLoader,
                models.get(route.id) { ReaderImageActionsViewModel(r.images, it) },
                models.get(route.id) { ReaderCommentModels(r.comments, r.users, r.manga, r.downloads, r.sessions) },
                modalComments = { comments(ReaderCommentModelKeys.modal) }))
        "tab" -> when (MainTab.valueOf(route.id.substringAfter(':'))) {
            MainTab.HOME -> HomeScreen(
                models.get(route.id) { r.startupTrace.measureSync("home_model") { HomeViewModel(r.manga, r.discovery, r.risk, r.appSettings, autoStart = false, startupTrace = r.startupTrace) } },
                models.get(route.id) { r.startupTrace.measureSync("home_novel_model") { HomeNovelViewModel(r.novels, r.sessions, autoStart = false, startupTrace = r.startupTrace) } }, nav::comic,
                onNavigateToSearch = { nav.open(AppRoute("search")) },
                onNavigateToExplore = nav::explore,
                onNavigateToDownloads = { nav.select(MainTab.DOWNLOADS) },
                onNavigateToTopic = { path, title -> nav.open(AppRoute("explore", arguments = mapOf("section" to "topics", "topic" to path, "topicTitle" to title))) },
                onNavigateToNovel = { nav.novel(it) })
            MainTab.FAVORITES -> FavoritesScreen(favorites, nav::comic, profile, onDiscover = { if (favorites.uiState.value.selectedKind == com.rchdliang.copymanga_neo.multiplatform.ui.favorites.FavoritesKind.NOVEL) nav.novel() else nav.open(AppRoute("search")) }) { nav.novel(it) }
            MainTab.DOWNLOADS -> DownloadsScreen(onComicClick = nav::comic, onDiscover = { nav.open(AppRoute("search")) },
                viewModel = models.get(route.id) { DownloadsViewModel(r.downloads, r.novels, r.sessions) }, exportViewModel = export(),
                onExportChapters = { comics -> nav.open(AppRoute("export-chapters", arguments = mapOf("comics" to Json.encodeToString(comics)))) },
                onNovelClick = { nav.novel(it) })
            MainTab.PROFILE -> ProfileScreen(
                models.get(route.id) { ProfileViewModel(r.users, r.blacklist, r.network, r.appSettings, autoStart = false) },
                ProfileUiServices(p.profile, p.buildInfo,
                    models.get(route.id) { SettingsViewModel(r.appSettings, r.readerPreferences, r.storage, r.chineseDictionary) },
                    models.get(route.id) { AboutViewModel(r.appInfo) },
                    models.get(route.id) { SettingsBackupViewModel(r.backup, p.profile, it) },
                    models.get(route.id) { NetworkSettingsViewModel(r.network) },
                    models.get(route.id) { NetworkLogViewModel(r.networkLog, p.profile, it) },
                    models.get(route.id) { LocalLibraryViewModel(r.localReading, r.readingProgress) },
                    models.get(route.id) { HistoryViewModel(r.manga, r.users, r.readingProgress) },
                    models.get(route.id) { ProfileAvatarViewModel(p.profile) }),
                onNavigateToDownloads = { nav.select(MainTab.DOWNLOADS) }, onComicClick = nav::comic,
                onHistoryCleared = favorites::loadFavorites,
                onReadComic = { path, chapter, name, group, page, fraction -> nav.reader(path, chapter, name, group, page, fraction) },
                onNavigateToNovel = { path, resume -> nav.novel(path, resume) })
        }
        else -> error("Unknown navigation destination: ${route.destination}")
    }
}
