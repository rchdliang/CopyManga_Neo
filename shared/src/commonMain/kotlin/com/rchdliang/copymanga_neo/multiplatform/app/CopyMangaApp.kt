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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.rchdliang.copymanga_neo.domain.usecase.FilterHomeComics
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
import com.rchdliang.copymanga_neo.multiplatform.ui.preview.PreviewAwareAsyncImage
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
private class MainSessionModel(private val users: UserRepository) : ViewModel() {
    private val loggedIn = MutableStateFlow(users.isLoggedInSync())
    val isLoggedIn = loggedIn.asStateFlow()
    val avatar = users.getAvatar().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private var triedAutoLoginAfterExpiry = false
    init {
        viewModelScope.launch {
            users.isAuthCacheReady().first { it }
            users.sessionChanges().collectLatest {
                val current = users.isLoggedInSync()
                val previous = loggedIn.value
                loggedIn.value = current
                if (!current && previous && users.getAutoLoginSync() && !triedAutoLoginAfterExpiry) {
                    triedAutoLoginAfterExpiry = true
                    viewModelScope.launch { autoLogin() }
                } else if (current) {
                    users.getMemberInfo()
                    triedAutoLoginAfterExpiry = false
                }
            }
        }
        viewModelScope.launch {
            users.isAuthCacheReady().first { it }
            if (!users.isLoggedInSync() && users.getAutoLoginSync()) autoLogin()
        }
    }
    private suspend fun autoLogin() {
        val generation = users.sessionVersionSync()
        val username = users.getSavedUsernameSync() ?: return
        val password = users.getSavedPasswordSync() ?: return
        if (!users.getAutoLoginSync() || users.sessionVersionSync() != generation) return
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
    val dark = when (preferences.themeMode) { AppThemeMode.DARK -> true; AppThemeMode.LIGHT -> false; AppThemeMode.SYSTEM -> isSystemInDarkTheme() }
    val share = models.get("application") { ShareViewModel(repositories.shares) }
    val session = models.get("application") { MainSessionModel(repositories.users) }
    val loggedIn by session.isLoggedIn.collectAsStateWithLifecycle()
    val avatar by session.avatar.collectAsStateWithLifecycle()
    val favorites = models.get("tab:FAVORITES") { FavoritesViewModel(repositories.manga, repositories.users, repositories.readingProgress, repositories.novelShelf) }
    val favoritesState by favorites.uiState.collectAsStateWithLifecycle()
    val exports = models.get("application", "export") { ComicExportViewModel(repositories.export, repositories.downloads, it) }
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
    val appImageLoader = remember(platforms.imageLoader) { platforms.imageLoader(10) }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, LocalUiPlatform provides platforms.ui, LocalShareViewModel provides share,
        LocalAppImageLoader provides appImageLoader,
        LocalNovelReaderPlatform provides platforms.novel, LocalComicExportPlatform provides platforms.export) {
        CopyManga_NeoTheme(darkTheme = dark) {
            AppToastHost {
                val toast = LocalToastHost.current
                val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
                LaunchedEffect(repositories.export, lifecycle) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        repositories.export.messages.collect { toast.showToast(it) }
                    }
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
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
                                                            RouteContent(AppRoute("tab", "tab:${tab.name}"), navigation, repositories, platforms, models, favorites)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else stateHolder.SaveableStateProvider(route.id) {
                                        RouteContent(route, navigation, repositories, platforms, models, favorites)
                                    }
                                }
                            }
                        }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainNavigationShell(
    current: MainTab, onSelect: (MainTab) -> Unit,
    loggedIn: Boolean, avatar: String?, updates: Int, content: @Composable () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 840.dp
        if (expanded) {
            val rail = rememberWideNavigationRailState(initialValue = WideNavigationRailValue.Collapsed)
            val scope = rememberCoroutineScope()
            Row(Modifier.fillMaxSize()) {
                WideNavigationRail(
                    state = rail,
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
                    colors = WideNavigationRailColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modalContainerColor = WideNavigationRailDefaults.colors().modalContainerColor,
                        modalScrimColor = WideNavigationRailDefaults.colors().modalScrimColor,
                        modalContentColor = WideNavigationRailDefaults.colors().modalContentColor
                    ),
                    header = {
                        Column(Modifier.padding(top = 16.dp).padding(start = 24.dp)) {
                            PreviewAwareAsyncImage(
                                model = avatar.takeIf { loggedIn }, contentDescription = "用户头像",
                                modifier = Modifier.padding(start = 6.dp).size(36.dp).clip(CircleShape).clickable { onSelect(MainTab.PROFILE) },
                                placeholder = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Filled.AccountCircle),
                                error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Filled.AccountCircle)
                            )
                            Spacer(Modifier.height(8.dp))
                            IconButton(
                                modifier = Modifier.semantics { stateDescription = if (rail.targetValue == WideNavigationRailValue.Expanded) "已展开" else "已折叠" },
                                onClick = { scope.launch { if (rail.targetValue == WideNavigationRailValue.Expanded) rail.collapse() else rail.expand() } }
                            ) { Icon(if (rail.targetValue == WideNavigationRailValue.Expanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Filled.Menu,
                                if (rail.targetValue == WideNavigationRailValue.Expanded) "折叠侧栏" else "展开侧栏") }
                        }
                    }
                ) {
                    MainTab.entries.forEach { tab ->
                        WideNavigationRailItem(railExpanded = rail.targetValue == WideNavigationRailValue.Expanded,
                            selected = current == tab, onClick = { onSelect(tab) },
                            icon = { TabIcon(tab, current == tab, updates) }, label = { Text(tab.title) })
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
    }
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
private fun RouteContent(route: AppRoute, nav: AppNavigation, r: AppRepositories, p: AppPlatforms, models: RouteModels, favorites: FavoritesViewModel) {
    val profile = { nav.select(MainTab.PROFILE) }
    val toast = LocalToastHost.current
    val back = { nav.back(); Unit }
    fun comments(key: String) = models.get(route.id, "comments:$key") { CommentsViewModel(r.comments, r.users, r.manga, r.downloads) }
    fun export() = models.get(route.id, "export") { ComicExportViewModel(r.export, r.downloads, it) }
    when (route.destination) {
        "detail" -> DetailScreen(route.path,
            models.get(route.id) { DetailViewModel(r.manga, r.users, r.downloads, r.blacklist, r.readingProgress, r.localReading) },
            commentsDialog = { target, title, allowed, dismiss, login -> CommentsDialog(target, title, dismiss, allowed, login, route.path, comments(target.toString())) },
            onChapterClick = { chapter, path, name, group -> nav.reader(path, chapter, name, group) }, onNavigateBack = back, onNavigateToProfile = profile,
            onThemeClick = { theme -> nav.open(AppRoute("search", arguments = mapOf(
                "themePathWord" to theme.pathWord.orEmpty(), "themeName" to theme.name))) },
            onAuthorClick = { author -> nav.open(AppRoute("search", arguments = mapOf("query" to author.name, "queryType" to QueryType.AUTHOR.value))) })
        "search" -> SearchScreen(onComicClick = nav::comic, onNavigateBack = back,
            onNavigateToExplore = { theme, top -> nav.open(AppRoute("explore", arguments = buildMap {
                put("section", "filter"); theme?.let { put("theme", it) }; top?.let { put("top", it) }
            })) },
            viewModel = models.get(route.id, defaults = route.arguments) { SearchViewModel(r.manga, r.discovery, it, r.appSettings, autoStart = false) })
        "explore" -> ComicExploreScreen(nav::comic, back,
            models.get(route.id, defaults = route.arguments) { ComicExploreViewModel(r.discovery, it, autoStart = false) })
        "novel" -> NovelScreen(onBack = back, initialResume = route.resume, initialPath = route.path.takeIf { it.isNotBlank() },
            viewModel = models.get(route.id) { NovelViewModel(r.novels, r.novelShelf, r.localReading, r.readingProgress, r.network, r.sessions, r.downloads, r.novelPreferences, r.annotations) },
            commentsViewModel = comments("novel"))
        "reader" -> ReaderScreen(chapterPathWord = route.chapter, comicPathWord = route.path, chapterName = route.chapterName,
            groupPathWord = route.group, onNavigateBack = back, initialPage = route.page, initialFraction = route.fraction,
            viewModel = models.get(route.id) { ReaderViewModel(r.manga, r.readerPreferences, r.downloads, r.readingProgress, r.readerTools, r.localReading, r.sessions) },
            services = ReaderUiServices(p.reader, p.imageLoader,
                models.get(route.id) { ReaderImageActionsViewModel(r.images, it) },
                { chapter -> models.get(route.id, "inline:$chapter") { ReaderChapterCommentsViewModel(r.comments) } },
                modalComments = { comments(ReaderCommentModelKeys.modal) },
                inlineComments = { chapter -> comments(ReaderCommentModelKeys.inline(chapter)) }), exportViewModel = export())
        "tab" -> when (MainTab.valueOf(route.id.substringAfter(':'))) {
            MainTab.HOME -> HomeScreen(
                models.get(route.id) { HomeViewModel(r.manga, r.discovery, r.blacklist, FilterHomeComics(r.manga), r.risk, r.appSettings, autoStart = false) },
                models.get(route.id) { HomeNovelViewModel(r.novels, r.sessions, autoStart = false) }, nav::comic,
                onNavigateToSearch = { nav.open(AppRoute("search")) },
                onNavigateToExplore = nav::explore,
                onNavigateToDownloads = { nav.select(MainTab.DOWNLOADS) },
                onNavigateToTopic = { path, title -> nav.open(AppRoute("explore", arguments = mapOf("section" to "topics", "topic" to path, "topicTitle" to title))) },
                onNavigateToNovel = { nav.novel(it) })
            MainTab.FAVORITES -> FavoritesScreen(favorites, nav::comic, profile) { nav.novel(it) }
            MainTab.DOWNLOADS -> DownloadsScreen(onComicClick = nav::comic,
                viewModel = models.get(route.id) { DownloadsViewModel(r.downloads, r.novels, r.sessions) }, exportViewModel = export(), onNovelClick = { nav.novel(it) })
            MainTab.PROFILE -> ProfileScreen(
                models.get(route.id) { ProfileViewModel(r.users, r.blacklist, r.network, r.appSettings, autoStart = false) },
                ProfileUiServices(p.profile, p.buildInfo,
                    models.get(route.id) { SettingsViewModel(r.appSettings, r.readerPreferences, r.storage) },
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
