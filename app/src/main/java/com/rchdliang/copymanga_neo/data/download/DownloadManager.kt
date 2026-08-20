package com.rchdliang.copymanga_neo.data.download

import android.content.Context
import android.util.Log
import com.rchdliang.copymanga_neo.data.local.db.DownloadedChapterDao
import com.rchdliang.copymanga_neo.data.local.db.DownloadedComicDao
import com.rchdliang.copymanga_neo.data.local.entity.DownloadedChapterEntity
import com.rchdliang.copymanga_neo.domain.model.ChapterContent
import com.rchdliang.copymanga_neo.domain.model.ChapterInfo
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

class RiskControlException(message: String) : IOException(message)

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mangaRepository: MangaRepository,
    private val comicDao: DownloadedComicDao,
    private val chapterDao: DownloadedChapterDao,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "DownloadManager"
        private const val DOWNLOAD_DIR = "downloads"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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

    /** 对 currentTasks / taskInfoMap / pausedComics 写操作的串行化锁。读不持锁，见上注释。 */
    private val stateMutex = Mutex()

    /** 当前漫画任务与章节的完成信号，用于让取消/删除操作等待后台 I/O 真正退出。 */
    private val activeTaskCompletions = mutableMapOf<String, CompletableDeferred<Unit>>()
    private val activeChapterCompletions = mutableMapOf<String, CompletableDeferred<Unit>>()

    /** 当前图片请求；取消漫画或章节时会主动终止对应的 OkHttp Call。 */
    private val activeCalls = ConcurrentHashMap<String, okhttp3.Call>()

    init {
        scope.launch { processQueue() }
    }

    fun setImageConcurrency(count: Int) {
        imageConcurrency = Semaphore(count.coerceIn(1, 10))
    }

    fun enqueue(task: ComicDownloadTask) {
        scope.launch {
            stateMutex.withLock {
                val existing = currentTasks[task.pathWord]
                if (existing != null) {
                    // 合并：将新章节追加到已有任务中，避免覆盖正在下载的任务
                    val existingUuids = existing.chapters.map { it.chapter.uuid }.toSet()
                    val newChapters = task.chapters.filter { it.chapter.uuid !in existingUuids }
                    if (newChapters.isNotEmpty()) {
                        val mergedChapters = existing.chapters + newChapters
                        val mergedTask = existing.copy(chapters = mergedChapters)
                        currentTasks = currentTasks + (task.pathWord to mergedTask)
                        taskInfoMap = taskInfoMap + (task.pathWord to mergedTask)
                        updateProgressForTask(mergedTask)
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
                    Log.d(TAG, "enqueue: ${task.name} (${task.totalChapters} chapters)")
                    launch { queue.send(task) }
                    updateProgressForTask(task)
                }
            }
        }
    }

    suspend fun cancelComic(pathWord: String) {
        val completion = stateMutex.withLock {
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
        completion?.await()
        Log.d(TAG, "cancelComic: $pathWord (background I/O stopped, downloaded chapters preserved)")
    }

    fun pauseComic(pathWord: String) {
        scope.launch {
            stateMutex.withLock {
                pausedComics = pausedComics + pathWord
                Log.d(TAG, "pauseComic: $pathWord")
                updateProgress(
                    comicPathWord = pathWord,
                    status = DownloadTaskStatus.PAUSED,
                    statusText = "已暂停",
                    speedBytesPerSecond = 0L
                )
            }
        }
    }

    fun resumeComic(pathWord: String) {
        scope.launch {
            stateMutex.withLock {
                pausedComics = pausedComics - pathWord
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
                    launch { queue.send(resetTask) }
                }
            }
        }
    }

    fun isPaused(pathWord: String): Boolean = pathWord in pausedComics

    suspend fun removeChaptersFromTask(comicPathWord: String, chapterUuids: Set<String>) {
        val completions = stateMutex.withLock {
            val task = currentTasks[comicPathWord] ?: return@withLock emptyList()
            val remainingChapters = task.chapters.filter { it.chapter.uuid !in chapterUuids }
            if (remainingChapters.isEmpty()) {
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
        val completions = stateMutex.withLock {
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
        completions.forEach { it.await() }
    }

    fun getTask(pathWord: String): ComicDownloadTask? = currentTasks[pathWord]

    private suspend fun processQueue() {
        for (task in queue) {
            val completion = CompletableDeferred<Unit>()
            val shouldProcess = stateMutex.withLock {
                if (!currentTasks.containsKey(task.pathWord) || task.pathWord in pausedComics) {
                    false
                } else {
                    activeTaskCompletions[task.pathWord] = completion
                    true
                }
            }
            if (!shouldProcess) continue

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
                val remaining = currentTasks[task.pathWord]
                if (remaining == null || remaining.chapters.all {
                        it.status == ChapterDownloadStatus.DOWNLOADED
                    }) {
                    stateMutex.withLock {
                        currentTasks = currentTasks - task.pathWord
                    }
                }
            }
            _activeTask.value = null
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

            val currentTask = currentTasks[comicPathWord] ?: return
            val chapters = currentTask.chapters
            val chapterItem = chapters.firstOrNull {
                it.status == ChapterDownloadStatus.WAITING
            } ?: break  // 没有待处理的章节，退出循环

            if (comicPathWord in pausedComics) {
                Log.d(TAG, "Task paused for $comicPathWord (before chapter start)")
                return
            }

            val idx = chapters.indexOfFirst { it.chapter.uuid == chapterItem.chapter.uuid }

            // 标记为 DOWNLOADING
            val updatedChapters = chapters.toMutableList()
            updatedChapters[idx] = chapterItem.copy(
                status = ChapterDownloadStatus.DOWNLOADING,
                progress = 0f
            )
            val newTask = currentTask.copy(chapters = updatedChapters)
            stateMutex.withLock {
                currentTasks = currentTasks + (comicPathWord to newTask)
            }
            updateProgressForTask(newTask)

            val chapterCompletion = CompletableDeferred<Unit>()
            stateMutex.withLock {
                activeChapterCompletions[chapterItem.chapter.uuid] = chapterCompletion
            }

            try {
                val chapterIndex = idx + 1
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

                    if (imagePaths.size < imageUrls.size) {
                        Log.w(
                            TAG,
                            "Chapter ${chapterItem.chapter.name} incomplete: ${imagePaths.size}/${imageUrls.size}"
                        )
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
                        imageCount = imagePaths.size,
                        imagePaths = imagePaths.joinToString(","),
                        status = "downloaded",
                        downloadAt = System.currentTimeMillis()
                    )
                    chapterDao.insertChapter(entity)

                    markChapterCompleted(comicPathWord, chapterItem)
                    val roomChapterCount = chapterDao.getChapterUuids(comicPathWord).size
                    comicDao.updateChapterCount(comicPathWord, roomChapterCount)

                    updateProgress(
                        comicPathWord = comicPathWord,
                        status = DownloadTaskStatus.DOWNLOADING,
                        statusText = "章节下载完成",
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
                }
            } catch (e: RiskControlException) {
                Log.w(TAG, "下载章节触发风控: ${chapterItem.chapter.name}", e)
                handleRiskControl(
                    comicPathWord,
                    chapterItem,
                    e.message ?: "触发网站风控，已暂停下载"
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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

        val finalTask = currentTasks[comicPathWord]
        if (finalTask != null && finalTask.chapters.all {
                it.status == ChapterDownloadStatus.DOWNLOADED
            }) {
            updateProgress(
                comicPathWord = comicPathWord,
                status = DownloadTaskStatus.COMPLETED,
                statusText = "下载完成",
                currentChapterProgress = 1f,
                speedBytesPerSecond = 0L,
                errorMessage = null
            )
        }
    }

    private suspend fun downloadChapterContent(
        chapter: ChapterInfo,
        comicPathWord: String
    ): ChapterContent? {
        return mangaRepository.getChapterContent(
            comicPathWord = comicPathWord,
            chapterUuid = chapter.uuid,
            group = chapter.groupPathWord ?: "default"
        ).getOrNull()
    }

    private suspend fun downloadChapterImages(
        comicPathWord: String,
        chapterUuid: String,
        imageUrls: List<String>,
        chapterIndex: Int,
        chapterName: String?
    ): List<String> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "$DOWNLOAD_DIR/$comicPathWord/$chapterUuid")
        if (!dir.exists()) dir.mkdirs()

        val totalImages = imageUrls.size
        val paths = mutableListOf<String>()

        var bytesSinceLastTick = 0L
        var lastTick = System.currentTimeMillis()

        for ((i, url) in imageUrls.withIndex()) {
            if (comicPathWord in pausedComics) {
                Log.d(TAG, "Paused during image download for $chapterUuid")
                break
            }
            imageConcurrency.acquire()
            try {
                val ext = url.substringAfterLast('.').substringBefore('?').ifEmpty { "jpg" }
                val file = File(dir, "${i.toString().padStart(3, '0')}.$ext")

                if (file.exists() && file.length() > 0) {
                    paths.add(file.absolutePath)
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
                    continue
                }

                val request = Request.Builder().url(url).build()
                val call = okHttpClient.newCall(request)
                activeCalls[comicPathWord] = call
                val response = try {
                    call.execute()
                } finally {
                    activeCalls.remove(comicPathWord, call)
                }

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

                val buffer = ByteArray(8192)
                var totalRead = 0L

                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            totalRead += read
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
                }

                response.close()

                if (file.exists() && file.length() > 0) {
                    paths.add(file.absolutePath)
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
                imageConcurrency.release()
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
        chapterItem: ChapterDownloadItem
    ) {
        stateMutex.withLock {
            val task = currentTasks[comicPathWord] ?: return@withLock
            val chapters = task.chapters.toMutableList()
            val idx = chapters.indexOfFirst { it.chapter.uuid == chapterItem.chapter.uuid }
            if (idx >= 0) {
                chapters[idx] = chapterItem.copy(
                    status = ChapterDownloadStatus.DOWNLOADED,
                    progress = 1f
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
            val newStatus = status ?: old?.status ?: DownloadTaskStatus.IDLE
            if (newChapterIndex > 0 && newStatus != DownloadTaskStatus.COMPLETED) {
                val dynamicProgress =
                    ((newChapterIndex - 1).coerceAtLeast(0) + newChapterProgress) / totalChapters
                dynamicProgress.coerceIn(0f, 1f)
            } else {
                task.overallProgress.coerceIn(0f, 1f)
            }
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
            speedBytesPerSecond = speedBytesPerSecond ?: old?.speedBytesPerSecond ?: 0L
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
            currentChapter != null -> DownloadTaskStatus.DOWNLOADING
            task.errorCount > 0 -> DownloadTaskStatus.ERROR
            else -> DownloadTaskStatus.IDLE
        }

        val old = _downloadProgress.value[task.pathWord]
        val statusText = when (status) {
            DownloadTaskStatus.COMPLETED -> "下载完成"
            DownloadTaskStatus.ERROR -> old?.statusText ?: "下载出错"
            DownloadTaskStatus.PAUSED_BY_RISK -> old?.statusText ?: "触发风控，已暂停下载"
            DownloadTaskStatus.PAUSED -> "已暂停"
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
            statusText = statusText ?: old?.statusText ?: "等待中",
            errorMessage = old?.errorMessage ?: errorChapter?.errorMessage,
            downloadedImages = old?.downloadedImages ?: 0,
            totalImages = old?.totalImages ?: 0,
            speedBytesPerSecond = 0L
        )
        val updated = _downloadProgress.value.toMutableMap()
        updated[task.pathWord] = info
        _downloadProgress.value = updated
        if (_activeTask.value?.pathWord == task.pathWord) {
            _activeTask.value = task
        }
    }
}
