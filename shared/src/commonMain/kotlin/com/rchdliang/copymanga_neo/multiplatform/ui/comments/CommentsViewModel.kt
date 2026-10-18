package com.rchdliang.copymanga_neo.multiplatform.ui.comments

import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.CommentKind
import com.rchdliang.copymanga_neo.domain.model.CommentPolicy
import com.rchdliang.copymanga_neo.domain.model.CommentReplyPreview
import com.rchdliang.copymanga_neo.domain.model.CommentTarget
import com.rchdliang.copymanga_neo.domain.model.CommentText
import com.rchdliang.copymanga_neo.domain.model.DownloadedChapterComments
import com.rchdliang.copymanga_neo.domain.model.MangaComment
import com.rchdliang.copymanga_neo.domain.repository.CommentClosedException
import com.rchdliang.copymanga_neo.domain.repository.CommentRepository
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

data class CommentFeed(
    val items: List<MangaComment> = emptyList(),
    val total: Int = 0,
    val nextOffset: Int? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val totalExact: Boolean = true,
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    val downloaded: Boolean = false,
    val downloadComplete: Boolean = false
)

/** 阅读器裁剪窗口时仅保存文字和回复 ID，避免草稿持有整份评论数据。 */
data class CommentDraftSnapshot(val root: String, val replies: Map<String, String>) {
    val isEmpty get() = root.isBlank() && replies.values.all { it.isBlank() }
}

data class CommentsUiState(
    val reportTarget: MangaComment? = null,
    val reportConfig: com.rchdliang.copymanga_neo.domain.model.CommentReportConfig? = null,
    val reportLoading: Boolean = false,
    val reporting: Boolean = false,
    val reportError: String? = null,
    val target: CommentTarget? = null,
    val feed: CommentFeed = CommentFeed(),
    val parent: MangaComment? = null,
    val replyTo: MangaComment? = null,
    val draft: String = "",
    val loggedIn: Boolean = false,
    val posting: Boolean = false,
    val postingAllowed: Boolean = true,
    val checkingPermission: Boolean = false,
    val permissionError: String? = null,
    val message: String? = null,
    val replyPreviews: Map<String, CommentReplyPreview> = emptyMap(),
    val previewGeneration: Long = 0,
    val closed: Boolean = false,
    val canDownloadComments: Boolean = false,
    val downloadingComments: Boolean = false
)

/** 一个打开的评论窗口只有一个活动请求上下文，切章、退出和切账号都会失效旧请求。 */
class CommentsViewModel constructor(
    private val repository: CommentRepository,
    private val users: UserRepository,
    private val manga: MangaRepository,
    private val downloads: DownloadRepository
) : ViewModel() {
    private val state = MutableStateFlow(CommentsUiState(loggedIn = users.isLoggedInSync()))
    val uiState = state.asStateFlow()
    private var rootFeed = CommentFeed()
    private var rootDraft = ""
    private var pendingReaderDrafts: CommentDraftSnapshot? = null
    private val replyDrafts = mutableMapOf<String, String>()
    private var readJob: Job? = null
    private var postJob: Job? = null
    private var reportJob: Job? = null
    private var permissionJob: Job? = null
    private var downloadJob: Job? = null
    private var permissionPathWord: String? = null
    private var revision = 0L
    private var previewGeneration = 0L
    private val previewJobs = mutableMapOf<String, Job>()
    private val previewPermits = Semaphore(2)
    private val handler = CoroutineExceptionHandler { _, error ->
        UiLog.e("Comments", "评论操作失败", error)
        state.update {
            it.copy(
                posting = false,
                reporting = false,
                reportLoading = false,
                downloadingComments = false,
                reportError = if (it.reportTarget != null) "举报操作失败，请重试" else it.reportError,
                feed = it.feed.copy(loading = false, refreshing = false),
                message = "评论操作失败，请重试"
            )
        }
    }

    init {
        viewModelScope.launch(handler) {
            var previous: Pair<Boolean, String?>? = null
            combine(users.isLoggedIn(), users.getUsername()) { loggedIn, name -> loggedIn to name }
                .distinctUntilChanged().collect { account ->
                    if (previous != null && previous != account) {
                        postJob?.cancel()
                        reportJob?.cancel()
                        state.update {
                            it.copy(
                                reportTarget = null, reporting = false, reportLoading = false,
                                posting = false,
                                message = "登录状态已变化，请确认后再发表"
                            )
                        }
                    }
                    previous = account
                    state.update { it.copy(loggedIn = account.first) }
                }
        }
    }

    fun open(target: CommentTarget, postingAllowed: Boolean, comicPathWord: String? = null) {
        close()
        permissionPathWord =
            comicPathWord?.takeIf { it.isNotBlank() && target.kind == CommentKind.CHAPTER }
        state.value = CommentsUiState(
            target = target,
            loggedIn = users.isLoggedInSync(),
            postingAllowed = postingAllowed && permissionPathWord == null,
            checkingPermission = postingAllowed && permissionPathWord != null
        )
        rootFeed = CommentFeed()
        rootDraft = ""
        replyDrafts.clear()
        pendingReaderDrafts?.let { saved ->
            rootDraft = saved.root
            replyDrafts.putAll(saved.replies)
            state.update { it.copy(draft = saved.root) }
        }
        pendingReaderDrafts = null
        load(refresh = true)
        if (postingAllowed && permissionPathWord != null) retryPermission()
    }

    /** 阅读器按漫画详情的 close_roast 确认章节评论权限，不因未知状态放行发表。 */
    fun retryPermission() {
        val pathWord = permissionPathWord ?: return
        val target = state.value.target ?: return
        permissionJob?.cancel()
        state.update {
            it.copy(
                postingAllowed = false,
                checkingPermission = true,
                permissionError = null
            )
        }
        permissionJob = viewModelScope.launch(handler) {
            (withTimeoutOrNull(15_000L.milliseconds) { manga.getComicDetail(pathWord) }
                ?: Result.failure(com.rchdliang.copymanga_neo.domain.platform.PlatformIOException("确认评论权限超时"))).fold(
                onSuccess = { detail ->
                    ensureActive()
                    if (state.value.target == target) state.update {
                        it.copy(
                            checkingPermission = false,
                            postingAllowed = CommentPolicy.postingAllowed(
                                target.kind,
                                detail.comic.closeComment,
                                detail.comic.closeRoast
                            )
                        )
                    }
                },
                onFailure = {
                    ensureActive()
                    if (state.value.target == target) state.update {
                        it.copy(
                            checkingPermission = false,
                            permissionError = "无法确认评论权限，请重试"
                        )
                    }
                }
            )
        }
    }

    fun close() {
        reportJob?.cancel()
        permissionJob?.cancel()
        downloadJob?.cancel()
        permissionPathWord = null
        invalidatePreviews()
        revision++
        readJob?.cancel()
        postJob?.cancel()
        state.update { it.copy(target = null, posting = false, downloadingComments = false) }
    }

    private fun DownloadedChapterComments.toFeed(loading: Boolean = false) = CommentFeed(
        items = comments, total = total, loading = loading,
        downloaded = true, downloadComplete = complete
    )

    /** 旧下载可单独补齐评论；取消及失败均保留已有快照和草稿。 */
    fun downloadComments() {
        val current = state.value
        val target = current.target ?: return
        if (target.kind != CommentKind.CHAPTER || !current.canDownloadComments ||
            current.posting || current.reporting || current.downloadingComments
        ) return
        readJob?.cancel()
        val requestRevision = ++revision
        state.update {
            it.copy(
                downloadingComments = true,
                feed = it.feed.copy(loading = false, refreshing = false),
                message = null
            )
        }
        downloadJob = viewModelScope.launch(handler) {
            repository.downloadChapterComments(target.id).fold(
                onSuccess = { snapshot ->
                    ensureActive()
                    if (requestRevision == revision) state.update {
                        it.copy(
                            downloadingComments = false,
                            feed = snapshot.toFeed(),
                            message = "评论已下载"
                        )
                    }
                },
                onFailure = {
                    ensureActive()
                    val cached = repository.getDownloadedChapterComments(target.id).getOrNull()
                    ensureActive()
                    if (requestRevision == revision) state.update {
                        it.copy(
                            downloadingComments = false,
                            feed = cached?.toFeed() ?: it.feed,
                            message = "评论下载未完成，已保留现有内容，可重试"
                        )
                    }
                }
            )
        }
    }

    fun setDraft(value: String) {
        if (!state.value.posting) state.update { it.copy(draft = value) }
    }

    fun hasDraft(): Boolean =
        state.value.draft.isNotBlank() || rootDraft.isNotBlank() || replyDrafts.values.any { it.isNotBlank() }

    fun captureReaderDrafts(): CommentDraftSnapshot {
        pendingReaderDrafts?.let { if (state.value.target == null) return it }
        saveDraft()
        return CommentDraftSnapshot(rootDraft, replyDrafts.filterValues { it.isNotBlank() }.toMap())
    }

    fun restoreReaderDrafts(value: CommentDraftSnapshot) { pendingReaderDrafts = value }

    fun clearMessage() {
        state.update { it.copy(message = null) }
    }

    /** 举报独立于评论草稿，关闭或切换账号时取消请求。 */
    fun openReport(comment: MangaComment) {
        val current = state.value
        val target = current.target ?: return
        if (current.reporting || current.posting) return
        if (!current.loggedIn) {
            state.update { it.copy(message = "请先到“我的”登录后举报") }
            return
        }
        reportJob?.cancel()
        state.update {
            it.copy(
                reportTarget = comment,
                reportConfig = null,
                reportLoading = true,
                reportError = null
            )
        }
        reportJob = viewModelScope.launch(handler) {
            repository.getReportConfig(target).fold(
                onSuccess = { config ->
                    ensureActive()
                    state.update { it.copy(reportConfig = config, reportLoading = false) }
                },
                onFailure = { error ->
                    ensureActive()
                    state.update {
                        it.copy(
                            reportLoading = false,
                            reportError = error.message ?: "举报类型加载失败"
                        )
                    }
                }
            )
        }
    }

    fun closeReport() {
        if (state.value.reporting) return
        reportJob?.cancel()
        state.update { it.copy(reportTarget = null, reportLoading = false, reportError = null) }
    }

    fun report(type: Int, reason: String) {
        val current = state.value
        val target = current.target ?: return
        val comment = current.reportTarget ?: return
        if (current.reporting || current.reportLoading || !current.loggedIn ||
            CommentText.length(reason) > CommentText.MAX_LENGTH ||
            current.reportConfig?.types?.none { it.type == type } != false
        ) return
        state.update { it.copy(reporting = true, reportError = null) }
        reportJob = viewModelScope.launch(handler) {
            repository.reportComment(target, comment.id, type, reason).fold(
                onSuccess = {
                    ensureActive()
                    state.update {
                        it.copy(
                            reporting = false,
                            reportTarget = null,
                            message = "举报已提交"
                        )
                    }
                },
                onFailure = { error ->
                    ensureActive()
                    state.update {
                        it.copy(
                            reporting = false, reportError = (error.message ?: "举报失败") +
                                    if (error is com.rchdliang.copymanga_neo.domain.platform.PlatformIOException) "。结果可能未确认，请勿重复提交。" else ""
                        )
                    }
                }
            )
        }
    }

    private fun saveDraft() {
        val current = state.value
        val id = (current.replyTo ?: current.parent)?.id
        if (id == null) rootDraft = current.draft else replyDrafts[id] = current.draft
    }

    /** 直接打开输入面板时保留列表位置，并按回复对象隔离草稿。 */
    fun startReply(comment: MangaComment) {
        if (state.value.posting || state.value.target?.kind !in setOf(CommentKind.COMIC, CommentKind.NOVEL)) return
        saveDraft()
        state.update {
            it.copy(
                replyTo = comment,
                draft = replyDrafts[comment.id].orEmpty(),
                message = null
            )
        }
    }

    fun startComment() {
        if (state.value.posting) return
        saveDraft()
        val parent = state.value.parent
        state.update {
            it.copy(
                replyTo = null,
                draft = if (parent == null) rootDraft else replyDrafts[parent.id].orEmpty(),
                message = null
            )
        }
    }

    private fun invalidatePreviews() {
        previewGeneration++
        previewJobs.values.forEach { it.cancel() }
        previewJobs.clear()
        state.update { it.copy(replyPreviews = emptyMap(), previewGeneration = previewGeneration) }
    }

    /** 只为可见评论加载预览；失败不影响主列表，也不在重组时反复重试。 */
    fun loadReplyPreview(comment: MangaComment) {
        val current = state.value
        val target = current.target ?: return
        if (target.kind == CommentKind.CHAPTER || current.parent != null || comment.replyCount <= 0 ||
            comment.id in current.replyPreviews || comment.id in previewJobs
        ) return
        val generation = previewGeneration
        previewJobs[comment.id] = viewModelScope.launch(handler) {
            previewPermits.withPermit {
                repository.getReplyPreview(target, comment.id).fold(
                    onSuccess = { preview ->
                        ensureActive()
                        if (generation == previewGeneration) state.update {
                            fun correctCount(items: List<MangaComment>) = items.map { item ->
                                if (item.id == comment.id && preview.total != null) item.copy(
                                    replyCount = preview.total
                                ) else item
                            }
                            if (it.parent != null) rootFeed =
                                rootFeed.copy(items = correctCount(rootFeed.items))
                            it.copy(
                                feed = if (it.parent == null) it.feed.copy(items = correctCount(it.feed.items)) else it.feed,
                                replyPreviews = it.replyPreviews + (comment.id to preview)
                            )
                        }
                    },
                    onFailure = {
                        ensureActive()
                        if (generation == previewGeneration) state.update {
                            it.copy(
                                replyPreviews = it.replyPreviews + (comment.id to CommentReplyPreview(
                                    emptyList()
                                ))
                            )
                        }
                    }
                )
            }
        }
    }

    fun showReplies(comment: MangaComment) {
        val current = state.value
        if (current.posting || current.target == null || current.target.kind == CommentKind.CHAPTER) return
        if (current.parent == null) {
            saveDraft()
            rootFeed = current.feed.copy(loading = false, refreshing = false)
            readJob?.cancel()
            revision++
            state.update {
                it.copy(
                    parent = comment,
                    replyTo = comment,
                    draft = replyDrafts[comment.id].orEmpty(),
                    feed = CommentFeed()
                )
            }
            load(refresh = true)
        } else startReply(comment)
    }

    fun backToComments() {
        if (state.value.posting) return
        readJob?.cancel()
        revision++
        saveDraft()
        state.update {
            it.copy(
                parent = null,
                replyTo = null,
                draft = rootDraft,
                feed = rootFeed,
                message = null
            )
        }
    }

    fun load(refresh: Boolean, fromPullToRefresh: Boolean = false) {
        val current = state.value
        val target = current.target ?: return
        if (current.posting || current.reporting || current.downloadingComments ||
            ((!refresh || fromPullToRefresh) && current.feed.loading)
        ) return
        val offset = if (refresh) 0 else current.feed.nextOffset ?: return
        readJob?.cancel()
        val requestRevision = ++revision
        val parentId = current.parent?.id
        if (refresh && parentId == null) invalidatePreviews()
        state.update {
            it.copy(
                feed = it.feed.copy(
                    loading = true,
                    refreshing = fromPullToRefresh,
                    refreshFailed = false,
                    error = null
                )
            )
        }
        readJob = viewModelScope.launch(handler) {
            if (target.kind == CommentKind.CHAPTER && parentId == null) {
                val localChapter = downloads.getDownloadedChapter(target.id)
                val cached = if (refresh && current.feed.items.isEmpty())
                    repository.getDownloadedChapterComments(target.id).getOrNull() else null
                ensureActive()
                if (requestRevision != revision) return@launch
                state.update {
                    it.copy(
                        canDownloadComments = localChapter != null,
                        feed = cached?.toFeed(loading = true)?.copy(refreshing = fromPullToRefresh)
                            ?: it.feed
                    )
                }
            }
            repository.getComments(target, offset, parentId).fold(
                onSuccess = { page ->
                    ensureActive()
                    if (requestRevision != revision) return@fold
                    state.update {
                        val items =
                            (if (refresh) page.comments else it.feed.items + page.comments).distinctBy { comment -> comment.id }
                        val exact = parentId == null || page.nextOffset == null
                        if (parentId != null && exact) {
                            rootFeed = rootFeed.copy(items = rootFeed.items.map { root ->
                                if (root.id == parentId) root.copy(replyCount = items.size) else root
                            })
                        }
                        it.copy(
                            feed = CommentFeed(
                                items,
                                if (parentId == null) page.total else items.size,
                                page.nextOffset,
                                totalExact = exact
                            ),
                            parent = if (parentId != null && exact) it.parent?.copy(replyCount = items.size) else it.parent,
                            replyPreviews = if (parentId != null && exact) {
                                it.replyPreviews + (parentId to CommentReplyPreview(
                                    items.take(2),
                                    items.size
                                ))
                            } else it.replyPreviews
                        )
                    }
                },
                onFailure = { error ->
                    ensureActive()
                    if (requestRevision == revision) state.update {
                        it.copy(
                            postingAllowed = it.postingAllowed && error !is CommentClosedException,
                            closed = error is CommentClosedException,
                            feed = it.feed.copy(
                                loading = false,
                                refreshing = false,
                                refreshFailed = refresh,
                                error = error.message ?: "评论加载失败"
                            )
                        )
                    }
                }
            )
        }
    }

    fun post() {
        val current = state.value
        val target = current.target ?: return
        if (current.posting || current.downloadingComments || !current.postingAllowed) return
        if (!current.loggedIn) {
            state.update { it.copy(message = "请先到“我的”登录") }; return
        }
        if (!CommentText.isValid(current.draft)) {
            state.update { it.copy(message = "评论需要 3～200 个字") }; return
        }
        readJob?.cancel()
        val requestRevision = ++revision
        val reply = current.replyTo ?: current.parent
        state.update {
            it.copy(
                posting = true,
                message = null,
                feed = it.feed.copy(loading = false)
            )
        }
        postJob = viewModelScope.launch(handler) {
            repository.postComment(target, current.draft, reply?.id).fold(
                onSuccess = {
                    ensureActive()
                    if (requestRevision != revision) return@fold
                    val threadId = current.parent?.id ?: reply?.id
                    reply?.let { replyDrafts.remove(it.id) }
                    threadId?.let { id ->
                        rootFeed = rootFeed.copy(items = rootFeed.items.map {
                            if (it.id == id) it.copy(replyCount = it.replyCount + 1) else it
                        })
                    }
                    if (reply == null) rootDraft = ""
                    invalidatePreviews()
                    state.update {
                        it.copy(
                            posting = false,
                            draft = "",
                            replyTo = null,
                            message = "发表成功"
                        )
                    }
                    load(refresh = true)
                },
                onFailure = { error ->
                    ensureActive()
                    if (requestRevision == revision) state.update {
                        it.copy(
                            posting = false,
                            message = (error.message
                                ?: "发表失败") + if (error is com.rchdliang.copymanga_neo.domain.platform.PlatformIOException) "，请刷新确认是否发表成功。" else "",
                            postingAllowed = it.postingAllowed && error !is CommentClosedException
                        )
                    }
                }
            )
        }
    }
}
