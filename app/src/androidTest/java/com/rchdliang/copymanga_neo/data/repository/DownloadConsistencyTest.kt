package com.rchdliang.copymanga_neo.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rchdliang.copymanga_neo.data.download.ChapterDownloadItem
import com.rchdliang.copymanga_neo.data.download.ChapterDownloadStatus
import com.rchdliang.copymanga_neo.data.download.ComicDownloadTask
import com.rchdliang.copymanga_neo.data.download.DownloadManager
import com.rchdliang.copymanga_neo.data.download.DownloadTaskStatus
import com.rchdliang.copymanga_neo.data.local.ReaderPreferencesStore
import com.rchdliang.copymanga_neo.data.local.db.AppDatabase
import com.rchdliang.copymanga_neo.data.local.entity.DownloadedChapterEntity
import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import com.rchdliang.copymanga_neo.domain.model.ChapterContent
import com.rchdliang.copymanga_neo.domain.model.ChapterInfo
import com.rchdliang.copymanga_neo.domain.model.ComicDetail
import com.rchdliang.copymanga_neo.domain.model.DownloadedComicInfo
import com.rchdliang.copymanga_neo.domain.model.Theme
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.ThemeBlacklistRepository
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import com.rchdliang.copymanga_neo.ui.detail.DetailViewModel
import com.rchdliang.copymanga_neo.ui.reader.ReaderUiState
import com.rchdliang.copymanga_neo.ui.reader.ReaderViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class DownloadConsistencyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val comic = "download-test-${UUID.randomUUID()}"
    private lateinit var db: AppDatabase
    private lateinit var manager: DownloadManager
    private lateinit var downloads: DownloadRepositoryImpl
    private val store = ViewModelStore()
    private var content: suspend (String) -> Result<ChapterContent> = {
        Result.success(ChapterContent(listOf("https://example.com/page.jpg"), null, null, it))
    }
    private var metadata: suspend (String, String) -> Result<ChapterInfo> = { uuid, group ->
        Result.success(chapter(uuid).copy(groupPathWord = group))
    }
    private var requestedImages = 0
    private var foregroundAllowed = true
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        requestedImages++
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(200).message("test").header("Content-Type", "image/jpeg")
            .body(byteArrayOf(1, 2, 3).toResponseBody("image/jpeg".toMediaType())).build()
    }.build()
    private val service = Retrofit.Builder().baseUrl("https://example.com/")
        .addConverterFactory(GsonConverterFactory.create()).build()
        .create(MangaApiService::class.java)
    private val networkSettings =
        com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore(context)
    private val remote = object : MangaRepository by MangaRepositoryImpl(
        service, service, networkSettings = networkSettings,
        officialContent = OfficialAppContentSource(service, networkSettings)
    ) {
        override suspend fun getChapterCatalog(
            pathWord: String, forceRefresh: Boolean
        ): Result<com.rchdliang.copymanga_neo.domain.model.ChapterCatalog> =
            Result.failure(IOException("offline"))

        override suspend fun getChapters(
            pathWord: String, group: String, offset: Int, limit: Int
        ): Result<com.rchdliang.copymanga_neo.domain.model.ChapterList> =
            Result.failure(IOException("offline"))

        override suspend fun getComicDetail(pathWord: String): Result<ComicDetail> =
            Result.failure(IOException("offline"))

        override suspend fun getChapterContent(
            comicPathWord: String,
            chapterUuid: String,
            group: String
        ) = content(chapterUuid)

        override suspend fun getChapterInfo(
            comicPathWord: String,
            chapterUuid: String,
            group: String
        ) = metadata(chapterUuid, group)
    }

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        manager = DownloadManager(
            context,
            remote,
            db.downloadedComicDao(),
            db.downloadedChapterDao(),
            client,
            startForegroundDownload = { check(foregroundAllowed) { "foreground start denied" } }
        )
        downloads =
            DownloadRepositoryImpl(db.downloadedComicDao(), db.downloadedChapterDao(), manager)
        downloads.saveComicCache(comicInfo())
    }

    @After
    fun tearDown(): Unit = runBlocking {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        manager.cancelAll()
        db.close()
        File(context.filesDir, "downloads/$comic").deleteRecursively()
    }

    @Test
    fun foregroundStartFailurePausesQueueBeforeAnyNetworkRequest() = runBlocking {
        foregroundAllowed = false
        downloads.enqueueDownload(task("one"))
        awaitStatus(DownloadTaskStatus.PAUSED)
        assertEquals(0, requestedImages)
        assertNotNull(manager.getTask(comic))
        foregroundAllowed = true
        downloads.resumeComic(comic)
        awaitStatus(DownloadTaskStatus.COMPLETED)
        assertEquals(1, downloads.getDownloadedChapters(comic).size)
    }

    @Test
    fun immediateResumeWaitsForPausedChapterRequestToExit() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var requests = 0
        content = {
            if (++requests == 1) {
                started.complete(Unit)
                release.await()
            }
            Result.success(ChapterContent(listOf("https://example.com/page.jpg"), null, null, it))
        }
        downloads.enqueueDownload(task("one"))
        withTimeout(5000.milliseconds) { started.await() }
        manager.pauseAll().join()
        val resume = manager.resumeComic(comic)
        assertTrue(manager.isPaused(comic))
        release.complete(Unit)
        withTimeout(5000.milliseconds) { resume.join() }
        awaitStatus(DownloadTaskStatus.COMPLETED)
        assertEquals(2, requests)
        assertEquals(1, requestedImages)
        assertEquals(1, downloads.getDownloadedChapters(comic).size)
    }

    @Test
    fun metadataAndFailedRecordsDoNotCountAsDownloaded() = runBlocking {
        assertTrue(downloads.getAllDownloadedComics().first().isEmpty())
        assertFalse(downloads.isComicDownloaded(comic))
        db.downloadedChapterDao().insertChapter(entity("failed").copy(status = "error"))
        db.downloadedChapterDao()
            .insertChapter(entity("missing").copy(imagePaths = "/missing/page.jpg"))
        assertTrue(downloads.getAllDownloadedComics().first().isEmpty())
        assertTrue(downloads.getDownloadedChapterUuids(comic).isEmpty())
    }

    @Test
    fun actualChaptersOverridePlannedCountersAndDeletionRemovesLastItem() = runBlocking {
        db.downloadedChapterDao().insertChapter(entity("one"))
        db.downloadedChapterDao().insertChapter(entity("two"))
        downloads.saveComicCache(comicInfo().copy(totalChapters = 999, downloadedChapters = 999))
        val saved = downloads.getAllDownloadedComics().first().single()
        assertEquals(2, saved.downloadedChapters)
        assertEquals(2, saved.totalChapters)
        assertEquals(2, downloads.getDownloadedChapters(comic).size)
        downloads.deleteChapters(comic, listOf("one", "two"))
        assertTrue(downloads.getAllDownloadedComics().first().isEmpty())
        assertFalse(downloads.isComicDownloaded(comic))
    }

    @Test
    fun metadataRefreshPreservesImagesAndCanClearObsoleteLinks() = runBlocking {
        val original = entity("one")
        db.downloadedChapterDao().insertChapter(original)
        downloads.updateChapterMetadata(
            comic,
            "extra",
            listOf(chapter("one").copy(nextChapter = "two"))
        )
        val refreshed = requireNotNull(downloads.getDownloadedChapter("one"))
        assertEquals("two", refreshed.nextChapter)
        assertEquals(original.imagePaths, refreshed.imagePaths.single())
        downloads.updateChapterMetadata(comic, "extra", listOf(chapter("one")))
        assertNull(downloads.getDownloadedChapter("one")?.nextChapter)
    }

    @Test
    fun emptyChapterAndFailedChapterRemainRetryable() = runBlocking {
        content = { Result.success(ChapterContent(emptyList(), null, null, it)) }
        downloads.enqueueDownload(task("one"))
        awaitStatus(DownloadTaskStatus.ERROR)
        assertTrue(downloads.getAllDownloadedComics().first().isEmpty())
        assertNull(db.downloadedChapterDao().getChapter("one"))
        content = {
            Result.success(
                ChapterContent(
                    listOf("https://example.com/page.jpg"),
                    null,
                    null,
                    it
                )
            )
        }
        downloads.resumeComic(comic)
        awaitStatus(DownloadTaskStatus.COMPLETED)
        assertEquals(1, downloads.getAllDownloadedComics().first().single().downloadedChapters)
    }

    @Test
    fun partialTaskKeepsOnlySuccessfulChaptersAfterCancellation() = runBlocking {
        content = { uuid ->
            if (uuid == "two") Result.failure(IOException("offline"))
            else Result.success(
                ChapterContent(
                    listOf("https://example.com/page.jpg"),
                    null,
                    "two",
                    uuid
                )
            )
        }
        downloads.enqueueDownload(task("one", "two"))
        awaitStatus(DownloadTaskStatus.ERROR)
        downloads.cancelComic(comic)
        val saved = downloads.getAllDownloadedComics().first().single()
        assertEquals(1, saved.downloadedChapters)
        assertEquals(1, saved.totalChapters)
        assertTrue(downloads.getDownloadProgress().value.isEmpty())
    }

    @Test
    fun cancellationBeforeContentReturnsCannotResurrectTheDownload() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        content = {
            started.complete(Unit)
            release.await()
            Result.success(ChapterContent(listOf("https://example.com/page.jpg"), null, null, it))
        }
        downloads.enqueueDownload(task("one"))
        withTimeout(5000.milliseconds) { started.await() }
        val cancel = launch { downloads.cancelComic(comic) }
        withTimeout(5000.milliseconds) { downloads.getDownloadProgress().first { it.isEmpty() } }
        release.complete(Unit)
        cancel.join()
        assertTrue(downloads.getAllDownloadedComics().first().isEmpty())
        assertEquals(0, requestedImages)
    }

    @Test
    fun localReaderRefreshesOldLastChapterWithoutRequestingImages() = runBlocking {
        val local = entity("one")
        db.downloadedChapterDao().insertChapter(local)
        val requested = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        metadata = { uuid, group ->
            assertEquals("extra", group)
            requested.complete(Unit)
            release.await()
            Result.success(chapter(uuid).copy(nextChapter = "new-online-chapter"))
        }
        val viewModel = reader()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.loadChapter(comic, "one", "default")
        }
        withTimeout(5000.milliseconds) { requested.await() }
        assertEquals(
            listOf(local.imagePaths),
            (viewModel.uiState.value as ReaderUiState.Success).content.imageUrls
        )
        release.complete(Unit)
        val state = withTimeout(5000.milliseconds) {
            viewModel.uiState.first {
                it is ReaderUiState.Success && it.content.nextUuid == "new-online-chapter"
            }
        } as ReaderUiState.Success
        assertEquals(listOf(local.imagePaths), state.content.imageUrls)
        assertEquals(0, requestedImages)
    }

    @Test
    fun localReaderKeepsOfflineContentWhenMetadataRefreshFails() = runBlocking {
        db.downloadedChapterDao().insertChapter(entity("one"))
        metadata = { _, _ -> Result.failure(IOException("offline")) }
        val viewModel = reader()
        InstrumentationRegistry.getInstrumentation()
            .runOnMainSync { viewModel.loadChapter(comic, "one") }
        val state =
            withTimeout(5000.milliseconds) { viewModel.uiState.first { it is ReaderUiState.Success } } as ReaderUiState.Success
        assertNull(state.content.nextUuid)
        assertEquals(0, requestedImages)
    }

    @Test
    fun managingOneGroupDoesNotDeleteDownloadsFromAnotherGroup() = runBlocking {
        db.downloadedChapterDao().insertChapter(entity("one").copy(groupPathWord = "default"))
        db.downloadedChapterDao().insertChapter(entity("two"))
        val viewModel = detail()
        withTimeout(5000.milliseconds) { viewModel.uiState.first { it.chapterDownloadStatuses.size == 2 && it.chapters.size == 1 } }
        withContext(Dispatchers.Main) {
            viewModel.enterManageMode()
            assertEquals(setOf("one", "two"), viewModel.getSelectedChapterUuids())
            viewModel.toggleChapterSelection("one")
            viewModel.confirmBatchAction()
        }
        withTimeout(5000.milliseconds) {
            db.downloadedChapterDao().getAllChapters()
                .first { rows -> rows.none { it.uuid == "one" } }
        }
        assertEquals(setOf("two"), downloads.getDownloadedChapterUuids(comic))
    }

    @Test
    fun reversingTheDirectoryAndFailedRefreshPreserveTheReadingStart() = runBlocking {
        db.downloadedChapterDao().insertChapter(entity("one").copy(index = 1, nextChapter = "two"))
        db.downloadedChapterDao().insertChapter(entity("two").copy(index = 2, prevChapter = "one"))
        val viewModel = detail()
        withTimeout(5_000.milliseconds) { viewModel.uiState.first { it.chapters.size == 2 && !it.isLoading } }
        withContext(Dispatchers.Main) {
            assertEquals("one", viewModel.getStartChapter()?.uuid)
            viewModel.toggleSortOrder()
            assertEquals("one", viewModel.getStartChapter()?.uuid)
            viewModel.refreshDetail()
        }
        withTimeout(5_000.milliseconds) { viewModel.uiState.first { !it.isLoading && it.toastMessage == "offline" } }
        assertEquals(2, viewModel.uiState.value.chapters.size)
        assertNotNull(viewModel.uiState.value.comicDetail)
    }

    private fun detail(): DetailViewModel {
        val user = Proxy.newProxyInstance(
            UserRepository::class.java.classLoader,
            arrayOf(UserRepository::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "isLoggedIn" -> flowOf(false)
                "isAuthCacheReady" -> flowOf(true)
                else -> error("Unexpected user call: ${method.name}")
            }
        } as UserRepository
        val blacklist = object : ThemeBlacklistRepository {
            override val blockedThemes = flowOf(emptyList<Theme>())
            override val isEnabled = flowOf(true)
            override suspend fun setEnabled(enabled: Boolean) = Result.success(Unit)
            override suspend fun setBlocked(theme: Theme, blocked: Boolean) = Result.success(Unit)
        }
        lateinit var viewModel: DetailViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = DetailViewModel(
                remote, user, downloads, blacklist,
                com.rchdliang.copymanga_neo.testing.InMemoryReadingProgressRepository()
            )
            store.put("detail", viewModel)
            viewModel.loadDetail(comic)
        }
        return viewModel
    }

    private suspend fun awaitStatus(status: DownloadTaskStatus) {
        withTimeout(10000.milliseconds) {
            downloads.getDownloadProgress().first { it[comic]?.status == status }
        }
    }

    private fun reader(): ReaderViewModel {
        val prefs = ReaderPreferencesStore(object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
                transform(emptyPreferences())
        })
        lateinit var reader: ReaderViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            reader = ReaderViewModel(
                remote, prefs, downloads,
                com.rchdliang.copymanga_neo.testing.InMemoryReadingProgressRepository(),
                object : com.rchdliang.copymanga_neo.domain.repository.ReaderToolsRepository {
                    override val settings = kotlinx.coroutines.flow.MutableStateFlow(
                        com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings()
                    )
                    override val bookmarks = kotlinx.coroutines.flow.MutableStateFlow(
                        emptyList<com.rchdliang.copymanga_neo.domain.model.ReaderBookmark>()
                    )

                    override suspend fun updateSettings(transform: (com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings) -> com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings): Result<Unit> {
                        settings.value = transform(settings.value)
                        return Result.success(Unit)
                    }

                    override suspend fun toggleBookmark(bookmark: com.rchdliang.copymanga_neo.domain.model.ReaderBookmark): Result<Unit> =
                        Result.success(Unit)
                }
            )
            store.put("reader", reader)
        }
        return reader
    }

    private fun comicInfo() =
        DownloadedComicInfo(comic, "test", null, "", "连载中", null, null, null, 10, 0, 0)

    private fun chapter(uuid: String) =
        ChapterInfo(1, uuid, 1, 1, 1, uuid, "id", comic, null, "extra", 0, null, null, null, null)

    private fun task(vararg uuids: String) = ComicDownloadTask(
        comic,
        "test",
        null,
        uuids.map { ChapterDownloadItem(chapter(it), ChapterDownloadStatus.WAITING) },
        "",
        "连载中",
        null,
        null
    )

    private fun entity(uuid: String): DownloadedChapterEntity {
        val file = File(context.filesDir, "downloads/$comic/$uuid/page.jpg")
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        return DownloadedChapterEntity(uuid, comic, uuid, 1, 1, file.path, "downloaded", 0, "extra")
    }
}
