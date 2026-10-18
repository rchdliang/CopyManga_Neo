package com.rchdliang.copymanga_neo.data.download

import android.content.Context
import android.util.Log
import com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore
import com.rchdliang.copymanga_neo.data.local.db.DownloadedChapterDao
import com.rchdliang.copymanga_neo.data.local.db.DownloadedComicDao
import com.rchdliang.copymanga_neo.data.local.entity.DownloadedChapterEntity
import com.rchdliang.copymanga_neo.domain.model.BookDownloadTask
import com.rchdliang.copymanga_neo.domain.model.ChapterContent
import com.rchdliang.copymanga_neo.domain.model.ChapterDownloadItem
import com.rchdliang.copymanga_neo.domain.model.ChapterDownloadStatus
import com.rchdliang.copymanga_neo.domain.model.ChapterInfo
import com.rchdliang.copymanga_neo.domain.model.ComicDownloadTask
import com.rchdliang.copymanga_neo.domain.model.DownloadKind
import com.rchdliang.copymanga_neo.domain.model.DownloadProgressInfo
import com.rchdliang.copymanga_neo.domain.model.DownloadTaskStatus
import com.rchdliang.copymanga_neo.domain.model.NovelCacheProgress
import com.rchdliang.copymanga_neo.domain.model.NovelCacheStage
import com.rchdliang.copymanga_neo.domain.model.NovelDownloadTask
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import com.rchdliang.copymanga_neo.domain.model.comicDownloadKey
import com.rchdliang.copymanga_neo.domain.model.matches
import com.rchdliang.copymanga_neo.domain.model.mergeRequest
import com.rchdliang.copymanga_neo.domain.model.novelDownloadKey
import com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository
import com.rchdliang.copymanga_neo.domain.repository.CommentRepository
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.NovelRepository
import com.rchdliang.copymanga_neo.domain.repository.ReadingSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import com.rchdliang.copymanga_neo.domain.repository.RiskControlException as SiteRiskControlException

class RiskControlException(message: String) : IOException(message)
private class DownloadNetworkUnavailableException : IOException("等待网络连接")

@Singleton
class DownloadManager internal constructor(
    private val context: Context,
    private val mangaRepository: MangaRepository,
    private val comicDao: DownloadedComicDao,
    private val chapterDao: DownloadedChapterDao,
    private val okHttpClient: OkHttpClient,
    private val startForegroundDownload: () -> Unit,
    private val queueStore: DownloadQueueStore? = null,
    private val networkPolicy: DownloadNetworkPolicy? = null,
    networkSettings: NetworkSettingsStore? = null,
    private val commentsRepository: CommentRepository? = null,
    private val localAccess: LocalDownloadAccess = LocalDownloadAccess(),
    private val novels: NovelRepository? = null,
    private val sessions: ReadingSessionRepository? = null,
    private val appSettings: AppSettingsRepository? = null
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        mangaRepository: MangaRepository,
        comicDao: DownloadedComicDao,
        chapterDao: DownloadedChapterDao,
        okHttpClient: OkHttpClient,
        queueStore: DownloadQueueStore,
        networkPolicy: DownloadNetworkPolicy,
        networkSettings: NetworkSettingsStore,
        commentsRepository: CommentRepository,
        localAccess: LocalDownloadAccess,
        novels: NovelRepository,
        sessions: ReadingSessionRepository,
        appSettings: AppSettingsRepository
    ) : this(
        context, mangaRepository, comicDao, chapterDao, okHttpClient, {
            DownloadService.start(context)
        },
        queueStore,
        networkPolicy,
        networkSettings,
        commentsRepository,
        localAccess,
        novels,
        sessions,
        appSettings
    )

    companion object {
        private const val TAG = "DownloadManager"
        private const val DOWNLOAD_DIR = "downloads"
    }

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "下载任务处理失败", error)
        })
    private val queue = Channel<BookDownloadTask>(UNLIMITED)

    @Volatile
    private var imageConcurrency = 3
    private val bandwidthLimiter = DownloadBandwidthLimiter()
    private val downloadSettingsReady = CompletableDeferred<Unit>()

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgressInfo>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgressInfo>> =
        _downloadProgress.asStateFlow()

    private val _activeTask = MutableStateFlow<BookDownloadTask?>(null)
    val activeTask: StateFlow<BookDownloadTask?> = _activeTask.asStateFlow()

    private val mutableTasks = MutableStateFlow<List<BookDownloadTask>>(emptyList())
    val tasks: StateFlow<List<BookDownloadTask>> = mutableTasks.asStateFlow()

    // Copy-on-write 快照：所有写操作经 stateMutex 串行化；读操作无锁读取 @Volatile 引用，得到的可能稍旧但安全。
    @Volatile
    private var currentTasks: Map<String, BookDownloadTask> = emptyMap()
        set(value) {
            field = value; mutableTasks.value = value.values.toList()
        }

    @Volatile
    private var pausedTasks: Set<String> = emptySet()

    /** 网络等待不等同于手动暂停，连接恢复时只继续此集合中的任务。 */
    @Volatile
    private var networkWaiting: Set<String> = emptySet()

    /** 对 currentTasks / pausedTasks 写操作的串行化锁。读不持锁，见上注释。 */
    private val stateMutex = Mutex()
    private val pauseRevisions = mutableMapOf<String, Long>()
    private val cacheRemovals = mutableSetOf<String>()

    /** 当前作品任务与漫画章节的完成信号，用于让取消/删除操作等待后台 I/O 真正退出。 */
    private val activeTaskCompletions = mutableMapOf<String, CompletableDeferred<Unit>>()
    private val activeChapterCompletions = mutableMapOf<String, CompletableDeferred<Unit>>()

    /** 当前图片请求；取消漫画或章节时会主动终止对应的 OkHttp Call。 */
    private val activeCalls = ConcurrentHashMap<okhttp3.Call, String>()
    private val activeRequests = ConcurrentHashMap<String, Job>()

    private val restoration = scope.async { restoreQueue() }

    init {
        if (appSettings == null) downloadSettingsReady.complete(Unit)
        else scope.launch {
            try {
                appSettings.settings.collect { settings ->
                    imageConcurrency = settings.comicImageConcurrency.coerceIn(1, 10)
                    bandwidthLimiter.limitKiB = settings.comicDownloadLimitKiB.coerceAtLeast(0)
                    downloadSettingsReady.complete(Unit)
                }
            } catch (error: Exception) {
                downloadSettingsReady.completeExceptionally(error)
                throw error
            }
        }
        networkSettings?.onChange { scope.launch { pauseNovelTasks("连接设置已变化，请确认后继续") } }
        sessions?.let { sessionRepository ->
            scope.launch {
                sessionRepository.awaitReady()
                var previous = sessionRepository.getReadingSessionSync()
                sessionRepository.sessionChanges.collect {
                    val next = sessionRepository.getReadingSessionSync()
                    if (next != previous) pauseNovelTasks("账号或请求模式已变化，请确认后继续")
                    previous = next
                }
            }
        }
    }

    private fun novelSessionAllowed(task: NovelDownloadTask): Boolean =
        task.matches(sessions?.getReadingSessionSync())

    private suspend fun pauseNovelTasks(reason: String) {
        restoration.await()
        currentTasks.values.filterIsInstance<NovelDownloadTask>().forEach { task ->
            pauseDownload(task.taskKey).join()
            stateMutex.withLock {
                updateProgress(
                    task.taskKey,
                    status = DownloadTaskStatus.PAUSED,
                    statusText = reason
                )
            }
        }
    }

    init {
        scope.launch { restoration.await(); processQueue() }
        networkPolicy?.let { policy ->
            scope.launch {
                restoration.await()
                policy.ready.await()
                var previous = policy.state.value
                policy.state.collect { next ->
                    // 即使仍允许下载，切换绑定或限制开关也必须撤销旧连接。
                    if (next.bindingChangedFrom(previous)) applyNetworkPolicy(false)
                    previous = next
                    applyNetworkPolicy(next.allowed)
                }
            }
        }
    }

    /** 恢复为暂停状态，由用户点击继续；已完成章节只认完整的本地文件。 */
    private suspend fun restoreQueue() {
        novels?.refreshCachedVolumes()
        val saved = queueStore?.load().orEmpty()
        stateMutex.withLock {
            saved.forEach { task ->
                val complete = when (task) {
                    is ComicDownloadTask -> chapterDao.getChaptersOnce(task.pathWord)
                        .filter { it.hasCompleteFiles() }.map { it.uuid }.toSet()

                    is NovelDownloadTask -> novels?.cachedVolumes?.value.orEmpty()
                        .filter { it.mode == task.mode && it.owner == task.owner && it.book.path == task.pathWord && it.visibleInDownloads && it.offlineReady }
                        .map { it.volume.id }.toSet()
                }
                val restored = task.withItems(task.items.map {
                    val done =
                        it.id in complete && (task is NovelDownloadTask || commentsRepository == null ||
                                commentsRepository.getDownloadedChapterComments(it.id)
                                    .getOrNull()?.complete == true)
                    it.withState(
                        status = if (done) ChapterDownloadStatus.DOWNLOADED else ChapterDownloadStatus.WAITING,
                        progress = if (done) 1f else 0f, errorMessage = null
                    )
                })
                if (restored.items.all { it.status == ChapterDownloadStatus.DOWNLOADED }) {
                    queueStore?.remove(restored.taskKey)
                } else {
                    currentTasks = currentTasks + (restored.taskKey to restored)
                    pausedTasks = pausedTasks + restored.taskKey
                    updateProgressForTask(restored)
                    updateProgress(
                        restored.taskKey, status = DownloadTaskStatus.PAUSED,
                        statusText = "队列已恢复，点击继续下载", speedBytesPerSecond = 0L
                    )
                }
            }
        }
    }

    /** 入队与修改章节时先落盘；保存失败时暂停，避免产生无法恢复的任务。 */
    private suspend fun persistTask(task: BookDownloadTask): Boolean = try {
        queueStore?.save(task)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "保存下载队列失败", e)
        pausedTasks = pausedTasks + task.taskKey
        cancelImageCalls(task.taskKey)
        activeRequests[task.taskKey]?.cancel()
        updateProgress(
            task.taskKey,
            status = DownloadTaskStatus.PAUSED,
            statusText = "队列保存失败，请重试",
            errorMessage = "无法保存下载队列",
            speedBytesPerSecond = 0L
        )
        false
    }

    private fun networkAllowed() = networkPolicy?.state?.value?.allowed != false

    private fun waitingForNetwork() = if (networkPolicy?.state?.value?.wifiOnly != false)
        "等待 Wi-Fi 连接，连接后自动继续" else "等待网络连接，连接后自动继续"

    private fun downloadClient(): OkHttpClient = networkPolicy?.client(okHttpClient) ?: okHttpClient

    private suspend fun applyNetworkPolicy(allowed: Boolean) {
        if (!allowed) {
            val calls = stateMutex.withLock {
                currentTasks.values.filter { task ->
                    task.taskKey !in pausedTasks && task.items.any {
                        it.status == ChapterDownloadStatus.WAITING || it.status == ChapterDownloadStatus.DOWNLOADING
                    }
                }.forEach { task ->
                    networkWaiting = networkWaiting + task.taskKey
                    updateProgress(
                        task.taskKey, status = DownloadTaskStatus.WAITING_FOR_WIFI,
                        statusText = waitingForNetwork(), speedBytesPerSecond = 0L
                    )
                }
                activeCalls.filterValues { it in networkWaiting }.keys.toList()
            }
            calls.forEach { it.cancel() }
            activeRequests.filterKeys { it in networkWaiting }.values.forEach { it.cancel() }
        } else {
            networkWaiting.toList().forEach { pathWord ->
                scope.launch {
                    val stopping = stateMutex.withLock { activeTaskCompletions[pathWord] }
                    stopping?.await()
                    stateMutex.withLock {
                        val task = currentTasks[pathWord] ?: return@withLock
                        if (!networkAllowed() || pathWord !in networkWaiting || pathWord in pausedTasks) return@withLock
                        networkWaiting = networkWaiting - pathWord
                        val ready = task.withItems(task.items.map {
                            if (it.status == ChapterDownloadStatus.DOWNLOADING) it.withState(
                                status = ChapterDownloadStatus.WAITING,
                                progress = 0f
                            )
                            else it
                        })
                        currentTasks = currentTasks + (pathWord to ready)
                        updateProgressForTask(ready)
                        queue.trySend(ready)
                    }
                }
            }
        }
    }

    /** 保留旧入口，统一写入持久化设置；每章启动时固定工作协程数量。 */
    fun setImageConcurrency(count: Int) {
        if (appSettings == null) imageConcurrency = count.coerceIn(1, 10)
        else scope.launch {
            appSettings.setComicImageConcurrency(count.coerceIn(1, 10)).fold(
                onSuccess = {}, onFailure = { Log.e(TAG, "保存图片并发数失败", it) }
            )
        }
    }

    private fun cancelImageCalls(key: String) {
        activeCalls.filterValues { it == key }.keys.forEach { it.cancel() }
    }

    fun enqueue(task: BookDownloadTask) {
        scope.launch {
            restoration.await()
            networkPolicy?.ready?.await()
            val stoppingDownloads = stateMutex.withLock {
                val existing = currentTasks[task.taskKey]
                if (existing == null) {
                    listOfNotNull(activeTaskCompletions[task.taskKey])
                } else {
                    val existingUuids = existing.items.map { it.id }.toSet()
                    task.items.filter { it.id !in existingUuids }
                        .mapNotNull { activeChapterCompletions[it.id] }
                }
            }
            stoppingDownloads.forEach { it.await() }
            stateMutex.withLock {
                val existing = currentTasks[task.taskKey]
                if (existing != null) {
                    // 合并：将新章节追加到已有任务中，避免覆盖正在下载的任务
                    val existingUuids = existing.items.map { it.id }.toSet()
                    val newChapters = task.items.filter { it.id !in existingUuids }
                    val mergedTask = existing.mergeRequest(task)
                    if (mergedTask.items != existing.items) {
                        currentTasks = currentTasks + (task.taskKey to mergedTask)
                        if (!persistTask(mergedTask)) return@withLock
                        updateProgressForTask(mergedTask)
                        if (ensureForeground(mergedTask)) launch { queue.send(mergedTask) }
                        Log.d(
                            TAG,
                            "enqueue (merged): ${task.name}, added ${newChapters.size} items, total ${mergedTask.items.size}"
                        )
                    } else {
                        Log.d(TAG, "enqueue: ${task.name} - all chapters already in queue, skipped")
                    }
                } else {
                    currentTasks = currentTasks + (task.taskKey to task)
                    if (!persistTask(task)) return@withLock
                    Log.d(TAG, "enqueue: ${task.name} (${task.totalChapters} chapters)")
                    updateProgressForTask(task)
                    if (ensureForeground(task)) launch { queue.send(task) }
                }
            }
        }
    }

    suspend fun cancelDownload(pathWord: String) {
        restoration.await()
        val completion = stateMutex.withLock {
            queueStore?.remove(pathWord)
            networkWaiting = networkWaiting - pathWord
            pausedTasks = pausedTasks - pathWord
            currentTasks = currentTasks - pathWord
            _downloadProgress.update { it - pathWord }
            activeTaskCompletions[pathWord]
        }
        cancelImageCalls(pathWord)
        activeRequests.remove(pathWord)?.cancel()
        completion?.await()
        stateMutex.withLock {
            if (_activeTask.value?.taskKey == pathWord && pathWord !in activeTaskCompletions) {
                _activeTask.value = null
            }
        }
        Log.d(TAG, "cancelComic: $pathWord (background I/O stopped, downloaded chapters preserved)")
    }

    fun pauseDownload(pathWord: String, recordIntent: Boolean = true) = scope.launch {
        restoration.await()
        val calls = stateMutex.withLock {
            if (pathWord !in currentTasks) return@launch
            if (recordIntent) pauseRevisions[pathWord] = (pauseRevisions[pathWord] ?: 0L) + 1L
            networkWaiting = networkWaiting - pathWord
            pausedTasks = pausedTasks + pathWord
            Log.d(TAG, "pauseComic: $pathWord")
            updateProgress(
                comicPathWord = pathWord,
                status = DownloadTaskStatus.PAUSED,
                statusText = "已暂停",
                speedBytesPerSecond = 0L
            )
            activeCalls.filterValues { it == pathWord }.keys.toList()
        }
        calls.forEach { it.cancel() }
        activeRequests[pathWord]?.cancel()
    }

    fun resumeDownload(
        pathWord: String,
        expectedTaskId: String? = null,
        expectedPauseRevision: Long? = null
    ) = scope.launch {
        restoration.await()
        networkPolicy?.ready?.await()
        // 暂停会取消当前 Call；先等旧 I/O 退出，再清除暂停标记，防止取消被误判为错误。
        val stoppingTask = stateMutex.withLock { activeTaskCompletions[pathWord] }
        stoppingTask?.await()
        stateMutex.withLock {
            if (expectedTaskId != null && (currentTasks[pathWord]?.taskId != expectedTaskId ||
                        (pauseRevisions[pathWord] ?: 0L) != expectedPauseRevision)
            ) return@withLock
            if (pathWord in cacheRemovals) return@withLock
            pausedTasks = pausedTasks - pathWord
            networkWaiting = networkWaiting - pathWord
            Log.d(TAG, "resumeComic: $pathWord")
            updateProgress(
                comicPathWord = pathWord,
                status = DownloadTaskStatus.WAITING,
                statusText = "等待继续下载",
                errorMessage = null,
                speedBytesPerSecond = 0L
            )
            currentTasks[pathWord]?.let { task ->
                val resetChapters = task.items.map { chapter ->
                    if (chapter.status == ChapterDownloadStatus.ERROR || chapter.status == ChapterDownloadStatus.DOWNLOADING) {
                        chapter.withState(
                            status = ChapterDownloadStatus.WAITING,
                            progress = 0f,
                            errorMessage = null
                        )
                    } else {
                        chapter
                    }
                }
                val resetTask = task.withItems(resetChapters)
                currentTasks = currentTasks + (pathWord to resetTask)
                if (!persistTask(resetTask)) return@withLock
                updateProgressForTask(resetTask)
                if (ensureForeground(resetTask)) launch { queue.send(resetTask) }
            }
        }
    }

    /** 通知按钮和服务超时共用同一条暂停路径，不清除已完成的章节。 */
    fun pauseAll(reason: String = "已暂停") = scope.launch {
        restoration.await()
        val calls = stateMutex.withLock {
            pausedTasks = pausedTasks + currentTasks.keys
            networkWaiting = emptySet()
            currentTasks.keys.forEach { pathWord ->
                pauseRevisions[pathWord] = (pauseRevisions[pathWord] ?: 0L) + 1L
                updateProgress(
                    pathWord,
                    status = DownloadTaskStatus.PAUSED,
                    statusText = reason,
                    errorMessage = if (reason == "已暂停") null else reason,
                    speedBytesPerSecond = 0L
                )
            }
            activeCalls.keys.toList()
        }
        calls.forEach { it.cancel() }
        activeRequests.values.forEach { it.cancel() }
    }

    suspend fun resumeAll() {
        restoration.await()
        currentTasks.keys.toList().forEach { resumeDownload(it).join() }
    }

    /** 必须先发布任务，再启动前台服务，最后将任务交给下载循环。 */
    private fun ensureForeground(task: BookDownloadTask): Boolean {
        if (task.taskKey in cacheRemovals) {
            pausedTasks = pausedTasks + task.taskKey
            updateProgress(
                task.taskKey,
                status = DownloadTaskStatus.PAUSED,
                statusText = "正在清理所选缓存"
            )
            return false
        }
        if (task is NovelDownloadTask && !novelSessionAllowed(task)) {
            pausedTasks = pausedTasks + task.taskKey
            updateProgress(
                task.taskKey, status = DownloadTaskStatus.PAUSED,
                statusText = "请切换到下载所属账号和请求模式后继续"
            )
            return false
        }
        return try {
            startForegroundDownload()
            true
        } catch (e: RuntimeException) {
            Log.e(TAG, "无法启动下载前台服务", e)
            pausedTasks = pausedTasks + task.taskKey
            updateProgress(
                task.taskKey, status = DownloadTaskStatus.PAUSED,
                statusText = "后台下载暂不可用，请返回应用后继续",
                errorMessage = "系统未允许启动后台下载", speedBytesPerSecond = 0L
            )
            false
        }
    }

    fun isPaused(pathWord: String): Boolean = comicDownloadKey(pathWord) in pausedTasks

    suspend fun removeChaptersFromTask(comicPathWord: String, chapterUuids: Set<String>) {
        removeTaskItems(comicDownloadKey(comicPathWord), chapterUuids)
    }

    private data class RemovalState(
        val taskId: String?,
        val shouldResume: Boolean,
        val pauseRevision: Long
    )

    /** 删除前终止并等待在途写入；小说须等缓存清理完成后才允许剩余分卷恢复。 */
    private suspend fun removeTaskItems(
        taskKey: String,
        ids: Set<String>,
        resumeRemaining: Boolean = true
    ): RemovalState {
        restoration.await()
        val original = stateMutex.withLock {
            if (!resumeRemaining) cacheRemovals += taskKey
            RemovalState(
                currentTasks[taskKey]?.taskId,
                taskKey !in pausedTasks,
                pauseRevisions[taskKey] ?: 0L
            )
        }
        pauseDownload(taskKey, recordIntent = false).join()
        stateMutex.withLock { activeTaskCompletions[taskKey] }?.await()
        stateMutex.withLock {
            val task = currentTasks[taskKey] ?: return@withLock
            if (task.taskId != original.taskId) return@withLock
            val items = task.items.filterNot { it.id in ids }.map {
                if (it.status == ChapterDownloadStatus.DOWNLOADING) it.withState(
                    ChapterDownloadStatus.WAITING
                ) else it
            }
            if (items.isEmpty()) {
                queueStore?.remove(taskKey)
                currentTasks = currentTasks - taskKey
                pausedTasks = pausedTasks - taskKey
                networkWaiting = networkWaiting - taskKey
                _downloadProgress.update { it - taskKey }
            } else {
                val changed = task.withItems(items)
                currentTasks = currentTasks + (taskKey to changed)
                check(persistTask(changed)) { "下载队列保存失败，已保留暂停" }
                updateProgressForTask(changed)
            }
        }
        if (resumeRemaining) resumeAfterRemoval(taskKey, original)
        return original
    }

    private suspend fun resumeAfterRemoval(taskKey: String, original: RemovalState) {
        if (original.shouldResume && original.taskId != null) {
            resumeDownload(taskKey, original.taskId, original.pauseRevision).join()
        }
    }

    /** 只移除所选分卷任务，已保存的正文与插图保留供离线阅读或继续下载。 */
    suspend fun cancelNovelVolumes(
        mode: RequestMode, owner: String, path: String, ids: Set<String>
    ): Result<Unit> = try {
        if (ids.isNotEmpty()) removeTaskItems(novelDownloadKey(mode, owner, path), ids)
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(
            IOException(
                "取消下载失败，剩余任务已暂停：${error.message ?: "请重试"}",
                error
            )
        )
    }

    suspend fun removeNovelVolumes(
        mode: RequestMode,
        owner: String,
        path: String,
        ids: Set<String>
    ): Result<Unit> {
        val repository = novels ?: return Result.failure(IOException("小说下载尚不可用"))
        val key = novelDownloadKey(mode, owner, path)
        var original: RemovalState? = null
        var removed = false
        val result = try {
            original = removeTaskItems(key, ids, resumeRemaining = false)
            ids.forEach { id ->
                repository.removeVolume(mode, owner, path, id).fold(
                    onSuccess = {}, onFailure = { throw it })
            }
            removed = true
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(
                IOException(
                    "缓存删除失败，剩余下载已暂停：${error.message ?: "请重试"}",
                    error
                )
            )
        } finally {
            withContext(NonCancellable) {
                stateMutex.withLock {
                    cacheRemovals -= key
                    if (!removed && key in currentTasks) {
                        pausedTasks = pausedTasks + key
                        updateProgress(
                            key, status = DownloadTaskStatus.PAUSED,
                            statusText = "缓存删除未完成，剩余下载已暂停"
                        )
                    }
                }
            }
        }
        if (removed) original?.let { resumeAfterRemoval(key, it) }
        return result
    }

    suspend fun cancelAllNovels() {
        restoration.await()
        (currentTasks.values.filterIsInstance<NovelDownloadTask>().map { it.taskKey } +
                _downloadProgress.value.values.filter { it.kind == DownloadKind.NOVEL }
                    .map { it.taskKey })
            .distinct().forEach { cancelDownload(it) }
    }

    suspend fun deleteChapterFiles(comicPathWord: String, chapterUuid: String) {
        withContext(Dispatchers.IO) {
            localAccess.mutate("$comicPathWord/$chapterUuid").use {
                val dir = File(context.filesDir, "$DOWNLOAD_DIR/$comicPathWord/$chapterUuid")
                if (dir.exists() && !dir.deleteRecursively()) {
                    Log.w(TAG, "Failed to delete chapter directory: ${dir.absolutePath}")
                }
            }
        }
    }

    suspend fun deleteComicFiles(comicPathWord: String) {
        withContext(Dispatchers.IO) {
            localAccess.mutate("$comicPathWord/").use {
                val dir = File(context.filesDir, "$DOWNLOAD_DIR/$comicPathWord")
                if (dir.exists() && !dir.deleteRecursively()) {
                    Log.w(TAG, "Failed to delete comic directory: ${dir.absolutePath}")
                }
            }
        }
    }

    suspend fun cancelAll() {
        restoration.await()
        val completions = stateMutex.withLock {
            queueStore?.clear()
            networkWaiting = emptySet()
            currentTasks = emptyMap()
            pausedTasks = emptySet()
            _downloadProgress.value = emptyMap()
            while (queue.tryReceive().isSuccess) {
                // 排空尚未开始的任务。
            }
            activeTaskCompletions.values.toList()
        }
        activeCalls.keys.forEach { it.cancel() }
        activeCalls.clear()
        activeRequests.values.forEach { it.cancel() }
        activeRequests.clear()
        completions.forEach { it.await() }
        stateMutex.withLock { if (activeTaskCompletions.isEmpty()) _activeTask.value = null }
    }

    fun getTask(pathWord: String): ComicDownloadTask? =
        currentTasks[comicDownloadKey(pathWord)] as? ComicDownloadTask

    suspend fun cancelComic(pathWord: String) = cancelDownload(comicDownloadKey(pathWord))
    fun pauseComic(pathWord: String) = pauseDownload(comicDownloadKey(pathWord))
    fun resumeComic(pathWord: String) = resumeDownload(comicDownloadKey(pathWord))

    private suspend fun processQueue() {
        for (task in queue) {
            val completion = CompletableDeferred<Unit>()
            val shouldProcess = stateMutex.withLock {
                if (task.taskKey in cacheRemovals) pausedTasks = pausedTasks + task.taskKey
                if (task is NovelDownloadTask && !novelSessionAllowed(task)) {
                    pausedTasks = pausedTasks + task.taskKey
                    updateProgress(
                        task.taskKey, status = DownloadTaskStatus.PAUSED,
                        statusText = "请切换到下载所属账号和请求模式后继续"
                    )
                }
                !(currentTasks[task.taskKey]?.taskId != task.taskId || task.taskKey in pausedTasks) && if (!networkAllowed() || task.taskKey in networkWaiting) {
                    networkWaiting = networkWaiting + task.taskKey
                    updateProgress(
                        task.taskKey, status = DownloadTaskStatus.WAITING_FOR_WIFI,
                        statusText = waitingForNetwork(), speedBytesPerSecond = 0L
                    )
                    false
                } else {
                    activeTaskCompletions[task.taskKey] = completion
                    true
                }
            }
            if (!shouldProcess) {
                if (networkAllowed() && networkWaiting.isNotEmpty()) applyNetworkPolicy(true)
                continue
            }

            _activeTask.value = task
            try {
                when (task) {
                    is ComicDownloadTask -> processComicTask(task)
                    is NovelDownloadTask -> processNovelTask(task)
                }
            } catch (error: Exception) {
                if (error is CancellationException) currentCoroutineContext().ensureActive()
                stateMutex.withLock {
                    updateProgress(
                        task.taskKey, status = DownloadTaskStatus.ERROR,
                        statusText = "下载任务失败，可重试", errorMessage = error.message
                    )
                }
                Log.e(TAG, "处理下载任务失败", error)
            } finally {
                stateMutex.withLock {
                    activeTaskCompletions.remove(task.taskKey, completion)
                }
                completion.complete(Unit)
            }
            if (task.taskKey in pausedTasks) {
                val updatedTask = currentTasks[task.taskKey]
                if (updatedTask != null) {
                    scope.launch { queue.send(updatedTask) }
                }
            } else {
                stateMutex.withLock {
                    val remaining = currentTasks[task.taskKey]
                    if (remaining?.taskId == task.taskId && remaining.items.all {
                            it.status == ChapterDownloadStatus.DOWNLOADED
                        }) {
                        currentTasks = currentTasks - task.taskKey
                        networkWaiting = networkWaiting - task.taskKey
                        try {
                            queueStore?.remove(task.taskKey)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // 下次恢复时仍会按完整章节核对并清除此记录。
                            Log.e(TAG, "清理已完成队列失败", e)
                        }
                    }
                }
            }
            _activeTask.value = null
            // Wi-Fi 可能在旧请求退出前已恢复，补发恢复事件以免漏掉快速切换。
            if (networkAllowed() && networkWaiting.isNotEmpty()) applyNetworkPolicy(true)
        }
    }


    private fun comicTask(path: String): ComicDownloadTask? =
        currentTasks[comicDownloadKey(path)] as? ComicDownloadTask

    /** 仅正文/插图请求因内容种类而异，队列、暂停、网络策略与服务生命周期均共用。 */
    private suspend fun processNovelTask(task: NovelDownloadTask) {
        val repository = novels ?: return
        val expectedSession = sessions?.getReadingSessionSync()
        val key = task.taskKey
        fun sessionValid() = expectedSession != null &&
                sessions.getReadingSessionSync() == expectedSession && novelSessionAllowed(task)
        while (true) {
            if (!sessionValid()) {
                stateMutex.withLock {
                    if (key in currentTasks) {
                        pausedTasks = pausedTasks + key
                        updateProgress(
                            key, status = DownloadTaskStatus.PAUSED,
                            statusText = "账号或请求模式已变化，请确认后继续"
                        )
                    }
                }
                return
            }
            val item = stateMutex.withLock {
                val current =
                    (currentTasks[key] as? NovelDownloadTask)?.takeIf { it.taskId == task.taskId }
                        ?: return@withLock null
                if (key in pausedTasks || key in cacheRemovals || !networkAllowed() || key in networkWaiting) return@withLock null
                val next =
                    current.volumes.firstOrNull { it.status == ChapterDownloadStatus.WAITING }
                        ?: return@withLock null
                val changed = current.copy(volumes = current.volumes.map {
                    if (it.id == next.id) it.copy(status = ChapterDownloadStatus.DOWNLOADING) else it
                })
                currentTasks = currentTasks + (key to changed)
                updateProgressForTask(changed)
                next
            } ?: return
            try {
                val volume = supervisorScope {
                    val updates = Channel<NovelCacheProgress>(Channel.CONFLATED)
                    val publisher = launch {
                        for (progress in updates) {
                            stateMutex.withLock {
                                val current = currentTasks[key]
                                if (sessionValid() && key !in pausedTasks && key !in networkWaiting &&
                                    key !in cacheRemovals && current?.taskId == task.taskId &&
                                    current.items.any { it.id == item.id && it.status == ChapterDownloadStatus.DOWNLOADING }
                                ) {
                                    val portion = if (progress.stage == NovelCacheStage.TEXT) 0f
                                    else (1f + progress.completedImages) / (1f + progress.totalImages)
                                    updateProgress(
                                        key,
                                        status = DownloadTaskStatus.DOWNLOADING,
                                        statusText = if (progress.stage == NovelCacheStage.TEXT) "正在下载正文"
                                        else "正在下载插图 ${progress.completedImages}/${progress.totalImages}",
                                        currentChapterName = item.name,
                                        currentChapterProgress = portion,
                                        downloadedImages = progress.completedImages,
                                        totalImages = progress.totalImages
                                    )
                                }
                            }
                        }
                    }
                    val request = async {
                        repository.readVolume(
                            task.mode, task.owner, task.book.path, item.id,
                            allImages = true, forDownload = true, onProgress = { progress ->
                                updates.trySend(progress)
                            }).fold(onSuccess = { it }, onFailure = { throw it })
                    }
                    activeRequests[key] = request
                    try {
                        if (!sessionValid() || key in pausedTasks || !networkAllowed() ||
                            currentTasks[key]?.items?.none { it.id == item.id } != false
                        ) request.cancel()
                        request.await()
                    } finally {
                        activeRequests.remove(key, request)
                        request.cancel()
                        updates.close()
                        publisher.cancel()
                    }
                }
                currentCoroutineContext().ensureActive()
                check(volume.offlineReady) { "本卷插图未完整下载" }
                if (!sessionValid()) continue
                stateMutex.withLock {
                    val current =
                        (currentTasks[key] as? NovelDownloadTask)?.takeIf { it.taskId == task.taskId }
                            ?: return@withLock
                    val changed = current.copy(volumes = current.volumes.map {
                        if (it.id == item.id) it.copy(
                            status = ChapterDownloadStatus.DOWNLOADED,
                            progress = 1f
                        ) else it
                    })
                    currentTasks = currentTasks + (key to changed)
                    updateProgressForTask(changed)
                }
            } catch (error: Exception) {
                if (error is CancellationException) currentCoroutineContext().ensureActive()
                stateMutex.withLock {
                    val current =
                        (currentTasks[key] as? NovelDownloadTask)?.takeIf { it.taskId == task.taskId }
                            ?: return@withLock
                    val interrupted = error is CancellationException || key in pausedTasks ||
                            !networkAllowed() || key in networkWaiting || !sessionValid()
                    val changed = current.copy(volumes = current.volumes.map {
                        if (it.id == item.id) it.copy(
                            status = if (interrupted) ChapterDownloadStatus.WAITING else ChapterDownloadStatus.ERROR,
                            progress = 0f,
                            errorMessage = if (interrupted) null else error.message
                                ?: "分卷下载失败"
                        ) else it
                    })
                    currentTasks = currentTasks + (key to changed)
                    if (!sessionValid()) pausedTasks = pausedTasks + key
                    if (!networkAllowed() && key !in pausedTasks) networkWaiting =
                        networkWaiting + key
                    updateProgressForTask(changed)
                }
                if (error is CancellationException || key in pausedTasks || key in networkWaiting || !sessionValid()) return
            }
        }
    }

    private suspend fun processComicTask(task: ComicDownloadTask) {
        val comicPathWord = task.pathWord

        // 使用 while 循环每次从 currentTasks 重新读取，以支持中途合并进来的新章节
        while (true) {
            if (!currentTasks.containsKey(comicDownloadKey(comicPathWord))) {
                Log.d(TAG, "Task cancelled for $comicPathWord")
                return
            }
            if (comicDownloadKey(comicPathWord) in pausedTasks) {
                Log.d(TAG, "Task paused for $comicPathWord")
                return
            }
            if (!networkAllowed() || comicDownloadKey(comicPathWord) in networkWaiting) {
                stateMutex.withLock {
                    networkWaiting = networkWaiting + comicDownloadKey(comicPathWord)
                    updateProgress(
                        comicPathWord, status = DownloadTaskStatus.WAITING_FOR_WIFI,
                        statusText = waitingForNetwork(), speedBytesPerSecond = 0L
                    )
                }
                return
            }

            // 选择章节、登记 I/O 完成信号和状态变更必须原子化，避免取消后旧快照复活任务。
            val pending = stateMutex.withLock {
                val currentTask = comicTask(comicPathWord)
                    ?.takeIf { it.taskId == task.taskId } ?: return@withLock null
                if (comicDownloadKey(comicPathWord) in pausedTasks) return@withLock null
                val chapters = currentTask.chapters
                val idx = chapters.indexOfFirst { it.status == ChapterDownloadStatus.WAITING }
                if (idx < 0) return@withLock null
                val chapterItem = chapters[idx]
                val updatedChapters = chapters.toMutableList()
                updatedChapters[idx] =
                    chapterItem.copy(status = ChapterDownloadStatus.DOWNLOADING, progress = 0f)
                val newTask = currentTask.copy(chapters = updatedChapters)
                currentTasks = currentTasks + (comicDownloadKey(comicPathWord) to newTask)
                val completion = CompletableDeferred<Unit>()
                activeChapterCompletions[chapterItem.chapter.uuid] = completion
                updateProgressForTask(newTask)
                Triple(chapterItem, idx, completion)
            } ?: break
            val (chapterItem, idx, chapterCompletion) = pending

            try {
                val chapterIndex = idx + 1
                val existing = chapterDao.getChapter(chapterItem.chapter.uuid)
                    ?.takeIf { it.comicPathWord == comicPathWord && it.hasCompleteFiles() }
                // 评论阶段暂停后继续时，复用已经完整保存的图片。
                if (existing == null) {
                    updateProgress(
                        comicPathWord = comicPathWord,
                        status = DownloadTaskStatus.REQUESTING_PAGE,
                        statusText = "正在请求网页",
                        currentChapterIndex = chapterIndex,
                        currentChapterName = chapterItem.chapter.name,
                        currentChapterProgress = 0f,
                        downloadedImages = 0,
                        totalImages = 0,
                        speedBytesPerSecond = 0L
                    )

                    val chapterContent = downloadChapterContent(
                        chapterItem.chapter,
                        comicPathWord
                    )

                    // 章节内容请求返回前，用户可能已经取消整部漫画或移除了当前章节。
                    // 此时不得继续创建图片文件或回写 Room。
                    val latestTask = comicTask(comicPathWord) ?: return
                    if (latestTask.chapters.none { it.chapter.uuid == chapterItem.chapter.uuid }) {
                        continue
                    }

                    if (chapterContent != null) {
                        updateProgress(
                            comicPathWord = comicPathWord,
                            status = DownloadTaskStatus.REQUESTING_PAGE,
                            statusText = "正在获取章节图片列表",
                            currentChapterIndex = chapterIndex,
                            currentChapterName = chapterItem.chapter.name,
                            speedBytesPerSecond = 0L
                        )

                        val imageUrls = chapterContent.imageUrls
                        updateProgress(
                            comicPathWord = comicPathWord,
                            status = DownloadTaskStatus.DOWNLOADING,
                            statusText = "开始下载图片",
                            currentChapterIndex = chapterIndex,
                            currentChapterName = chapterItem.chapter.name,
                            currentChapterProgress = 0f,
                            downloadedImages = 0,
                            totalImages = imageUrls.size,
                            speedBytesPerSecond = 0L
                        )

                        val imagePaths = downloadChapterImages(
                            comicPathWord,
                            chapterItem.chapter.uuid,
                            imageUrls,
                            chapterIndex,
                            chapterItem.chapter.name
                        )

                        if (comicDownloadKey(comicPathWord) in pausedTasks) {
                            Log.d(
                                TAG,
                                "Chapter $comicPathWord/${chapterItem.chapter.name} paused mid-download"
                            )
                            markChapterWaiting(comicPathWord, chapterItem)
                            return
                        }

                        if (imageUrls.isEmpty() || imagePaths.size != imageUrls.size) {
                            throw IOException("章节图片不完整：${imagePaths.size}/${imageUrls.size}")
                        }

                        val taskBeforeCommit = comicTask(comicPathWord) ?: return
                        if (taskBeforeCommit.chapters.none {
                                it.chapter.uuid == chapterItem.chapter.uuid
                            }) {
                            continue
                        }

                        val entity = DownloadedChapterEntity(
                            uuid = chapterItem.chapter.uuid,
                            comicPathWord = comicPathWord,
                            name = chapterItem.chapter.name,
                            index = chapterItem.chapter.index,
                            imageCount = imageUrls.size,
                            imagePaths = imagePaths.joinToString(","),
                            status = "downloaded",
                            downloadAt = System.currentTimeMillis(),
                            groupPathWord = chapterItem.chapter.groupPathWord ?: "default",
                            prevChapter = chapterContent.prevUuid
                                ?: chapterItem.chapter.prevChapter,
                            nextChapter = chapterContent.nextUuid ?: chapterItem.chapter.nextChapter
                        )
                        val committed = stateMutex.withLock {
                            val latest = comicTask(comicPathWord)
                            if (latest?.taskId == task.taskId && latest.chapters.any {
                                    it.chapter.uuid == chapterItem.chapter.uuid
                                }) {
                                chapterDao.insertChapter(entity)
                                true
                            } else false
                        }
                        if (!committed) continue

                        val roomChapterCount = chapterDao.getChapterUuids(comicPathWord).size
                        comicDao.updateChapterCount(comicPathWord, roomChapterCount)

                        updateProgress(
                            comicPathWord = comicPathWord,
                            status = DownloadTaskStatus.DOWNLOADING,
                            statusText = "章节图片已下载",
                            currentChapterIndex = chapterIndex,
                            currentChapterName = chapterItem.chapter.name,
                            currentChapterProgress = 1f,
                            downloadedImages = imageUrls.size,
                            totalImages = imageUrls.size,
                            speedBytesPerSecond = 0L
                        )
                    } else {
                        markChapterError(comicPathWord, chapterItem, "获取章节内容失败")
                        updateProgress(
                            comicPathWord = comicPathWord,
                            status = DownloadTaskStatus.ERROR,
                            statusText = "获取章节内容失败",
                            errorMessage = "获取章节内容失败",
                            speedBytesPerSecond = 0L
                        )
                        continue
                    }
                }

                val latest = comicTask(comicPathWord)
                if (latest?.taskId != task.taskId) return
                if (latest.chapters.none { it.chapter.uuid == chapterItem.chapter.uuid }) continue
                updateProgress(
                    comicPathWord,
                    statusText = "正在下载章节评论",
                    speedBytesPerSecond = 0L
                )
                val commentResult = downloadCommentsForTask(comicPathWord, chapterItem.chapter.uuid)
                if (comicDownloadKey(comicPathWord) in pausedTasks || !networkAllowed() || comicDownloadKey(
                        comicPathWord
                    ) in networkWaiting
                ) {
                    markChapterWaiting(comicPathWord, chapterItem)
                    return
                }
                val warning = if (commentResult.isFailure) "图片已下载，评论下载未完成" else null
                markChapterCompleted(comicPathWord, chapterItem, warning)
                updateProgress(comicPathWord, statusText = warning ?: "章节和评论下载完成")
            } catch (e: SiteRiskControlException) {
                Log.w(TAG, "下载章节触发站点风控: ${chapterItem.chapter.name}", e)
                handleRiskControl(comicPathWord, chapterItem, e.reason)
            } catch (e: RiskControlException) {
                Log.w(TAG, "下载章节触发风控: ${chapterItem.chapter.name}", e)
                handleRiskControl(
                    comicPathWord,
                    chapterItem,
                    e.message ?: "触发网站风控，已暂停下载"
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e is DownloadNetworkUnavailableException || !networkAllowed() || comicDownloadKey(
                        comicPathWord
                    ) in networkWaiting
                ) {
                    markChapterWaiting(comicPathWord, chapterItem)
                    stateMutex.withLock {
                        if (comicDownloadKey(comicPathWord) !in pausedTasks && currentTasks.containsKey(
                                comicDownloadKey(comicPathWord)
                            )
                        ) {
                            networkWaiting = networkWaiting + comicDownloadKey(comicPathWord)
                            updateProgress(
                                comicPathWord,
                                status = DownloadTaskStatus.WAITING_FOR_WIFI,
                                statusText = waitingForNetwork(),
                                speedBytesPerSecond = 0L
                            )
                        }
                    }
                    return
                }
                if (comicDownloadKey(comicPathWord) in pausedTasks) {
                    markChapterWaiting(comicPathWord, chapterItem)
                    return
                }
                Log.e(TAG, "下载章节失败: ${chapterItem.chapter.name}", e)
                markChapterError(
                    comicPathWord,
                    chapterItem,
                    e.message ?: "未知错误"
                )
                updateProgress(
                    comicPathWord = comicPathWord,
                    status = DownloadTaskStatus.ERROR,
                    statusText = "章节下载失败",
                    errorMessage = e.message ?: "未知错误",
                    speedBytesPerSecond = 0L
                )
            } finally {
                stateMutex.withLock {
                    activeChapterCompletions.remove(chapterItem.chapter.uuid, chapterCompletion)
                }
                chapterCompletion.complete(Unit)
            }

            updateProgressForTask(comicTask(comicPathWord))
        }

        stateMutex.withLock {
            val finalTask = comicTask(comicPathWord)
            if (finalTask?.taskId == task.taskId && finalTask.chapters.all {
                    it.status == ChapterDownloadStatus.DOWNLOADED
                }) {
                updateProgress(
                    comicPathWord = comicPathWord,
                    status = DownloadTaskStatus.COMPLETED,
                    statusText = if (finalTask.chapters.any { it.errorMessage != null })
                        "图片下载完成，部分评论未完整下载" else "章节和评论下载完成",
                    currentChapterProgress = 1f,
                    speedBytesPerSecond = 0L,
                    errorMessage = null
                )
            }
        }
    }

    private suspend fun downloadChapterContent(
        chapter: ChapterInfo,
        comicPathWord: String
    ): ChapterContent? {
        if (!networkAllowed()) throw DownloadNetworkUnavailableException()
        return supervisorScope {
            val request = async {
                mangaRepository.getChapterDownloadContent(
                    comicPathWord, chapter.uuid,
                    chapter.groupPathWord ?: "default"
                ).fold(onSuccess = { it }, onFailure = { throw it })
            }
            val requestKey = comicDownloadKey(comicPathWord)
            activeRequests[requestKey] = request
            val restricted =
                networkPolicy?.let { policy -> async { policy.state.first { !it.allowed } } }
            try {
                if (requestKey in pausedTasks ||
                    comicTask(comicPathWord)?.chapters?.none { it.id == chapter.uuid } != false
                ) request.cancel()
                select {
                    request.onAwait { it }
                    restricted?.onAwait { throw DownloadNetworkUnavailableException() }
                }
            } catch (error: CancellationException) {
                currentCoroutineContext().ensureActive()
                throw IOException("章节内容请求已暂停", error)
            } finally {
                activeRequests.remove(requestKey, request)
                request.cancel()
                restricted?.cancel()
            }
        }
    }

    /** 评论属于附加下载；中断不回滚已完成图片，但暂停和取消仍终止请求。 */
    private suspend fun downloadCommentsForTask(comic: String, uuid: String): Result<Unit> {
        val repository = commentsRepository ?: return Result.success(Unit)
        if (repository.getDownloadedChapterComments(uuid)
                .getOrNull()?.complete == true
        ) return Result.success(Unit)
        return supervisorScope {
            val request = async { repository.downloadChapterComments(uuid).map { } }
            activeRequests[comicDownloadKey(comic)] = request
            val restricted =
                networkPolicy?.let { policy -> async { policy.state.first { !it.allowed } } }
            try {
                if (comicDownloadKey(comic) in pausedTasks || comicTask(comic)?.chapters?.none { it.chapter.uuid == uuid } != false) request.cancel()
                select {
                    request.onAwait { it }
                    restricted?.onAwait { Result.failure(IOException("等待网络连接")) }
                }
            } catch (error: CancellationException) {
                currentCoroutineContext().ensureActive()
                Result.failure(IOException("评论下载已暂停", error))
            } finally {
                activeRequests.remove(comicDownloadKey(comic), request)
                request.cancel()
                restricted?.cancel()
            }
        }
    }

    /** 每章仅创建固定数量的工作协程；结果按原图序归位，失败会撤销全部同组请求。 */
    private suspend fun downloadChapterImages(
        comicPathWord: String,
        chapterUuid: String,
        imageUrls: List<String>,
        chapterIndex: Int,
        chapterName: String?
    ): List<String> = supervisorScope {
        downloadSettingsReady.await()
        val key = comicDownloadKey(comicPathWord)
        val taskId = comicTask(comicPathWord)?.taskId ?: throw IOException("下载已取消")
        val workerCount = imageConcurrency.coerceIn(1, 10).coerceAtMost(imageUrls.size)
        val client = downloadClient()
        val dir = File(context.filesDir, "$DOWNLOAD_DIR/$comicPathWord/$chapterUuid")
        val paths = arrayOfNulls<String>(imageUrls.size)
        val nextIndex = AtomicInteger()
        var completed = 0
        var bytesSinceLastTick = 0L
        var lastTick = System.nanoTime()

        suspend fun checkActive() {
            currentCoroutineContext().ensureActive()
            if (!networkAllowed() || key in networkWaiting) throw DownloadNetworkUnavailableException()
            if (key in pausedTasks) throw IOException("下载已暂停")
            val task = comicTask(comicPathWord)
            if (task?.taskId != taskId || task.chapters.none { it.chapter.uuid == chapterUuid }) {
                throw IOException("下载已取消")
            }
        }

        // 与暂停、删除使用同一把锁，旧任务不能发布图片、回执或进度。
        suspend fun publish(action: () -> Unit) = stateMutex.withLock {
            checkActive()
            action()
        }

        suspend fun report(bytes: Int = 0, finished: Boolean = false) = stateMutex.withLock {
            checkActive()
            bytesSinceLastTick += bytes
            if (finished) completed++
            val now = System.nanoTime()
            val elapsed = (now - lastTick) / 1_000_000L
            if (finished || elapsed >= 500L) {
                val speed = if (elapsed >= 500L) bytesSinceLastTick * 1000L / elapsed else null
                if (speed != null) {
                    bytesSinceLastTick = 0
                    lastTick = now
                }
                updateProgress(
                    comicPathWord = comicPathWord,
                    status = DownloadTaskStatus.DOWNLOADING,
                    statusText = "已下载图片 $completed/${imageUrls.size}",
                    currentChapterIndex = chapterIndex,
                    currentChapterName = chapterName,
                    currentChapterProgress = completed.toFloat() / imageUrls.size.coerceAtLeast(1),
                    downloadedImages = completed,
                    totalImages = imageUrls.size,
                    speedBytesPerSecond = speed
                )
            }
        }

        // 先登记后启动，让暂停也能取消正在等限速预算的协程。
        val request = async(Dispatchers.IO, start = CoroutineStart.LAZY) {
            coroutineScope {
                repeat(workerCount) {
                    launch {
                        while (true) {
                            checkActive()
                            val i = nextIndex.getAndIncrement()
                            if (i >= imageUrls.size) break
                            val url = imageUrls[i]
                            val ext =
                                url.substringAfterLast('.').substringBefore('?').ifEmpty { "jpg" }
                            val file = File(dir, "${i.toString().padStart(3, '0')}.$ext")
                            localAccess.mutate("$comicPathWord/$chapterUuid").use {
                                publish { if (!dir.exists() && !dir.mkdirs()) throw IOException("无法创建下载目录") }
                                downloadImageIfNeeded(file, url, ::publish) {
                                    val call = client.newCall(Request.Builder().url(url).build())
                                    activeCalls[call] = key
                                    // 子协程取消会立即关闭阻塞中的 socket，包括响应头和流读取。
                                    val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                                        try {
                                            awaitCancellation()
                                        } finally {
                                            call.cancel()
                                        }
                                    }
                                    try {
                                        checkActive()
                                        call.execute().use { response ->
                                            if (isRiskResponseCode(response.code)) {
                                                throw RiskControlException("服务器返回 ${response.code}，可能触发风控")
                                            }
                                            if (!response.isSuccessful) {
                                                throw IOException("图片下载失败，HTTP ${response.code}")
                                            }
                                            val body = response.body
                                            val contentType =
                                                response.header("Content-Type").orEmpty()
                                            if (contentType.isNotBlank() &&
                                                !contentType.startsWith("image/") &&
                                                !contentType.contains("octet-stream")
                                            ) {
                                                val preview = try {
                                                    body.source().peek().readUtf8(512)
                                                } catch (error: IOException) {
                                                    ""
                                                }
                                                if (isRiskHtml(preview)) throw RiskControlException(
                                                    "图片地址返回风控页面"
                                                )
                                                throw IOException("图片地址返回了非图片内容：$contentType")
                                            }
                                            body.byteStream().use { input ->
                                                writeDownloadedImageSuspending(
                                                    file,
                                                    input,
                                                    body.contentLength(),
                                                    ::publish
                                                ) { read ->
                                                    checkActive()
                                                    bandwidthLimiter.consume(read)
                                                    checkActive()
                                                    report(bytes = read)
                                                }
                                            }
                                        }
                                    } finally {
                                        cancellation.cancel()
                                        call.cancel()
                                        activeCalls.remove(call)
                                    }
                                }
                            }
                            paths[i] = file.absolutePath
                            report(finished = true)
                        }
                    }
                }
            }
            paths.map { it ?: throw IOException("章节图片不完整") }
        }
        activeRequests[key] = request
        try {
            checkActive()
            request.await()
        } catch (error: CancellationException) {
            currentCoroutineContext().ensureActive()
            throw IOException("图片下载已暂停", error)
        } finally {
            activeRequests.remove(key, request)
            request.cancel()
        }
    }

    private fun isRiskResponseCode(code: Int): Boolean {
        return code == 403 || code == 429
    }

    private fun isRiskHtml(content: String): Boolean {
        val lower = content.lowercase()
        return lower.contains("captcha") ||
                lower.contains("verify") ||
                lower.contains("cloudflare") ||
                lower.contains("cf-challenge") ||
                lower.contains("安全验证") ||
                lower.contains("人机验证") ||
                lower.contains("访问频繁") ||
                lower.contains("too many requests")
    }

    private suspend fun handleRiskControl(
        comicPathWord: String,
        chapterItem: ChapterDownloadItem,
        message: String
    ) {
        stateMutex.withLock {
            val task = comicTask(comicPathWord) ?: return
            if (task.chapters.none { it.chapter.uuid == chapterItem.chapter.uuid }) return
            pausedTasks = pausedTasks + comicDownloadKey(comicPathWord)
        }
        markChapterError(comicPathWord, chapterItem, message)
        updateProgress(
            comicPathWord = comicPathWord,
            status = DownloadTaskStatus.PAUSED_BY_RISK,
            statusText = "触发风控，已暂停下载",
            errorMessage = message,
            speedBytesPerSecond = 0L
        )
        Log.w(TAG, "下载触发风控，已暂停：$comicPathWord, $message")
    }

    private suspend fun markChapterError(
        comicPathWord: String,
        chapterItem: ChapterDownloadItem,
        message: String
    ) {
        stateMutex.withLock {
            val task = comicTask(comicPathWord) ?: return@withLock
            val chapters = task.chapters.toMutableList()
            val idx = chapters.indexOfFirst { it.chapter.uuid == chapterItem.chapter.uuid }
            if (idx >= 0) {
                chapters[idx] = chapterItem.copy(
                    status = ChapterDownloadStatus.ERROR,
                    errorMessage = message
                )
                currentTasks =
                    currentTasks + (comicDownloadKey(comicPathWord) to task.copy(chapters = chapters))
            }
        }
    }

    private suspend fun markChapterCompleted(
        comicPathWord: String,
        chapterItem: ChapterDownloadItem,
        warning: String? = null
    ) {
        stateMutex.withLock {
            val task = comicTask(comicPathWord) ?: return@withLock
            val chapters = task.chapters.toMutableList()
            val idx = chapters.indexOfFirst { it.chapter.uuid == chapterItem.chapter.uuid }
            if (idx >= 0) {
                chapters[idx] = chapterItem.copy(
                    status = ChapterDownloadStatus.DOWNLOADED,
                    progress = 1f,
                    errorMessage = warning
                )
                currentTasks =
                    currentTasks + (comicDownloadKey(comicPathWord) to task.copy(chapters = chapters))
            }
        }
    }

    private suspend fun markChapterWaiting(
        comicPathWord: String,
        chapterItem: ChapterDownloadItem
    ) {
        stateMutex.withLock {
            val task = comicTask(comicPathWord) ?: return@withLock
            val chapters = task.chapters.toMutableList()
            val idx = chapters.indexOfFirst { it.chapter.uuid == chapterItem.chapter.uuid }
            if (idx >= 0) {
                chapters[idx] = chapterItem.copy(
                    status = ChapterDownloadStatus.WAITING,
                    progress = 0f
                )
                currentTasks =
                    currentTasks + (comicDownloadKey(comicPathWord) to task.copy(chapters = chapters))
            }
        }
    }

    private fun updateProgress(
        comicPathWord: String,
        status: DownloadTaskStatus? = null,
        statusText: String? = null,
        errorMessage: String? = null,
        currentChapterIndex: Int? = null,
        currentChapterName: String? = null,
        currentChapterProgress: Float? = null,
        downloadedImages: Int? = null,
        totalImages: Int? = null,
        speedBytesPerSecond: Long? = null
    ) {
        val key =
            if (comicPathWord in currentTasks) comicPathWord else comicDownloadKey(comicPathWord)
        val task = currentTasks[key] ?: return
        val old = _downloadProgress.value[key]

        if (key in networkWaiting && status != null && status !in setOf(
                DownloadTaskStatus.WAITING_FOR_WIFI, DownloadTaskStatus.PAUSED,
                DownloadTaskStatus.PAUSED_BY_RISK, DownloadTaskStatus.COMPLETED
            )
        ) return

        if (key in pausedTasks &&
            status != null &&
            status != DownloadTaskStatus.PAUSED &&
            status != DownloadTaskStatus.PAUSED_BY_RISK
        ) {
            return
        }

        val completedChapters = task.completedChapters
        val totalChapters = task.totalChapters

        val newChapterProgress = currentChapterProgress ?: old?.currentChapterProgress ?: 0f
        val newChapterIndex = currentChapterIndex ?: old?.currentChapterIndex ?: 0

        val nextStatus = status ?: old?.status ?: DownloadTaskStatus.IDLE
        val overall = downloadOverallProgress(task, old, nextStatus, newChapterProgress)

        val info = DownloadProgressInfo(
            comicPathWord = task.pathWord,
            name = task.name,
            cover = task.cover,
            completedChapters = completedChapters,
            totalChapters = totalChapters,
            overallProgress = overall,
            currentChapterIndex = newChapterIndex,
            currentChapterName = currentChapterName
                ?: old?.currentChapterName
                ?: task.currentChapterDisplayName,
            currentChapterProgress = newChapterProgress,
            status = nextStatus,
            statusText = statusText ?: old?.statusText ?: "等待中",
            errorMessage = errorMessage,
            downloadedImages = downloadedImages ?: old?.downloadedImages ?: 0,
            totalImages = totalImages ?: old?.totalImages ?: 0,
            speedBytesPerSecond = speedBytesPerSecond ?: old?.speedBytesPerSecond ?: 0L,
            taskId = task.taskId,
            kind = task.kind, taskKey = task.taskKey,
            owner = (task as? NovelDownloadTask)?.owner, mode = (task as? NovelDownloadTask)?.mode
        )

        _downloadProgress.update { it + (key to info) }
        if (_activeTask.value?.taskKey == key) {
            _activeTask.value = task
        }
    }

    private fun updateProgressForTask(task: BookDownloadTask?) {
        if (task == null) return
        val currentChapter = task.items.firstOrNull {
            it.status == ChapterDownloadStatus.DOWNLOADING
        }
        val status = when {
            task.taskKey in pausedTasks -> {
                val oldInfo = _downloadProgress.value[task.taskKey]
                if (oldInfo?.status == DownloadTaskStatus.PAUSED_BY_RISK) {
                    DownloadTaskStatus.PAUSED_BY_RISK
                } else {
                    DownloadTaskStatus.PAUSED
                }
            }

            task.items.all { it.status == ChapterDownloadStatus.DOWNLOADED } -> DownloadTaskStatus.COMPLETED
            task.taskKey in networkWaiting -> DownloadTaskStatus.WAITING_FOR_WIFI
            currentChapter != null -> DownloadTaskStatus.DOWNLOADING
            task.items.any { it.status == ChapterDownloadStatus.WAITING } -> DownloadTaskStatus.WAITING
            task.errorCount > 0 -> DownloadTaskStatus.ERROR
            else -> DownloadTaskStatus.IDLE
        }

        val old = _downloadProgress.value[task.taskKey]
        val statusText = when (status) {
            DownloadTaskStatus.COMPLETED -> if (task.kind == DownloadKind.NOVEL) "正文和插图下载完成"
            else if (task.items.any { it.errorMessage != null })
                "图片下载完成，部分评论未完整下载" else "章节和评论下载完成"

            DownloadTaskStatus.ERROR -> "已完成 ${task.completedChapters}/${task.totalChapters} ${if (task.kind == DownloadKind.NOVEL) "卷" else "章"}，${task.errorCount} 项失败"
            DownloadTaskStatus.PAUSED_BY_RISK -> old?.statusText ?: "触发风控，已暂停下载"
            DownloadTaskStatus.PAUSED -> "已暂停"
            DownloadTaskStatus.WAITING_FOR_WIFI -> waitingForNetwork()
            else -> old?.statusText
        }
        val errorChapter = task.items.firstOrNull { it.status == ChapterDownloadStatus.ERROR }

        val info = DownloadProgressInfo(
            comicPathWord = task.pathWord,
            name = task.name,
            cover = task.cover,
            completedChapters = task.completedChapters,
            totalChapters = task.totalChapters,
            overallProgress = task.overallProgress,
            currentChapterIndex = task.items.indexOfFirst {
                it.status == ChapterDownloadStatus.DOWNLOADING
            }.let { if (it < 0) 0 else it + 1 },
            currentChapterName = currentChapter?.name,
            currentChapterProgress = currentChapter?.progress ?: 0f,
            status = status,
            statusText = statusText ?: "等待中",
            errorMessage = old?.errorMessage ?: errorChapter?.errorMessage,
            downloadedImages = old?.downloadedImages ?: 0,
            totalImages = old?.totalImages ?: 0,
            speedBytesPerSecond = 0L,
            taskId = task.taskId,
            kind = task.kind, taskKey = task.taskKey,
            owner = (task as? NovelDownloadTask)?.owner, mode = (task as? NovelDownloadTask)?.mode
        )
        _downloadProgress.update { it + (task.taskKey to info) }
        if (_activeTask.value?.taskKey == task.taskKey) {
            _activeTask.value = task
        }
    }
}
