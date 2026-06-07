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
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

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

    init {
        scope.launch { processQueue() }
    }

    fun setImageConcurrency(count: Int) {
        imageConcurrency = Semaphore(count.coerceIn(1, 10))
    }

    fun enqueue(task: ComicDownloadTask) {
        currentTasks[task.pathWord] = task
        scope.launch { queue.send(task) }
        updateProgressForTask(task)
    }

    fun cancelComic(pathWord: String) {
        scope.launch {
            currentTasks.remove(pathWord)
            val updated = _downloadProgress.value.toMutableMap()
            updated.remove(pathWord)
            _downloadProgress.value = updated
            if (_activeTask.value?.pathWord == pathWord) {
                _activeTask.value = null
            }
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
            _activeTask.value = task
            processComicTask(task)
            currentTasks.remove(task.pathWord)
            _activeTask.value = null
        }
    }

    private suspend fun processComicTask(task: ComicDownloadTask) {
        val comicPathWord = task.pathWord
        val chaptersToDownload = task.chapters.filter {
            it.status == ChapterDownloadStatus.WAITING || it.status == ChapterDownloadStatus.ERROR
        }

        for ((index, chapterItem) in chaptersToDownload.withIndex()) {
            if (!currentTasks.containsKey(comicPathWord)) {
                Log.d(TAG, "Task cancelled for $comicPathWord")
                return
            }

            val updatedTask = currentTasks[comicPathWord] ?: return
            val updatedChapters = updatedTask.chapters.toMutableList()
            val idx = updatedChapters.indexOfFirst { it.chapter.uuid == chapterItem.chapter.uuid }
            if (idx >= 0) {
                updatedChapters[idx] = chapterItem.copy(
                    status = ChapterDownloadStatus.DOWNLOADING,
                    progress = 0f
                )
            }
            currentTasks[comicPathWord] = updatedTask.copy(chapters = updatedChapters)
            updateProgressForTask(currentTasks[comicPathWord]!!)

            try {
                val chapterContent = downloadChapterContent(
                    chapterItem.chapter,
                    comicPathWord
                )
                if (chapterContent != null) {
                    val imagePaths = downloadChapterImages(
                        comicPathWord,
                        chapterItem.chapter.uuid,
                        chapterContent.imageUrls
                    )

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

                    if (idx >= 0) {
                        updatedChapters[idx] = chapterItem.copy(
                            status = ChapterDownloadStatus.DOWNLOADED,
                            progress = 1f
                        )
                    }
                    currentTasks[comicPathWord] = updatedTask.copy(chapters = updatedChapters)
                    comicDao.updateChapterCount(comicPathWord, updatedChapters.count {
                        it.status == ChapterDownloadStatus.DOWNLOADED
                    })
                } else {
                    markChapterError(
                        comicPathWord,
                        chapterItem,
                        idx,
                        updatedChapters,
                        "获取章节内容失败"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "下载章节失败: ${chapterItem.chapter.name}", e)
                markChapterError(
                    comicPathWord,
                    chapterItem,
                    idx,
                    updatedChapters,
                    e.message ?: "未知错误"
                )
            }

            updateProgressForTask(currentTasks[comicPathWord]!!)
        }

        val finalTask = currentTasks[comicPathWord]
        if (finalTask != null && finalTask.chapters.all {
                it.status == ChapterDownloadStatus.DOWNLOADED || it.status == ChapterDownloadStatus.ERROR
            }) {
            updateProgressForTask(
                finalTask.copy(
                chapters = finalTask.chapters.map { it.copy(progress = 1f) }
            ))
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
        imageUrls: List<String>
    ): List<String> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "$DOWNLOAD_DIR/$comicPathWord/$chapterUuid")
        if (!dir.exists()) dir.mkdirs()

        val paths = mutableListOf<String>()
        for ((i, url) in imageUrls.withIndex()) {
            imageConcurrency.acquire()
            try {
                val ext = url.substringAfterLast('.').substringBefore('?').ifEmpty { "jpg" }
                val file = File(dir, "${i.toString().padStart(3, '0')}.$ext")

                if (file.exists() && file.length() > 0) {
                    paths.add(file.absolutePath)
                    continue
                }

                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body
                responseBody.source().let { source ->
                    file.sink().buffer().use { it.writeAll(source) }
                }
                response.close()

                if (file.exists() && file.length() > 0) {
                    paths.add(file.absolutePath)
                }
            } catch (e: IOException) {
                Log.e(TAG, "下载图片失败: $url", e)
            } finally {
                imageConcurrency.release()
            }
        }
        paths
    }

    private fun markChapterError(
        comicPathWord: String,
        chapterItem: ChapterDownloadItem,
        idx: Int,
        updatedChapters: MutableList<ChapterDownloadItem>,
        message: String
    ) {
        if (idx >= 0) {
            updatedChapters[idx] = chapterItem.copy(
                status = ChapterDownloadStatus.ERROR,
                errorMessage = message
            )
        }
        currentTasks[comicPathWord]?.let { task ->
            currentTasks[comicPathWord] = task.copy(chapters = updatedChapters)
        }
    }

    private fun updateProgressForTask(task: ComicDownloadTask) {
        val currentChapter = task.chapters.firstOrNull {
            it.status == ChapterDownloadStatus.DOWNLOADING
        }
        val info = DownloadProgressInfo(
            comicPathWord = task.pathWord,
            completedChapters = task.completedChapters,
            totalChapters = task.totalChapters,
            overallProgress = task.overallProgress,
            currentChapterIndex = task.chapters.indexOfFirst {
                it.status == ChapterDownloadStatus.DOWNLOADING
            }.coerceAtLeast(0),
            currentChapterName = currentChapter?.chapter?.name,
            currentChapterProgress = currentChapter?.progress ?: 0f,
            status = when {
                task.chapters.all { it.status == ChapterDownloadStatus.DOWNLOADED } -> DownloadTaskStatus.COMPLETED
                currentChapter != null -> DownloadTaskStatus.DOWNLOADING
                task.errorCount > 0 -> DownloadTaskStatus.ERROR
                else -> DownloadTaskStatus.IDLE
            }
        )
        val updated = _downloadProgress.value.toMutableMap()
        updated[task.pathWord] = info
        _downloadProgress.value = updated
    }
}
