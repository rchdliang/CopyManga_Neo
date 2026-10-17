package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rchdliang.copymanga_neo.multiplatform.ui.components.routeProgressAllowed
import com.rchdliang.copymanga_neo.multiplatform.ui.components.LocalRouteActive
import com.rchdliang.copymanga_neo.multiplatform.ui.components.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import com.rchdliang.copymanga_neo.domain.model.NovelAnnotation
import com.rchdliang.copymanga_neo.domain.model.NovelBook
import com.rchdliang.copymanga_neo.domain.model.NovelReaderPreferences
import com.rchdliang.copymanga_neo.domain.model.NovelReadingVolume
import com.rchdliang.copymanga_neo.domain.model.NovelSection
import com.rchdliang.copymanga_neo.domain.model.NovelTextRange
import com.rchdliang.copymanga_neo.domain.model.NovelTextSelection
import com.rchdliang.copymanga_neo.domain.model.NovelVolume
import com.rchdliang.copymanga_neo.domain.model.displayBookTitle
import com.rchdliang.copymanga_neo.domain.reading.NovelBookMatch
import com.rchdliang.copymanga_neo.domain.reading.NovelReaderDocument
import com.rchdliang.copymanga_neo.domain.reading.NovelReaderEntry
import com.rchdliang.copymanga_neo.domain.reading.NovelSearchScope
import com.rchdliang.copymanga_neo.domain.reading.NovelTextDocument
import com.rchdliang.copymanga_neo.domain.reading.textIn
import kotlin.math.abs
import kotlin.math.roundToInt

private val NovelSelectionSaver = Saver<NovelTextSelection?, List<Any>>(
    save = { selected ->
        selected?.let {
            listOf(
                it.text,
                it.range.startLine,
                it.range.startCharacter,
                it.range.endLine,
                it.range.endCharacter
            )
        }
    },
    restore = { value ->
        NovelTextSelection(
            value[0] as String,
            NovelTextRange(value[1] as Int, value[2] as Int, value[3] as Int, value[4] as Int)
        )
    }
)

/** 请求与持久化操作独立于阅读界面，预览无需构造 ViewModel。 */
internal data class NovelReaderActions(
    val position: (NovelReaderLocation) -> Unit = {},
    val bookmark: () -> Unit = {},
    val preferences: (NovelReaderPreferences) -> Unit = {},
    val volume: (String) -> Unit = {},
    val loadVolumes: () -> Unit = {},
    val openBookmark: (LocalReadingRecord) -> Unit = {},
    val removeBookmark: (LocalReadingRecord) -> Unit = {},
    val cache: () -> Unit = {},
    val refresh: () -> Unit = {},
    val deleteCache: () -> Unit = {},
    val retry: () -> Unit = {},
    val addAnnotation: (NovelTextSelection, String, String, (Boolean) -> Unit) -> Unit = { _, _, _, done ->
        done(
            true
        )
    },
    val updateAnnotation: (NovelAnnotation, (Boolean) -> Unit) -> Unit = { _, done -> done(true) },
    val removeAnnotation: (NovelAnnotation) -> Unit = {},
    val openAnnotation: (NovelAnnotation) -> Unit = {},
    val openSearch: (String?) -> Unit = {},
    val search: (String, NovelSearchScope) -> Unit = { _, _ -> },
    val cancelSearch: () -> Unit = {},
    val closeSearch: () -> Unit = {},
    val endSearch: () -> Unit = {},
    val searchMatch: (NovelBookMatch) -> Unit = {},
    val moveSearchMatch: (Int) -> Unit = {}

)

@Composable
internal fun NovelReader(state: NovelUiState, viewModel: NovelViewModel, onBack: () -> Unit) {
    val committedActive by rememberUpdatedState(LocalRouteActive.current)
    val reportingLifecycle = LocalLifecycleOwner.current.lifecycle
    val preferences by viewModel.readerPreferences.collectAsStateWithLifecycle()
    val local by viewModel.localData.collectAsStateWithLifecycle()
    val savedAnnotations by viewModel.annotations.collectAsStateWithLifecycle()
    val readerSearch by viewModel.readerSearch.collectAsStateWithLifecycle()
    val path = state.reader?.book?.path ?: state.detailPath
    val bookmarks = local.bookmarks.filter {
        it.owner == state.owner && it.kind == "novel" && it.path == path &&
                it.group in setOf("default", state.mode.name)
    }.sortedByDescending { it.updatedAt }
    val annotations = savedAnnotations.filter {
        it.owner == state.owner && it.mode == state.mode.name && it.path == path
    }.sortedByDescending { it.updatedAt }
    NovelReaderContent(
        state, preferences, bookmarks, viewModel.currentPosition(), onBack,
        NovelReaderActions(
            position = {
                if (routeProgressAllowed(committedActive, reportingLifecycle.currentState)) viewModel.position(
                    it.section,
                    it.line,
                    it.character,
                    it.fraction,
                    it.completed
                )
            },
            bookmark = viewModel::bookmark,
            preferences = viewModel::updateReaderPreferences,
            volume = { id -> state.reader?.book?.path?.let { viewModel.openVolume(it, id) } },
            openBookmark = viewModel::jumpToBookmark,
            removeBookmark = viewModel::removeBookmark,
            loadVolumes = viewModel::loadReaderVolumes,
            cache = viewModel::cache,
            refresh = viewModel::refreshVolume,
            deleteCache = viewModel::deleteCache,
            retry = viewModel::retry,
            addAnnotation = viewModel::addAnnotation,
            updateAnnotation = viewModel::updateAnnotation,
            removeAnnotation = viewModel::removeAnnotation,
            openAnnotation = viewModel::jumpToAnnotation,
            openSearch = viewModel::openReaderSearch,
            search = viewModel::searchReader,
            cancelSearch = { viewModel.cancelReaderSearch() },
            closeSearch = viewModel::closeReaderSearch,
            endSearch = viewModel::endReaderSearch,
            searchMatch = viewModel::openReaderSearchMatch,
            moveSearchMatch = viewModel::moveReaderSearchMatch
        ), annotations = annotations, search = readerSearch
    )
}

@Composable
private fun NovelReaderTheme(preferences: NovelReaderPreferences, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val colors = when (preferences.theme) {
        "LIGHT" -> lightColorScheme()
        "DARK" -> darkColorScheme()
        "PAPER" -> base.copy(
            surface = base.tertiaryContainer, background = base.tertiaryContainer,
            onSurface = base.onTertiaryContainer, onSurfaceVariant = base.onTertiaryContainer
        )

        else -> base
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** 系统栏与常亮由宿主实现；正文、浮层与返回规则仍共用同一套阅读逻辑。 */
@Composable
private fun NovelReaderWindow(keepScreenOn: Boolean, controlsVisible: Boolean) {
    if (LocalRouteActive.current) novelReaderPlatform()?.ReaderWindow(
        keepScreenOn = keepScreenOn,
        controlsVisible = controlsVisible,
        lightBars = MaterialTheme.colorScheme.surface.luminance() > .5f
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NovelReaderContent(
    state: NovelUiState,
    preferences: NovelReaderPreferences = NovelReaderPreferences(),
    bookmarks: List<LocalReadingRecord> = emptyList(),
    initialPosition: LocalReadingRecord? = null,
    onBack: () -> Unit = {},
    actions: NovelReaderActions = NovelReaderActions(),
    initiallyShowControls: Boolean = false,
    annotations: List<NovelAnnotation> = emptyList(),
    search: NovelReaderSearchUiState = NovelReaderSearchUiState()
) {
    NovelReaderTheme(preferences) {
        Surface(
            Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            val volume = state.reader
            if (volume == null) {
                NovelReaderWindow(preferences.keepScreenOn, true)
                Column(Modifier.fillMaxSize()) {
                    NovelReaderHeader(state.book?.let { displayBookTitle(it.path, it.name) }
                        ?: "小说阅读",
                        "", onBack, false, false, {})
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(), contentAlignment = Alignment.Center
                    ) {
                        if (state.busy) LoadingIndicator()
                        else Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                state.error ?: "本卷暂时无法打开",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = actions.retry) {
                                Text(
                                    "重试",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            } else key(volume.book.path, volume.volume.id) {
                NovelReaderBody(
                    state,
                    volume,
                    preferences,
                    bookmarks,
                    initialPosition,
                    onBack,
                    actions,
                    initiallyShowControls,
                    annotations,
                    search
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NovelReaderBody(
    state: NovelUiState, volume: NovelReadingVolume, preferences: NovelReaderPreferences,
    bookmarks: List<LocalReadingRecord>, initialPosition: LocalReadingRecord?,
    onBack: () -> Unit, actions: NovelReaderActions, initiallyShowControls: Boolean,
    annotations: List<NovelAnnotation>,
    search: NovelReaderSearchUiState
) {
    val document = remember(volume.document, volume.sections) {
        NovelReaderDocument(
            volume.document,
            volume.sections
        )
    }

    fun fromRecord(record: LocalReadingRecord?): NovelReaderLocation {
        val line = document.source.clampLine(record?.line ?: 0)
        val image = document.imageItem(record?.section.orEmpty()) != null
        return NovelReaderLocation(
            line,
            document.source.clampCharacter(line, record?.character ?: 0),
            if (record?.section?.let { key -> document.images.any { it.key == key } } == true && !image) 0.0
            else record?.fraction ?: 0.0,
            if (image) record?.section.orEmpty() else document.sectionAt(line, volume.sections),
            record?.completed ?: false)
    }

    var location by remember { mutableStateOf(fromRecord(initialPosition ?: state.restore)) }
    var navigation by remember { mutableIntStateOf(0) }
    var appliedRevision by remember { mutableIntStateOf(state.anchorRevision) }
    var annotationDraft by rememberSaveable(stateSaver = NovelSelectionSaver) {
        mutableStateOf<NovelTextSelection?>(null)
    }
    var annotationSaving by remember { mutableStateOf(false) }
    var annotationSaveFailed by remember { mutableStateOf(false) }
    var editingAnnotationId by rememberSaveable { mutableStateOf<String?>(null) }
    val searchRange = search.selected?.takeIf { hit ->
        hit.volumeId == volume.volume.id && search.bookPath == volume.book.path &&
                volume.document.textIn(hit.match.range)
                    ?.equals(search.query, ignoreCase = true) == true
    }?.match?.range
    val volumeAnnotations = remember(annotations, volume) {
        annotations.filter { it.volume == volume.volume.id && volume.document.textIn(it.range) == it.text }
    }
    var pageLabel by remember { mutableStateOf<String?>(null) }
    val textMenu = remember { NovelReaderTextMenuState() }
    var controlsVisible by rememberSaveable { mutableStateOf(initiallyShowControls) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var seek by remember { mutableStateOf(false) }
    var galleryKey by rememberSaveable {
        mutableStateOf(initialPosition?.section?.takeIf { key ->
            document.images.any { it.key == key } && document.imageItem(key) == null
        })
    }
    LaunchedEffect(state.error) { if (state.error != null) controlsVisible = true }
    LaunchedEffect(document, state.anchorRevision) {
        if (appliedRevision != state.anchorRevision) {
            controlsVisible = false
            location = fromRecord(state.restore)
            appliedRevision = state.anchorRevision
            navigation++
            galleryKey = state.restore?.section?.takeIf { key ->
                document.images.any { it.key == key } && document.imageItem(key) == null
            }
        } else location = location.copy(
            line = document.source.clampLine(location.line),
            character = document.source.clampCharacter(location.line, location.character)
        )
    }
    fun jump(line: Int, section: String = "") {
        val target = document.source.clampLine(line)
        val imageAtLine =
            document.entries.firstOrNull { it is NovelReaderEntry.Image && it.line == target } as? NovelReaderEntry.Image
        location = NovelReaderLocation(target, section = imageAtLine?.section?.key ?: section)
        navigation++
        controlsVisible = false
    }

    val chapters = volume.sections.filter { it.type == 1 && it.start < document.source.lines.size }
    val chapter = chapters.indexOfLast { location.line >= it.start }.coerceAtLeast(0)
    val chapterTitle = chapters.getOrNull(chapter)?.name?.trim() ?: volume.volume.name
    val isBookmarked = bookmarks.any {
        it.chapter == volume.volume.id && it.line == location.line && it.character == location.character &&
                it.section == location.section && abs(it.fraction - location.fraction) < .02
    }
    val fraction = if (location.completed) 1f else {
        val length =
            document.source.lines[document.source.clampLine(location.line)].length.coerceAtLeast(1)
        ((location.line + location.character.toDouble() / length) / document.source.lines.size).toFloat()
            .coerceIn(0f, 1f)
    }
    val overlayVisible =
        controlsVisible || sheet != null || galleryKey != null || seek || annotationDraft != null || search.visible
    NovelReaderWindow(preferences.keepScreenOn, overlayVisible)
    BackHandler(enabled = controlsVisible && sheet == null && galleryKey == null && !seek && annotationDraft == null && !search.visible) {
        controlsVisible = false
    }
    // 系统栏临时出现时，正文只保留固定的挖孔安全区，避免分页和阅读位置跳动。
    val stableInsets =
        WindowInsets.displayCutout
    Box(Modifier.fillMaxSize()) {
        NovelReaderTextMenuHost(
            state = textMenu,
            document = volume.document,
            onAnnotate = { selection ->
                annotationSaving = false
                annotationSaveFailed = false
                controlsVisible = false; editingAnnotationId = null; annotationDraft = selection
            },
            onSearch = { query -> controlsVisible = false; actions.openSearch(query) },
            dismissKey = listOf(document, preferences, navigation, galleryKey),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(stableInsets)
        ) {
            NovelReaderViewport(
                volume,
                document,
                preferences,
                location,
                navigation,
                onLocation = { location = it; actions.position(it) },
                onPageLabel = { pageLabel = it },
                onImage = { controlsVisible = false; galleryKey = it },
                onToggleControls = { textMenu.dismiss(); controlsVisible = !controlsVisible },
                onDismissControls = { controlsVisible = false },
                controlsVisible = controlsVisible,
                keyboardEnabled = LocalNovelReaderPlatform.current is com.rchdliang.copymanga_neo.multiplatform.ui.reader.DesktopReaderKeyboard,
                keyboardBlocked = sheet != null || galleryKey != null || seek || annotationDraft != null || search.visible || textMenu.active,
                annotations = volumeAnnotations,
                searchRange = searchRange,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (!search.visible && !controlsVisible && searchRange != null &&
            sheet == null && annotationDraft == null && galleryKey == null
        ) {
            NovelReaderSearchNavigation(
                search, actions.moveSearchMatch, { actions.openSearch(null) }, actions.endSearch,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
            )
        }
        val controlInsets = (novelReaderPlatform()?.statusBarControlInsets() ?: WindowInsets(0))
            .union(WindowInsets.displayCutout)
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(
                    controlInsets.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(max = 640.dp),
            enter = fadeIn(tween(180)) + slideInVertically { -it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically { -it / 2 }
        ) {
            Column {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp, shadowElevation = 6.dp
                ) {
                    NovelReaderHeader(
                        displayBookTitle(volume.book.path, volume.book.name),
                        volume.volume.name,
                        onBack,
                        true,
                        isBookmarked,
                        { actions.position(location); actions.bookmark() },
                        floating = true,
                        onSearch = { controlsVisible = false; actions.openSearch(null) }
                    )
                }
                if (state.caching || state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { error ->
                    Surface(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            Modifier.padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                error,
                                Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            TextButton(onClick = actions.retry, enabled = !state.refreshing) {
                                Text("重试", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    controlInsets.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(max = 640.dp),
            enter = fadeIn(tween(180)) + slideInVertically { it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically { it / 2 }
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp
            ) {
                Column {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            enabled = chapter > 0 || volume.volume.previous != null,
                            onClick = {
                                if (chapter > 0) jump(
                                    chapters[chapter - 1].start,
                                    chapters[chapter - 1].key
                                )
                                else {
                                    controlsVisible =
                                        false; volume.volume.previous?.let(actions.volume)
                                }
                            }) { Icon(Icons.Outlined.ChevronLeft, "上一章 / 上一卷") }
                        Column(Modifier.weight(1f)) {
                            Text(
                                chapterTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                pageLabel ?: volume.volume.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { controlsVisible = false; seek = true }) {
                            Text(
                                "${(fraction * 100).roundToInt()}%",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            enabled = chapter < chapters.lastIndex || volume.volume.next != null,
                            onClick = {
                                if (chapter < chapters.lastIndex) jump(
                                    chapters[chapter + 1].start,
                                    chapters[chapter + 1].key
                                )
                                else {
                                    controlsVisible = false; volume.volume.next?.let(actions.volume)
                                }
                            }) { Icon(Icons.Outlined.ChevronRight, "下一章 / 下一卷") }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        NovelReaderTool(
                            "目录",
                            Icons.AutoMirrored.Outlined.MenuBook,
                            Modifier.weight(1f)
                        ) { controlsVisible = false; sheet = "directory"; actions.loadVolumes() }
                        NovelReaderTool(
                            "插图",
                            Icons.Outlined.Collections,
                            Modifier.weight(1f),
                            document.images.isNotEmpty()
                        ) { controlsVisible = false; sheet = "images" }
                        NovelReaderTool("书签", Icons.Outlined.Bookmarks, Modifier.weight(1f)) {
                            controlsVisible = false; sheet = "bookmarks"
                        }
                        NovelReaderTool("设置", Icons.Outlined.Settings, Modifier.weight(1f)) {
                            controlsVisible = false; sheet = "settings"
                        }
                    }
                }
            }
        }
    }
    if (search.visible) NovelReaderSearchOverlay(
        search, actions.search, actions.cancelSearch, actions.searchMatch, actions.closeSearch
    )
    if (sheet == "directory" || sheet == "bookmarks") NovelReaderDirectoryOverlay(
        state = state, currentLine = location.line, bookmarks = bookmarks,
        annotations = annotations,
        onAnnotation = { sheet = null; actions.openAnnotation(it) },
        onEditAnnotation = { item ->
            annotationSaving = false
            annotationSaveFailed = false
            sheet = null; editingAnnotationId = item.id
            annotationDraft = NovelTextSelection(item.text, item.range)
        },
        onDeleteAnnotation = actions.removeAnnotation,
        showBookmarks = sheet == "bookmarks",
        onSection = { jump(it.start, it.key); sheet = null },
        onVolume = { id -> sheet = null; if (id != volume.volume.id) actions.volume(id) },
        onReload = actions.loadVolumes,
        onBookmark = { sheet = null; actions.openBookmark(it) },
        onDeleteBookmark = actions.removeBookmark,
        onDismiss = { sheet = null }
    )
    if (sheet != null && sheet != "directory" && sheet != "bookmarks") if (LocalRouteActive.current) ModalBottomSheet(
        onDismissRequest = { sheet = null },
        contentWindowInsets = { WindowInsets.displayCutout },
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.88f)
        ) {
            NovelSheetHeader(
                when (sheet) {
                    "directory" -> "目录"
                    "images" -> "本卷插图 · ${document.images.size} 张"
                    "bookmarks" -> "本书书签"
                    else -> "阅读设置"
                }
            ) { sheet = null }
            when (sheet) {
                "images" -> NovelReaderImageGrid(volume, document.images) {
                    galleryKey = it; sheet = null
                }

                else -> NovelReaderSettings(
                    preferences = preferences,
                    onPreferences = actions.preferences
                )
            }
        }
    }
    annotationDraft?.let { selected ->
        val existing = annotations.firstOrNull {
            if (editingAnnotationId != null) it.id == editingAnnotationId
            else it.volume == volume.volume.id && it.range == selected.range && it.text == selected.text
        }
        NovelAnnotationEditor(
            selected, existing, onSave = { style, note ->
                annotationSaving = true
                annotationSaveFailed = false
                val finished: (Boolean) -> Unit = { success ->
                    if (annotationDraft == selected) {
                        annotationSaving = false
                        annotationSaveFailed = !success
                        if (success) {
                            annotationDraft = null
                            editingAnnotationId = null
                        }
                    }
                }
                if (existing == null) actions.addAnnotation(selected, style, note, finished)
                else actions.updateAnnotation(existing.copy(style = style, note = note), finished)
            }, onDismiss = { annotationDraft = null; editingAnnotationId = null },
            saving = annotationSaving, saveFailed = annotationSaveFailed
        )
    }
    galleryKey?.let { selected ->
        key(selected) {
            NovelReaderGallery(volume, document.images, selected) {
                galleryKey = null
            }
        }
    }
    if (seek) {
        var value by remember { mutableStateOf((fraction * 100).roundToInt().toString()) }
        val progress = value.toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..100f }
        if (LocalRouteActive.current) AlertDialog(
            onDismissRequest = { seek = false },
            title = { Text("跳转阅读进度", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                OutlinedTextField(
                    value = value, onValueChange = { value = it }, singleLine = true,
                    label = {
                        Text(
                            "百分比（0–100）",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            },
            confirmButton = {
                TextButton(enabled = progress != null, onClick = {
                    progress?.let { jump((it / 100 * document.source.lines.lastIndex).roundToInt()) }; seek =
                    false
                }) { Text("跳转", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { seek = false }) {
                    Text(
                        "取消",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovelReaderHeader(
    title: String, subtitle: String, onBack: () -> Unit,
    enabled: Boolean, bookmarked: Boolean, onBookmark: () -> Unit,
    floating: Boolean = false,
    onSearch: (() -> Unit)? = null
) {
    TopAppBar(
        windowInsets = if (floating) WindowInsets(0) else TopAppBarDefaults.windowInsets,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (floating) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface
        ),
        title = {
            Column {
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotBlank()) Text(
                    subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    "返回小说详情"
                )
            }
        },
        actions = {
            if (onSearch != null) IconButton(onClick = onSearch) {
                Icon(
                    Icons.Outlined.Search,
                    "搜索正文"
                )
            }
            IconButton(onClick = onBookmark, enabled = enabled) {
                Icon(
                    if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkAdd,
                    if (bookmarked) "移除当前位置书签" else "添加当前位置书签",
                    tint = if (bookmarked) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
        })
}

@Composable
private fun NovelReaderTool(
    label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = LocalContentColor.current
            )
        }
    }
}

internal fun previewNovelVolume(): NovelReadingVolume {
    val paragraphs = listOf(
        "第一章 旅途的开始", "",
        "清晨，远处的山峦还笼罩在薄雾中。车轮沿着石板路缓缓向前，街边的店铺刚刚打开木门，新一天的故事也随之展开。",
        "她停下脚步，抬头望向天空。阳光穿过云层，把树叶染成温暖的金色。这里没有熟悉的路标，却有许多值得慢慢发现的风景。",
        "“那么，就从这里开始吧。”她轻声说道，翻开地图，继续向前走去。"
    ) +
            List(30) { "风从河面吹来，带着湿润的气息。桥对岸传来钟声，下一段旅程已经在等待。沿着这条道路，她将走向未曾见过的城市。" }
    return NovelReadingVolume(
        NovelBook("preview-novel", "旅途与故事", "", emptyList()),
        NovelVolume("preview-volume", "第一卷 · 启程", 0, null, "preview-next"),
        listOf(
            NovelSection("chapter-1", "第一章 · 旅途的开始", 1, 0, 16, null),
            NovelSection("chapter-2", "第二章 · 新的城市", 1, 16, paragraphs.size, null),
            NovelSection("image-1", "插图01", 2, 0, 0, "preview://image-1"),
            NovelSection("image-2", "插图02", 2, 0, 4, "preview://image-2")
        ),
        NovelTextDocument.decode(paragraphs.joinToString("\n").encodeToByteArray(), "UTF-8"),
        emptyMap(),
        true
    )
}

@Preview(name = "小说阅读器 · 手机", showBackground = true, widthDp = 412, heightDp = 860)
@Preview(name = "小说阅读器 · 横屏平板双页", showBackground = true, widthDp = 1280, heightDp = 800)
@Preview(
    name = "小说阅读器 · 手机深色", showBackground = true, widthDp = 412, heightDp = 860,
    uiMode = 0x20 // Android-compatible preview NIGHT_YES metadata, without an Android dependency.
)
@Composable
private fun NovelReaderPreview() {
    val tablet =
        with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= 840.dp
    var preferences by remember { mutableStateOf(NovelReaderPreferences(mode = if (tablet) "PAGED" else "SCROLL")) }
    com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme(dynamicColor = false) {
        NovelReaderContent(
            NovelUiState(view = NovelView.READER, reader = previewNovelVolume()),
            preferences = preferences,
            actions = NovelReaderActions(preferences = { preferences = it })
        )
    }
}

@Preview(name = "小说阅读器 · 展开操作栏", showBackground = true, widthDp = 412, heightDp = 860)
@Preview(name = "小说阅读器 · 平板操作栏", showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
private fun NovelReaderControlsPreview() {
    com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme(dynamicColor = false) {
        NovelReaderContent(
            NovelUiState(view = NovelView.READER, reader = previewNovelVolume()),
            initiallyShowControls = true
        )
    }
}

@Preview(name = "小说阅读器 · 加载", showBackground = true, widthDp = 412, heightDp = 860)
@Composable
private fun NovelReaderLoadingPreview() {
    com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme(dynamicColor = false) {
        NovelReaderContent(NovelUiState(view = NovelView.READER, busy = true))
    }
}

@Preview(name = "小说阅读设置 · 手机", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
private fun NovelReaderSettingsPreview() {
    var preferences by remember { mutableStateOf(NovelReaderPreferences()) }
    com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme(dynamicColor = false) {
        Surface {
            Column(Modifier.fillMaxSize()) {
                NovelSheetHeader("阅读设置") {}
                NovelReaderSettings(
                    preferences = preferences,
                    onPreferences = { preferences = it }
                )
            }
        }
    }
}

@Preview(name = "小说插图入口", showBackground = true, widthDp = 412, heightDp = 760)
@Composable
private fun NovelReaderImagesPreview() {
    val volume = previewNovelVolume()
    com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme(dynamicColor = false) {
        Surface {
            Column(Modifier.fillMaxSize()) {
                NovelSheetHeader("本卷插图") {}
                NovelReaderImageGrid(volume, volume.sections.filter { it.type == 2 }) {}
            }
        }
    }
}
