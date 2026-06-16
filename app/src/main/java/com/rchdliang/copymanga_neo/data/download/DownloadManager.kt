package com.rchdliang.copymanga_neo.data.download

import android.content.Context
import android.util.Log
import com.rchdliang.copymanga_neo.data.local.db.DownloadedChapterDao
import com.rchdliang.copymanga_neo.data.local.db.DownloadedComicDao
import com.rchdliang.copymanga_neo.data.local.entity.DownloadedChapterEntity
import com.rchdliang.copymanga_neo.data.remote.dto.ChapterDto
import com.rchdliang.copymanga_neo.domain.model.ChapterContent
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
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
    private val queueMutex = Mutex()
    private var imageConcurrency = Semaphore(3)

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgressInfo>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgressInfo>> =
        _downloadProgress.asStateFlow()

    private val _activeTask = MutableStateFlow<ComicDownloadTask?>(null)
    val activeTask: StateFlow<ComicDownloadTask?> = _activeTask.asStateFlow()

    private var currentTasks = mutableMapOf<String, ComicDownloadTask>()
    private val pausedComics = mutableSetOf<String>()
    private val taskInfoMap = mutableMapOf<String, ComicDownloadTask>()

    init {
        scope.launch { processQueue() }
    }

    fun setImageConcurrency(count: Int) {
        imageConcurrency = Semaphore(count.coerceIn(1, 10))
    }

    fun enqueue(task: ComicDownloadTask) {
        val existing = currentTasks[task.pathWord]
        if (existing != null) {
            // 合并：将新章节追加到已有任务中，避免覆盖正在下载的任务
            val existingUuids = existing.chapters.map { it.chapter.uuid }.toSet()
            val newChapters = task.chapters.filter { it.chapter.uuid !in existingUuids }
            if (newChapters.isNotEmpty()) {
                val mergedChapters = existing.chapters + newChapters
                val mergedTask = existing.copy(chapters = mergedChapters)
                currentTasks[task.pathWord] = mergedTask
                taskInfoMap[task.pathWord] = mergedTask
                updateProgressForTask(mergedTask)
                Log.d(
                    TAG,
                    "enqueue (merged): ${task.name}, added ${newChapters.size} chapters, total ${mergedChapters.size}"
                )
            } else {
                Log.d(TAG, "enqueue: ${task.name} - all chapters already in queue, skipped")
            }
        } else {
            currentTasks[task.pathWord] = task
            taskInfoMap[task.pathWord] = task
            Log.d(TAG, "enqueue: ${task.name} (${task.totalChapters} chapters)")
            scope.launch { queue.send(task) }
            updateProgressForTask(task)
        }
    }

    fun cancelComic(pathWord: String) {
        scope.launch {
            pausedComics.remove(pathWord)
            currentTasks.remove(pathWord)
            taskInfoMap.remove(pathWord)
            val updated = _downloadProgress.value.toMutableMap()
            updated.remove(pathWord)
            _downloadProgress.value = updated
            if (_activeTask.value?.pathWord == pathWord) {
                _activeTask.value = null
            }
            Log.d(TAG, "cancelComic: $pathWord (task cancelled, downloaded chapters preserved)")
        }
    }

    fun pauseComic(pathWord: String) {
        pausedComics.add(pathWord)
        Log.d(TAG, "pauseComic: $pathWord")
        updateProgress(
            comicPathWord = pathWord,
            status = DownloadTaskStatus.PAUSED,
            statusText = "已暂停",
            speedBytesPerSecond = 0L
        )
    }

    fun resumeComic(pathWord: String) {
        pausedComics.remove(pathWord)
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
            currentTasks[pathWord] = resetTask
            taskInfoMap[pathWord] = resetTask
            scope.launch { queue.send(resetTask) }
        }
    }

    fun isPaused(pathWord: String): Boolean = pathWord in pausedComics

    fun removeChaptersFromTask(comicPathWord: String, chapterUuids: Set<String>) {
        val task = currentTasks[comicPathWord] ?: return
        val remainingChapters = task.chapters.filter { it.chapter.uuid !in chapterUuids }
        if (remainingChapters.isEmpty()) {
            cancelComic(comicPathWord)
        } else {
            val updatedTask = task.copy(chapters = remainingChapters)
            currentTasks[comicPathWord] = updatedTask
            taskInfoMap[comicPathWord] = updatedTask
            updateProgressForTask(updatedTask)
            Log.d(
                TAG,
                "removeChaptersFromTask: $comicPathWord, removed ${chapterUuids.size}, remaining ${remainingChapters.size}"
            )
        }
    }

    fun deleteChapterFiles(comicPathWord: String, chapterUuid: String) {
        val dir = File(context.filesDir, "$DOWNLOAD_DIR/$comicPathWord/$chapterUuid")
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    fun deleteComicFiles(comicPathWord: String) {
        val dir = File(context.filesDir, "$DOWNLOAD_DIR/$comicPathWord")
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.deleteRecursively() }
            dir.delete()
        }
    }

    fun cancelAll() {
        scope.launch {
            currentTasks.clear()
            _downloadProgress.value = emptyMap()
            _activeTask.value = null
            while (queue.tryReceive().isSuccess) {
            }
        }
    }

    fun getTask(pathWord: String): ComicDownloadTask? = currentTasks[pathWord]

    private suspend fun processQueue() {
        for (task in queue) {
            if (!currentTasks.containsKey(task.pathWord)) continue
            if (task.pathWord in pausedComics) continue
            _activeTask.value = task
            processComicTask(task)
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
                    currentTasks.remove(task.pathWord)
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
            currentTasks[comicPathWord] = currentTask.copy(chapters = updatedChapters)
            updateProgressForTask(currentTasks[comicPathWord]!!)

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
            }

            updateProgressForTask(currentTasks[comicPathWord]!!)
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
        chapter: ChapterDto,
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
                val response = okHttpClient.newCall(request).execute()

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
                if (body == null) {
                    response.close()
                    throw IOException("图片响应为空")
                }

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

    private fun handleRiskControl(
        comicPathWord: String,
        chapterItem: ChapterDownloadItem,
        message: String
    ) {
        pausedComics.add(comicPathWord)
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

    private fun markChapterError(
        comicPathWord: String,
        chapterItem: ChapterDownloadItem,
        message: String
    ) {
        val task = currentTasks[comicPathWord] ?: return
        val chapters = task.chapters.toMutableList()
        val idx = chapters.indexOfFirst { it.chapter.uuid == chapterItem.chapter.uuid }
        if (idx >= 0) {
            chapters[idx] = chapterItem.copy(
                status = ChapterDownloadStatus.ERROR,
                errorMessage = message
            )
        }
        currentTasks[comicPathWord] = task.copy(chapters = chapters)
    }

    private fun markChapterCompleted(
        comicPathWord: String,
        chapterItem: ChapterDownloadItem
    ) {
        val task = currentTasks[comicPathWord] ?: return
        val chapters = task.chapters.toMutableList()
        val idx = chapters.indexOfFirst { it.chapter.uuid == chapterItem.chapter.uuid }
        if (idx >= 0) {
            chapters[idx] = chapterItem.copy(
                status = ChapterDownloadStatus.DOWNLOADED,
                progress = 1f
            )
        }
        currentTasks[comicPathWord] = task.copy(chapters = chapters)
    }

    private fun markChapterWaiting(
        comicPathWord: String,
        chapterItem: ChapterDownloadItem
    ) {
        val task = currentTasks[comicPathWord] ?: return
        val chapters = task.chapters.toMutableList()
        val idx = chapters.indexOfFirst { it.chapter.uuid == chapterItem.chapter.uuid }
        if (idx >= 0) {
            chapters[idx] = chapterItem.copy(
                status = ChapterDownloadStatus.WAITING,
                progress = 0f
            )
        }
        currentTasks[comicPathWord] = task.copy(chapters = chapters)
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
            val baseCompleted = completedChapters.toFloat()
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

    private fun updateProgressForTask(task: ComicDownloadTask) {
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
