package com.rchdliang.copymanga_neo.data.download

import android.content.Context
import android.util.Log
import com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore
import com.rchdliang.copymanga_neo.data.local.db.DownloadedChapterDao
import com.rchdliang.copymanga_neo.data.local.db.DownloadedComicDao
import com.rchdliang.copymanga_neo.data.local.entity.DownloadedChapterEntity
import com.rchdliang.copymanga_neo.domain.model.ChapterContent
import com.rchdliang.copymanga_neo.domain.model.ChapterInfo
import com.rchdliang.copymanga_neo.domain.repository.CommentRepository
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
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
    private val commentsRepository: CommentRepository? = null
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
        commentsRepository: CommentRepository
    ) : this(context, mangaRepository, comicDao, chapterDao, okHttpClient, {
        DownloadService.start(context)
    }, queueStore, networkPolicy, networkSettings, commentsRepository)

    companion object {
        private const val TAG = "DownloadManager"
        private const val DOWNLOAD_DIR = "downloads"
    }

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "下载任务处理失败", error)
        })
    private val queue = Channel<ComicDownloadTask>(UNLIMITED)
    private var imageConcurrency = Semaphore(3)

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgressInfo>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgressInfo>> =
        _downloadProgress.asStateFlow()

    private val _activeTask = MutableStateFlow<ComicDownloadTask?>(null)
    val activeTask: StateFlow<ComicDownloadTask?> = _activeTask.asStateFlow()

    // Copy-on-write 快照：所有写操作经 stateMutex 串行化；读操作无锁读取 @Volatile 引用，得到的可能稍旧但安全。
    @Volatile
    private var currentTasks: Map<String, ComicDownloadTask> = emptyMap()

    @Volatile
    private var taskInfoMap: Map<String, ComicDownloadTask> = emptyMap()

    @Volatile
    private var pausedComics: Set<String> = emptySet()

    /** 网络等待不等同于手动暂停，连接恢复时只继续此集合中的任务。 */
    @Volatile
    private var networkWaiting: Set<String> = emptySet()

    /** 对 currentTasks / taskInfoMap / pausedComics 写操作的串行化锁。读不持锁，见上注释。 */
    private val stateMutex = Mutex()

    /** 当前漫画任务与章节的完成信号，用于让取消/删除操作等待后台 I/O 真正退出。 */
    private val activeTaskCompletions = mutableMapOf<String, CompletableDeferred<Unit>>()
    private val activeChapterCompletions = mutableMapOf<String, CompletableDeferred<Unit>>()

    /** 当前图片请求；取消漫画或章节时会主动终止对应的 OkHttp Call。 */
    private val activeCalls = ConcurrentHashMap<String, okhttp3.Call>()
    private val activeCommentRequests = ConcurrentHashMap<String, Job>()
    private var wifiClient: Pair<android.net.Network, OkHttpClient>? = null

    init {
        networkSettings?.onChange {
            synchronized(this) {
                wifiClient?.second?.connectionPool?.evictAll()
                wifiClient = null
            }
        }
    }

    private val restoration = scope.async { restoreQueue() }

    init {
        scope.launch { restoration.await(); processQueue() }
        networkPolicy?.let { policy ->
            scope.launch {
                restoration.await()
                policy.ready.await()
                policy.state.collect { applyNetworkPolicy(it.allowed) }
            }
        }
    }

    /** 恢复为暂停状态，由用户点击继续；已完成章节只认完整的本地文件。 */
    private suspend fun restoreQueue() {
        val saved = queueStore?.load().orEmpty()
        stateMutex.withLock {
            saved.forEach { task ->
                val complete = chapterDao.getChaptersOnce(task.pathWord)
                    .filter { it.hasCompleteFiles() }.map { it.uuid }.toSet()
                val restored = task.copy(chapters = task.chapters.map {
                    val done = it.chapter.uuid in complete && (commentsRepository == null ||
                            commentsRepository.getDownloadedChapterComments(it.chapter.uuid)
                                .getOrNull()?.complete == true)
                    it.copy(
                        status = if (done) ChapterDownloadStatus.DOWNLOADED else ChapterDownloadStatus.WAITING,
                        progress = if (done) 1f else 0f, errorMessage = null
                    )
                })
                if (restored.chapters.all { it.status == ChapterDownloadStatus.DOWNLOADED }) {
                    queueStore?.remove(restored.pathWord)
                } else {
                    currentTasks = currentTasks + (restored.pathWord to restored)
                    taskInfoMap = taskInfoMap + (restored.pathWord to restored)
                    pausedComics = pausedComics + restored.pathWord
                    updateProgressForTask(restored)
                    updateProgress(
                        restored.pathWord, status = DownloadTaskStatus.PAUSED,
                        statusText = "队列已恢复，点击继续下载", speedBytesPerSecond = 0L
                    )
                }
            }
        }
    }

    /** 入队与修改章节时先落盘；保存失败时暂停，避免产生无法恢复的任务。 */
    private suspend fun persistTask(task: ComicDownloadTask): Boolean = try {
        queueStore?.save(task)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "保存下载队列失败", e)
        pausedComics = pausedComics + task.pathWord
        activeCalls[task.pathWord]?.cancel()
        activeCommentRequests[task.pathWord]?.cancel()
        updateProgress(
            task.pathWord,
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

    @Synchronized
    private fun downloadClient(): OkHttpClient {
        val policy = networkPolicy?.state?.value
        if (policy?.wifiOnly != true) return okHttpClient
        if (!policy.allowed) throw DownloadNetworkUnavailableException()
        val network = policy.network ?: throw DownloadNetworkUnavailableException()
        wifiClient?.takeIf { it.first == network }?.let { return it.second }
        wifiClient?.second?.connectionPool?.evictAll()
        return okHttpClient.newBuilder().socketFactory(network.socketFactory)
            .dns { host -> network.getAllByName(host).toList() }
            .connectionPool(ConnectionPool()).build().also { wifiClient = network to it }
    }

    private suspend fun applyNetworkPolicy(allowed: Boolean) {
        if (!allowed) {
            val calls = stateMutex.withLock {
                currentTasks.values.filter { task ->
                    task.pathWord !in pausedComics && task.chapters.any {
                        it.status == ChapterDownloadStatus.WAITING || it.status == ChapterDownloadStatus.DOWNLOADING
                    }
                }.forEach { task ->
                    networkWaiting = networkWaiting + task.pathWord
                    updateProgress(
                        task.pathWord, status = DownloadTaskStatus.WAITING_FOR_WIFI,
                        statusText = waitingForNetwork(), speedBytesPerSecond = 0L
                    )
                }
                activeCalls.filterKeys { it in networkWaiting }.values.toList()
            }
            calls.forEach { it.cancel() }
            activeCommentRequests.filterKeys { it in networkWaiting }.values.forEach { it.cancel() }
        } else {
            networkWaiting.toList().forEach { pathWord ->
                scope.launch {
                    val stopping = stateMutex.withLock { activeTaskCompletions[pathWord] }
                    stopping?.await()
                    stateMutex.withLock {
                        val task = currentTasks[pathWord] ?: return@withLock
                        if (!networkAllowed() || pathWord !in networkWaiting || pathWord in pausedComics) return@withLock
                        networkWaiting = networkWaiting - pathWord
                        val ready = task.copy(chapters = task.chapters.map {
                            if (it.status == ChapterDownloadStatus.DOWNLOADING) it.copy(
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

    fun setImageConcurrency(count: Int) {
        imageConcurrency = Semaphore(count.coerceIn(1, 10))
    }

    fun enqueue(task: ComicDownloadTask) {
        scope.launch {
            restoration.await()
            networkPolicy?.ready?.await()
            val stoppingDownloads = stateMutex.withLock {
                val existing = currentTasks[task.pathWord]
                if (existing == null) {
                    listOfNotNull(activeTaskCompletions[task.pathWord])
                } else {
                    val existingUuids = existing.chapters.map { it.chapter.uuid }.toSet()
                    task.chapters.filter { it.chapter.uuid !in existingUuids }
                        .mapNotNull { activeChapterCompletions[it.chapter.uuid] }
                }
            }
            stoppingDownloads.forEach { it.await() }
            stateMutex.withLock {
                val existing = currentTasks[task.pathWord]
                if (existing != null) {
                    // 合并：将新章节追加到已有任务中，避免覆盖正在下载的任务
                    val existingUuids = existing.chapters.map { it.chapter.uuid }.toSet()
                    val newChapters = task.chapters.filter { it.chapter.uuid !in existingUuids }
                    val requestedUuids = task.chapters.map { it.chapter.uuid }.toSet()
                    val retried = existing.chapters.map { item ->
                        if (item.chapter.uuid in requestedUuids && item.status == ChapterDownloadStatus.ERROR) {
                            item.copy(
                                status = ChapterDownloadStatus.WAITING,
                                progress = 0f,
                                errorMessage = null
                            )
                        } else item
                    }
                    if (newChapters.isNotEmpty() || retried != existing.chapters) {
                        val mergedChapters = retried + newChapters
                        val mergedTask = existing.copy(chapters = mergedChapters)
                        currentTasks = currentTasks + (task.pathWord to mergedTask)
                        taskInfoMap = taskInfoMap + (task.pathWord to mergedTask)
                        if (!persistTask(mergedTask)) return@withLock
                        updateProgressForTask(mergedTask)
                        if (ensureForeground(mergedTask)) launch { queue.send(mergedTask) }
                        Log.d(
                            TAG,
                            "enqueue (merged): ${task.name}, added ${newChapters.size} chapters, total ${mergedChapters.size}"
                        )
                    } else {
                        Log.d(TAG, "enqueue: ${task.name} - all chapters already in queue, skipped")
                    }
                } else {
                    currentTasks = currentTasks + (task.pathWord to task)
                    taskInfoMap = taskInfoMap + (task.pathWord to task)
                    if (!persistTask(task)) return@withLock
                    Log.d(TAG, "enqueue: ${task.name} (${task.totalChapters} chapters)")
                    updateProgressForTask(task)
                    if (ensureForeground(task)) launch { queue.send(task) }
                }
            }
        }
    }

    suspend fun cancelComic(pathWord: String) {
        restoration.await()
        val completion = stateMutex.withLock {
            queueStore?.remove(pathWord)
            networkWaiting = networkWaiting - pathWord
            pausedComics = pausedComics - pathWord
            currentTasks = currentTasks - pathWord
            taskInfoMap = taskInfoMap - pathWord
            val updated = _downloadProgress.value.toMutableMap()
            updated.remove(pathWord)
            _downloadProgress.value = updated
            if (_activeTask.value?.pathWord == pathWord) {
                _activeTask.value = null
            }
            activeTaskCompletions[pathWord]
        }
        activeCalls.remove(pathWord)?.cancel()
        activeCommentRequests.remove(pathWord)?.cancel()
        completion?.await()
        Log.d(TAG, "cancelComic: $pathWord (background I/O stopped, downloaded chapters preserved)")
    }

    fun pauseComic(pathWord: String) = scope.launch {
        restoration.await()
        val call = stateMutex.withLock {
            if (pathWord !in currentTasks) return@launch
            networkWaiting = networkWaiting - pathWord
            pausedComics = pausedComics + pathWord
            Log.d(TAG, "pauseComic: $pathWord")
            updateProgress(
                comicPathWord = pathWord,
                status = DownloadTaskStatus.PAUSED,
                statusText = "已暂停",
                speedBytesPerSecond = 0L
            )
            activeCalls[pathWord]
        }
        call?.cancel()
        activeCommentRequests[pathWord]?.cancel()
    }

    fun resumeComic(pathWord: String) = scope.launch {
        restoration.await()
        networkPolicy?.ready?.await()
        // 暂停会取消当前 Call；先等旧 I/O 退出，再清除暂停标记，防止取消被误判为错误。
        val stoppingTask = stateMutex.withLock { activeTaskCompletions[pathWord] }
        stoppingTask?.await()
        stateMutex.withLock {
            pausedComics = pausedComics - pathWord
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
                val resetChapters = task.chapters.map { chapter ->
                    if (chapter.status == ChapterDownloadStatus.ERROR) {
                        chapter.copy(
                            status = ChapterDownloadStatus.WAITING,
                            progress = 0f,
                            errorMessage = null
                        )
                    } else {
                        chapter
                    }
                }
                val resetTask = task.copy(chapters = resetChapters)
                currentTasks = currentTasks + (pathWord to resetTask)
                taskInfoMap = taskInfoMap + (pathWord to resetTask)
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
            pausedComics = pausedComics + currentTasks.keys
            networkWaiting = emptySet()
            currentTasks.keys.forEach { pathWord ->
                updateProgress(
                    pathWord,
                    status = DownloadTaskStatus.PAUSED,
                    statusText = reason,
                    errorMessage = if (reason == "已暂停") null else reason,
                    speedBytesPerSecond = 0L
                )
            }
            activeCalls.values.toList()
        }
        calls.forEach { it.cancel() }
        activeCommentRequests.values.forEach { it.cancel() }
    }

    suspend fun resumeAll() {
        restoration.await()
        currentTasks.keys.toList().forEach { resumeComic(it).join() }
    }

    /** 必须先发布任务，再启动前台服务，最后将任务交给下载循环。 */
    private fun ensureForeground(task: ComicDownloadTask): Boolean = try {
        startForegroundDownload()
        true
    } catch (e: RuntimeException) {
        Log.e(TAG, "无法启动下载前台服务", e)
        pausedComics = pausedComics + task.pathWord
        updateProgress(
            task.pathWord, status = DownloadTaskStatus.PAUSED,
            statusText = "后台下载暂不可用，请返回应用后继续",
            errorMessage = "系统未允许启动后台下载", speedBytesPerSecond = 0L
        )
        false
    }

    fun isPaused(pathWord: String): Boolean = pathWord in pausedComics

    suspend fun removeChaptersFromTask(comicPathWord: String, chapterUuids: Set<String>) {
        restoration.await()
        val completions = stateMutex.withLock {
            val task = currentTasks[comicPathWord] ?: return@withLock emptyList()
            val remainingChapters = task.chapters.filter { it.chapter.uuid !in chapterUuids }
            if (remainingChapters.isEmpty()) {
                queueStore?.remove(comicPathWord)
                networkWaiting = networkWaiting - comicPathWord
                pausedComics = pausedComics - comicPathWord
                currentTasks = currentTasks - comicPathWord
                taskInfoMap = taskInfoMap - comicPathWord
                val updated = _downloadProgress.value.toMutableMap()
                updated.remove(comicPathWord)
                _downloadProgress.value = updated
                if (_activeTask.value?.pathWord == comicPathWord) {
                    _activeTask.value = null
                }
                Log.d(TAG, "removeChaptersFromTask: $comicPathWord cleared")
            } else {
                val updatedTask = task.copy(chapters = remainingChapters)
                queueStore?.save(updatedTask)
                currentTasks = currentTasks + (comicPathWord to updatedTask)
                taskInfoMap = taskInfoMap + (comicPathWord to updatedTask)
                updateProgressForTask(updatedTask)
                Log.d(
                    TAG,
                    "removeChaptersFromTask: $comicPathWord, removed ${chapterUuids.size}, remaining ${remainingChapters.size}"
                )
            }
            chapterUuids.mapNotNull { activeChapterCompletions[it] }
        }
        if (completions.isNotEmpty()) {
            activeCalls.remove(comicPathWord)?.cancel()
            activeCommentRequests.remove(comicPathWord)?.cancel()
        }
        completions.forEach { it.await() }
    }

    suspend fun deleteChapterFiles(comicPathWord: String, chapterUuid: String) {
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "$DOWNLOAD_DIR/$comicPathWord/$chapterUuid")
            if (dir.exists() && !dir.deleteRecursively()) {
                Log.w(TAG, "Failed to delete chapter directory: ${dir.absolutePath}")
            }
        }
    }

    suspend fun deleteComicFiles(comicPathWord: String) {
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "$DOWNLOAD_DIR/$comicPathWord")
            if (dir.exists() && !dir.deleteRecursively()) {
                Log.w(TAG, "Failed to delete comic directory: ${dir.absolutePath}")
            }
        }
    }

    suspend fun cancelAll() {
        restoration.await()
        val completions = stateMutex.withLock {
            queueStore?.clear()
            networkWaiting = emptySet()
            currentTasks = emptyMap()
            taskInfoMap = emptyMap()
            pausedComics = emptySet()
            _downloadProgress.value = emptyMap()
            _activeTask.value = null
            while (queue.tryReceive().isSuccess) {
                // 排空尚未开始的任务。
            }
            activeTaskCompletions.values.toList()
        }
        activeCalls.values.forEach { it.cancel() }
        activeCalls.clear()
        activeCommentRequests.values.forEach { it.cancel() }
        activeCommentRequests.clear()
        completions.forEach { it.await() }
    }

    fun getTask(pathWord: String): ComicDownloadTask? = currentTasks[pathWord]

    private suspend fun processQueue() {
        for (task in queue) {
            val completion = CompletableDeferred<Unit>()
            val shouldProcess = stateMutex.withLock {
                !(currentTasks[task.pathWord]?.taskId != task.taskId || task.pathWord in pausedComics) && if (!networkAllowed() || task.pathWord in networkWaiting) {
                    networkWaiting = networkWaiting + task.pathWord
                    updateProgress(
                        task.pathWord, status = DownloadTaskStatus.WAITING_FOR_WIFI,
                        statusText = waitingForNetwork(), speedBytesPerSecond = 0L
                    )
                    false
                } else {
                    activeTaskCompletions[task.pathWord] = completion
                    true
                }
            }
            if (!shouldProcess) {
                if (networkAllowed() && networkWaiting.isNotEmpty()) applyNetworkPolicy(true)
                continue
            }

            _activeTask.value = task
            try {
                processComicTask(task)
            } finally {
                stateMutex.withLock {
                    activeTaskCompletions.remove(task.pathWord, completion)
                }
                completion.complete(Unit)
            }
            if (task.pathWord in pausedComics) {
                val updatedTask = currentTasks[task.pathWord]
                if (updatedTask != null) {
                    scope.launch { queue.send(updatedTask) }
                }
            } else {
                stateMutex.withLock {
                    val remaining = currentTasks[task.pathWord]
                    if (remaining?.taskId == task.taskId && remaining.chapters.all {
                            it.status == ChapterDownloadStatus.DOWNLOADED
                        }) {
                        currentTasks = currentTasks - task.pathWord
                        networkWaiting = networkWaiting - task.pathWord
                        try {
                            queueStore?.remove(task.pathWord)
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

    private suspend fun processComicTask(task: ComicDownloadTask) {
        val comicPathWord = task.pathWord

        // 使用 while 循环每次从 currentTasks 重新读取，以支持中途合并进来的新章节
        while (true) {
            if (!currentTasks.containsKey(comicPathWord)) {
                Log.d(TAG, "Task cancelled for $comicPathWord")
                return
            }
            if (comicPathWord in pausedComics) {
                Log.d(TAG, "Task paused for $comicPathWord")
                return
            }
            if (!networkAllowed() || comicPathWord in networkWaiting) {
                stateMutex.withLock {
                    networkWaiting = networkWaiting + comicPathWord
                    updateProgress(
                        comicPathWord, status = DownloadTaskStatus.WAITING_FOR_WIFI,
                        statusText = waitingForNetwork(), speedBytesPerSecond = 0L
                    )
                }
                return
            }

            // 选择章节、登记 I/O 完成信号和状态变更必须原子化，避免取消后旧快照复活任务。
            val pending = stateMutex.withLock {
                val currentTask = currentTasks[comicPathWord]
                    ?.takeIf { it.taskId == task.taskId } ?: return@withLock null
                if (comicPathWord in pausedComics) return@withLock null
                val chapters = currentTask.chapters
                val idx = chapters.indexOfFirst { it.status == ChapterDownloadStatus.WAITING }
                if (idx < 0) return@withLock null
                val chapterItem = chapters[idx]
                val updatedChapters = chapters.toMutableList()
                updatedChapters[idx] =
                    chapterItem.copy(status = ChapterDownloadStatus.DOWNLOADING, progress = 0f)
                val newTask = currentTask.copy(chapters = updatedChapters)
                currentTasks = currentTasks + (comicPathWord to newTask)
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
                    val latestTask = currentTasks[comicPathWord] ?: return
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

                        if (comicPathWord in pausedComics) {
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

                        val taskBeforeCommit = currentTasks[comicPathWord] ?: return
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
                            val latest = currentTasks[comicPathWord]
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

                val latest = currentTasks[comicPathWord]
                if (latest?.taskId != task.taskId) return
                if (latest.chapters.none { it.chapter.uuid == chapterItem.chapter.uuid }) continue
                updateProgress(
                    comicPathWord,
                    statusText = "正在下载章节评论",
                    speedBytesPerSecond = 0L
                )
                val commentResult = downloadCommentsForTask(comicPathWord, chapterItem.chapter.uuid)
                if (comicPathWord in pausedComics || !networkAllowed() || comicPathWord in networkWaiting) {
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
                if (e is DownloadNetworkUnavailableException || !networkAllowed() || comicPathWord in networkWaiting) {
                    markChapterWaiting(comicPathWord, chapterItem)
                    stateMutex.withLock {
                        if (comicPathWord !in pausedComics && currentTasks.containsKey(comicPathWord)) {
                            networkWaiting = networkWaiting + comicPathWord
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
                if (comicPathWord in pausedComics) {
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

            updateProgressForTask(currentTasks[comicPathWord])
        }

        stateMutex.withLock {
            val finalTask = currentTasks[comicPathWord]
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
            val restricted =
                networkPolicy?.let { policy -> async { policy.state.first { !it.allowed } } }
            try {
                select {
                    request.onAwait { it }
                    restricted?.onAwait { throw DownloadNetworkUnavailableException() }
                }
            } finally {
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
            activeCommentRequests[comic] = request
            val restricted =
                networkPolicy?.let { policy -> async { policy.state.first { !it.allowed } } }
            try {
                if (comic in pausedComics || currentTasks[comic]?.chapters?.none { it.chapter.uuid == uuid } != false) request.cancel()
                select {
                    request.onAwait { it }
                    restricted?.onAwait { Result.failure(IOException("等待网络连接")) }
                }
            } catch (error: CancellationException) {
                currentCoroutineContext().ensureActive()
                Result.failure(IOException("评论下载已暂停", error))
            } finally {
                activeCommentRequests.remove(comic, request)
                request.cancel()
                restricted?.cancel()
            }
        }
    }

    private suspend fun downloadChapterImages(
        comicPathWord: String,
        chapterUuid: String,
        imageUrls: List<String>,
        chapterIndex: Int,
        chapterName: String?
    ): List<String> = withContext(Dispatchers.IO) {
        if (!networkAllowed()) throw DownloadNetworkUnavailableException()
        // 仅 Wi-Fi 模式的图片连接绑定当前网络，断开后不重试到蜂窝连接。
        val client = downloadClient()
        val dir = File(context.filesDir, "$DOWNLOAD_DIR/$comicPathWord/$chapterUuid")
        if (!dir.exists()) dir.mkdirs()

        val totalImages = imageUrls.size
        val paths = mutableListOf<String>()

        var bytesSinceLastTick = 0L
        var lastTick = System.currentTimeMillis()

        for ((i, url) in imageUrls.withIndex()) {
            if (!networkAllowed() || comicPathWord in networkWaiting) throw DownloadNetworkUnavailableException()
            if (currentTasks[comicPathWord]?.chapters?.none { it.chapter.uuid == chapterUuid } != false) {
                throw IOException("下载已取消")
            }
            if (comicPathWord in pausedComics) {
                Log.d(TAG, "Paused during image download for $chapterUuid")
                break
            }
            val semaphore = imageConcurrency
            semaphore.acquire()
            try {
                val ext = url.substringAfterLast('.').substringBefore('?').ifEmpty { "jpg" }
                val file = File(dir, "${i.toString().padStart(3, '0')}.$ext")

                // 未提交章节的旧文件可能来自历史版本的中断写入，重试时重新获取。
                val request = Request.Builder().url(url).build()
                val call = client.newCall(request)
                activeCalls[comicPathWord] = call
                try {
                    if (!networkAllowed() || comicPathWord in networkWaiting) throw DownloadNetworkUnavailableException()
                    if (currentTasks[comicPathWord]?.chapters?.none { it.chapter.uuid == chapterUuid } != false) {
                        call.cancel()
                    }
                    call.execute().use { response ->

                        if (isRiskResponseCode(response.code)) {
                            response.close()
                            throw RiskControlException("服务器返回 ${response.code}，可能触发风控")
                        }

                        if (!response.isSuccessful) {
                            val code = response.code
                            response.close()
                            throw IOException("图片下载失败，HTTP $code")
                        }

                        val body = response.body

                        val contentType = response.header("Content-Type").orEmpty()
                        if (contentType.isNotBlank() &&
                            !contentType.startsWith("image/") &&
                            !contentType.contains("octet-stream")
                        ) {
                            val preview = try {
                                body.source().peek().readUtf8(512)
                            } catch (_: Throwable) {
                                ""
                            }
                            response.close()
                            if (isRiskHtml(preview)) {
                                throw RiskControlException("图片地址返回风控页面")
                            } else {
                                throw IOException("图片地址返回了非图片内容：$contentType")
                            }
                        }

                        file.parentFile?.mkdirs()

                        body.byteStream().use { input ->
                            writeDownloadedImage(file, input, body.contentLength()) { read ->
                                if (!networkAllowed() || comicPathWord in networkWaiting) throw DownloadNetworkUnavailableException()
                                if (comicPathWord in pausedComics) throw IOException("下载已暂停")
                                if (currentTasks[comicPathWord]?.chapters?.none { it.chapter.uuid == chapterUuid } != false) {
                                    throw IOException("下载已取消")
                                }
                                bytesSinceLastTick += read

                                val now = System.currentTimeMillis()
                                val elapsed = now - lastTick
                                if (elapsed >= 500L) {
                                    val speed = bytesSinceLastTick * 1000L / elapsed
                                    bytesSinceLastTick = 0L
                                    lastTick = now

                                    val imageProgress = i.toFloat() / totalImages.coerceAtLeast(1)
                                    updateProgress(
                                        comicPathWord = comicPathWord,
                                        status = DownloadTaskStatus.DOWNLOADING,
                                        statusText = "正在下载图片 ${i + 1}/$totalImages",
                                        currentChapterIndex = chapterIndex,
                                        currentChapterName = chapterName,
                                        currentChapterProgress = imageProgress,
                                        downloadedImages = i,
                                        totalImages = totalImages,
                                        speedBytesPerSecond = speed
                                    )
                                }
                            }
                        }
                        paths.add(file.absolutePath)
                    }
                } finally {
                    activeCalls.remove(comicPathWord, call)
                }

                val chapterProgress = (i + 1).toFloat() / totalImages.coerceAtLeast(1)
                updateProgress(
                    comicPathWord = comicPathWord,
                    status = DownloadTaskStatus.DOWNLOADING,
                    statusText = "正在下载图片 ${i + 1}/$totalImages",
                    currentChapterIndex = chapterIndex,
                    currentChapterName = chapterName,
                    currentChapterProgress = chapterProgress,
                    downloadedImages = i + 1,
                    totalImages = totalImages
                )
            } catch (e: RiskControlException) {
                Log.w(TAG, "下载图片触发风控: $url", e)
                throw e
            } catch (e: IOException) {
                Log.e(TAG, "下载图片失败: $url", e)
                throw e
            } finally {
                semaphore.release()
            }
        }
        paths
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
            val task = currentTasks[comicPathWord] ?: return
            if (task.chapters.none { it.chapter.uuid == chapterItem.chapter.uuid }) return
            pausedComics = pausedComics + comicPathWord
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
            val task = currentTasks[comicPathWord] ?: return@withLock
            val chapters = task.chapters.toMutableList()
            val idx = chapters.indexOfFirst { it.chapter.uuid == chapterItem.chapter.uuid }
            if (idx >= 0) {
                chapters[idx] = chapterItem.copy(
                    status = ChapterDownloadStatus.ERROR,
                    errorMessage = message
                )
                currentTasks = currentTasks + (comicPathWord to task.copy(chapters = chapters))
            }
        }
    }

    private suspend fun markChapterCompleted(
        comicPathWord: String,
        chapterItem: ChapterDownloadItem,
        warning: String? = null
    ) {
        stateMutex.withLock {
            val task = currentTasks[comicPathWord] ?: return@withLock
            val chapters = task.chapters.toMutableList()
            val idx = chapters.indexOfFirst { it.chapter.uuid == chapterItem.chapter.uuid }
            if (idx >= 0) {
                chapters[idx] = chapterItem.copy(
                    status = ChapterDownloadStatus.DOWNLOADED,
                    progress = 1f,
                    errorMessage = warning
                )
                currentTasks = currentTasks + (comicPathWord to task.copy(chapters = chapters))
            }
        }
    }

    private suspend fun markChapterWaiting(
        comicPathWord: String,
        chapterItem: ChapterDownloadItem
    ) {
        stateMutex.withLock {
            val task = currentTasks[comicPathWord] ?: return@withLock
            val chapters = task.chapters.toMutableList()
            val idx = chapters.indexOfFirst { it.chapter.uuid == chapterItem.chapter.uuid }
            if (idx >= 0) {
                chapters[idx] = chapterItem.copy(
                    status = ChapterDownloadStatus.WAITING,
                    progress = 0f
                )
                currentTasks = currentTasks + (comicPathWord to task.copy(chapters = chapters))
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
        val task = currentTasks[comicPathWord] ?: return
        val old = _downloadProgress.value[comicPathWord]

        if (comicPathWord in networkWaiting && status != null && status !in setOf(
                DownloadTaskStatus.WAITING_FOR_WIFI, DownloadTaskStatus.PAUSED,
                DownloadTaskStatus.PAUSED_BY_RISK, DownloadTaskStatus.COMPLETED
            )
        ) return

        if (comicPathWord in pausedComics &&
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

        val overall = if (totalChapters <= 0) {
            0f
        } else {
            // 已尝试的章节序号包含失败项，只能累加已完成章节和当前下载章节的进度。
            val inFlight =
                if (task.chapters.any { it.status == ChapterDownloadStatus.DOWNLOADING }) {
                    newChapterProgress
                } else 0f
            ((completedChapters + inFlight) / totalChapters).coerceIn(0f, 1f)
        }

        val info = DownloadProgressInfo(
            comicPathWord = comicPathWord,
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
            status = status ?: old?.status ?: DownloadTaskStatus.IDLE,
            statusText = statusText ?: old?.statusText ?: "等待中",
            errorMessage = errorMessage,
            downloadedImages = downloadedImages ?: old?.downloadedImages ?: 0,
            totalImages = totalImages ?: old?.totalImages ?: 0,
            speedBytesPerSecond = speedBytesPerSecond ?: old?.speedBytesPerSecond ?: 0L,
            taskId = task.taskId
        )

        val updated = _downloadProgress.value.toMutableMap()
        updated[comicPathWord] = info
        _downloadProgress.value = updated
        if (_activeTask.value?.pathWord == comicPathWord) {
            _activeTask.value = task
        }
    }

    private fun updateProgressForTask(task: ComicDownloadTask?) {
        if (task == null) return
        val currentChapter = task.chapters.firstOrNull {
            it.status == ChapterDownloadStatus.DOWNLOADING
        }
        val status = when {
            task.pathWord in pausedComics -> {
                val oldInfo = _downloadProgress.value[task.pathWord]
                if (oldInfo?.status == DownloadTaskStatus.PAUSED_BY_RISK) {
                    DownloadTaskStatus.PAUSED_BY_RISK
                } else {
                    DownloadTaskStatus.PAUSED
                }
            }

            task.chapters.all { it.status == ChapterDownloadStatus.DOWNLOADED } -> DownloadTaskStatus.COMPLETED
            task.pathWord in networkWaiting -> DownloadTaskStatus.WAITING_FOR_WIFI
            currentChapter != null -> DownloadTaskStatus.DOWNLOADING
            task.chapters.any { it.status == ChapterDownloadStatus.WAITING } -> DownloadTaskStatus.WAITING
            task.errorCount > 0 -> DownloadTaskStatus.ERROR
            else -> DownloadTaskStatus.IDLE
        }

        val old = _downloadProgress.value[task.pathWord]
        val statusText = when (status) {
            DownloadTaskStatus.COMPLETED -> if (task.chapters.any { it.errorMessage != null })
                "图片下载完成，部分评论未完整下载" else "章节和评论下载完成"

            DownloadTaskStatus.ERROR -> "已完成 ${task.completedChapters}/${task.totalChapters} 章，${task.errorCount} 章失败"
            DownloadTaskStatus.PAUSED_BY_RISK -> old?.statusText ?: "触发风控，已暂停下载"
            DownloadTaskStatus.PAUSED -> "已暂停"
            DownloadTaskStatus.WAITING_FOR_WIFI -> waitingForNetwork()
            else -> old?.statusText
        }
        val errorChapter = task.chapters.firstOrNull { it.status == ChapterDownloadStatus.ERROR }

        val info = DownloadProgressInfo(
            comicPathWord = task.pathWord,
            name = task.name,
            cover = task.cover,
            completedChapters = task.completedChapters,
            totalChapters = task.totalChapters,
            overallProgress = task.overallProgress,
            currentChapterIndex = task.chapters.indexOfFirst {
                it.status == ChapterDownloadStatus.DOWNLOADING
            }.coerceAtLeast(0),
            currentChapterName = currentChapter?.chapter?.name,
            currentChapterProgress = currentChapter?.progress ?: 0f,
            status = status,
            statusText = statusText ?: "等待中",
            errorMessage = old?.errorMessage ?: errorChapter?.errorMessage,
            downloadedImages = old?.downloadedImages ?: 0,
            totalImages = old?.totalImages ?: 0,
            speedBytesPerSecond = 0L,
            taskId = task.taskId
        )
        val updated = _downloadProgress.value.toMutableMap()
        updated[task.pathWord] = info
        _downloadProgress.value = updated
        if (_activeTask.value?.pathWord == task.pathWord) {
            _activeTask.value = task
        }
    }
}
