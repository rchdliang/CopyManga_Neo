package com.rchdliang.copymanga_neo.data.novel

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.BuildConfig
import com.rchdliang.copymanga_neo.data.download.DownloadNetworkPolicy
import com.rchdliang.copymanga_neo.data.local.AuthDataStore
import com.rchdliang.copymanga_neo.data.novel.cache.NovelCacheStore
import com.rchdliang.copymanga_neo.data.novel.cache.StoredNovelVolume
import com.rchdliang.copymanga_neo.data.remote.CopyMangaNetworkConfig
import com.rchdliang.copymanga_neo.domain.model.LocalReadingSession
import com.rchdliang.copymanga_neo.domain.model.NovelBook
import com.rchdliang.copymanga_neo.domain.model.NovelCacheProgress
import com.rchdliang.copymanga_neo.domain.model.NovelCacheStage
import com.rchdliang.copymanga_neo.domain.model.NovelCacheUsage
import com.rchdliang.copymanga_neo.domain.model.NovelCachedVolume
import com.rchdliang.copymanga_neo.domain.model.NovelPage
import com.rchdliang.copymanga_neo.domain.model.NovelReadingVolume
import com.rchdliang.copymanga_neo.domain.model.NovelTag
import com.rchdliang.copymanga_neo.domain.model.NovelVolume
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import com.rchdliang.copymanga_neo.domain.model.withFallback
import com.rchdliang.copymanga_neo.domain.reading.NovelImagePositionDebugStore
import com.rchdliang.copymanga_neo.domain.reading.NovelRequestSpec
import com.rchdliang.copymanga_neo.domain.reading.NovelTextDocument
import com.rchdliang.copymanga_neo.domain.repository.NovelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException

/** 官方小说数据及校验缓存；账号、网络约束和错误契约与其他仓库一致。 */
@Singleton
class OfficialNovelRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val client: OkHttpClient,
    private val auth: AuthDataStore,
    private val downloadNetwork: DownloadNetworkPolicy,
    private val themeCatalog: NovelThemeCatalogStore,
    private val imagePositionDebug: NovelImagePositionDebugStore
) : NovelRepository {
    private val cache = NovelCacheStore(File(context.filesDir, "official_novel_cache"))
    private val mutex = Mutex()
    private val mutableCached = MutableStateFlow<List<NovelCachedVolume>>(emptyList())
    override val cachedVolumes = mutableCached.asStateFlow()
    private val bookHints = linkedMapOf<String, NovelBook>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val initialization = scope.async {
        refreshCachedVolumes().onFailure {
            android.util.Log.w(
                "NovelCache",
                "小说缓存索引读取失败",
                it
            )
        }
    }

    override suspend fun discover(
        mode: RequestMode,
        owner: String,
        offset: Int,
        order: String,
        theme: String,
        author: String
    ): Result<NovelPage> = result {
        page(
            api(
                mode,
                owner,
                listOf("books"),
                NovelRequestSpec.browse(
                    mode == RequestMode.OFFICIAL_APP,
                    offset,
                    order,
                    theme,
                    author
                )
            )
        )
            .also { rememberBooks(mode, owner, it.books) }
    }

    override suspend fun search(
        mode: RequestMode,
        owner: String,
        offset: Int,
        query: String,
        type: String
    ): Result<NovelPage> = result {
        page(
            api(
                mode,
                owner,
                listOf("search", "books"),
                NovelRequestSpec.search(mode == RequestMode.OFFICIAL_APP, offset, query, type)
            )
        )
            .also { rememberBooks(mode, owner, it.books) }
    }

    override suspend fun tags(mode: RequestMode, owner: String): Result<List<NovelTag>> = result {
        val readingSession = session(mode, owner)
        val tags = mutableListOf<NovelTag>()
        var offset = 0
        do {
            val response = api(
                mode, owner, listOf("theme", "book", "count"),
                NovelRequestSpec.themeCounts(mode == RequestMode.OFFICIAL_APP, offset)
            )
            require(response["offset"]?.asInt == offset) { "小说题材分页位置无效" }
            val total = requireNotNull(response["total"]).asInt
            require(total >= 0)
            val page = NovelJson.themeCounts(response)
            tags += page
            offset += page.size
            if (offset >= total) break
            require(page.isNotEmpty()) { "小说题材目录未返回完整数据" }
            ensureSession(readingSession)
        } while (true)
        val complete = tags.distinctBy { it.path }
        ensureSession(readingSession)
        themeCatalog.save(mode, complete)
        complete
    }

    override fun cachedTags(mode: RequestMode): List<NovelTag> = themeCatalog.get(mode)

    override suspend fun detail(mode: RequestMode, owner: String, path: String): Result<NovelBook> =
        result {
            NovelJson.book(api(mode, owner, listOf("book", path)).getAsJsonObject("book"))
                .also { rememberBooks(mode, owner, listOf(it)) }
        }

    override suspend fun volumes(
        mode: RequestMode,
        owner: String,
        path: String
    ): Result<List<NovelVolume>> = result {
        api(mode, owner, listOf("book", path, "volumes")).getAsJsonArray("list")
            .map { NovelJson.volume(it.asJsonObject) }.sortedBy { it.index }
    }

    override suspend fun readVolume(
        mode: RequestMode,
        owner: String,
        path: String,
        id: String,
        allImages: Boolean,
        refresh: Boolean,
        onProgress: (NovelCacheProgress) -> Unit,
        forDownload: Boolean
    ): Result<NovelReadingVolume> = result {
        if (!forDownload && !allImages && !refresh) {
            val readingSession = session(mode, owner)
            // 已发布的正文可以立即阅读，不等待后台补齐整卷插图；此分支不修改缓存。
            val snapshot =
                runCatching { cache.read(mode, owner, path, id, repair = false) }.getOrNull()
            if (snapshot != null) {
                currentCoroutineContext().ensureActive()
                ensureSession(readingSession)
                return@result snapshot.reading
            }
        }
        mutex.withLock {
            val session = session(mode, owner)
            if (forDownload) downloadNetwork.ready.await()
            var cached = runCatching { cache.read(mode, owner, path, id) }.getOrNull()
            if (refresh || cached == null) {
                onProgress(NovelCacheProgress(NovelCacheStage.TEXT))
                val response = api(
                    mode,
                    owner,
                    listOf("book", path, "volume", id),
                    fallback = false,
                    forDownload = forDownload
                )
                if (BuildConfig.DEBUG) {
                    try {
                        NovelImagePositionDebugJson.sample(mode, path, id, response)
                            ?.let(imagePositionDebug::offer)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        android.util.Log.w("NovelDebug", "插图位置样本提取失败", error)
                    }
                }
                if (response["is_lock"]?.asBoolean == true) throw IOException("本卷受限，请在当前模式登录或检查官方阅读权限")
                val volume =
                    response.getAsJsonObject("volume") ?: throw IOException("官方未返回可读内容")
                val address = NovelJson.text(volume, "txt_addr")
                require(address.isNotBlank()) { "官方未返回正文地址，请检查阅读权限" }
                val book = NovelJson.book(response.getAsJsonObject("book"))
                    .withFallback(bookHint(mode, owner, path)).withFallback(cached?.reading?.book)
                response.add("book", bookJson(book))
                val bytes = resource(address, NovelCacheStore.TEXT_LIMIT, forDownload) {
                    onProgress(NovelCacheProgress(NovelCacheStage.TEXT, bytes = it))
                }
                cached = cache.publish(
                    mode, owner, path, id, response, bytes, cached,
                    downloadRequested = forDownload
                ) {
                    ensureSession(session)
                }
                publishCached(cached)
            }
            var state = requireNotNull(cached)
            if (forDownload) {
                currentCoroutineContext().ensureActive()
                ensureSession(session)
                state = cache.markDownloadRequested(state)
                publishCached(state)
            }
            val urls =
                state.reading.sections.filter { it.type == 2 }.map { requireNotNull(it.image) }
                    .distinct()
            var bytes = cache.describe(state).bytes
            onProgress(
                NovelCacheProgress(
                    NovelCacheStage.IMAGES,
                    state.reading.cachedImages.size,
                    urls.size,
                    bytes
                )
            )
            if (allImages) for (url in urls) {
                currentCoroutineContext().ensureActive()
                if (url in state.reading.cachedImages) continue
                val before = bytes
                val image = resource(url, NovelCacheStore.IMAGE_LIMIT, forDownload) { received ->
                    onProgress(
                        NovelCacheProgress(
                            NovelCacheStage.IMAGES,
                            state.reading.cachedImages.size,
                            urls.size,
                            before + received
                        )
                    )
                }
                val bounds =
                    android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "官方插图资源无法解码" }
                ensureSession(session)
                currentCoroutineContext().ensureActive()
                state = cache.writeImage(state, url, image)
                bytes = cache.describe(state).bytes
                publishCached(state)
                onProgress(
                    NovelCacheProgress(
                        NovelCacheStage.IMAGES,
                        state.reading.cachedImages.size,
                        urls.size,
                        bytes
                    )
                )
            }
            currentCoroutineContext().ensureActive()
            ensureSession(session)
            state = runCatching { cache.touch(state) }.getOrElse {
                android.util.Log.w("NovelCache", "小说缓存访问时间保存失败", it)
                state
            }
            publishCached(state)
            state.reading
        }
    }

    override suspend fun removeVolume(
        mode: RequestMode,
        owner: String,
        path: String,
        id: String
    ): Result<Unit> = result {
        mutex.withLock {
            session(mode, owner)
            cache.removeVolume(mode, owner, path, id)
            mutableCached.value =
                mutableCached.value.filterNot { it.mode == mode && it.owner == owner && it.book.path == path && it.volume.id == id }
        }
    }

    override suspend fun removeBook(mode: RequestMode, owner: String, path: String): Result<Unit> =
        result {
            mutex.withLock {
                session(mode, owner)
                try {
                    cache.removeBook(mode, owner, path)
                } finally {
                    mutableCached.value = cache.scan()
                }
            }
        }

    override suspend fun refreshCachedVolumes(): Result<Unit> = result {
        mutex.withLock {
            cache.pruneMetadata()
            mutableCached.value = cache.scan()
        }
    }

    override suspend fun cacheUsage(): Result<NovelCacheUsage> = result {
        initialization.join()
        cache.pruneMetadata()
        // 只读统计不等待正在传输的整卷；索引在每次成功发布资源后更新。
        NovelCacheUsage(cache.usageBytes(), mutableCached.value.size)
    }

    override suspend fun clearCache(): Result<Unit> = result {
        mutex.withLock {
            try {
                cache.clear()
            } finally {
                mutableCached.value = cache.scan()
            }
        }
    }

    private fun publishCached(cached: StoredNovelVolume) {
        val item = cache.describe(cached)
        mutableCached.value = (mutableCached.value.filterNot {
            it.mode == item.mode && it.owner == item.owner && it.book.path == item.book.path && it.volume.id == item.volume.id
        } + item).sortedByDescending { it.lastAccessedAt }
    }

    private fun page(json: JsonObject) = NovelPage(
        json.getAsJsonArray("list").map { NovelJson.book(it.asJsonObject) },
        json["total"].asInt, json["offset"].asInt, json["limit"].asInt
    )

    @Synchronized
    private fun rememberBooks(mode: RequestMode, owner: String, books: List<NovelBook>) {
        books.forEach { book ->
            val key = bookKey(mode, owner, book.path)
            val value = book.withFallback(bookHints.remove(key))
            bookHints[key] = value
        }
        while (bookHints.size > 256) bookHints.remove(bookHints.keys.first())
    }

    @Synchronized
    private fun bookHint(mode: RequestMode, owner: String, path: String) =
        bookHints[bookKey(mode, owner, path)]

    private fun bookKey(mode: RequestMode, owner: String, path: String) =
        listOf(mode.name, owner, path).joinToString("\u0000")

    private fun bookJson(book: NovelBook) = JsonObject().apply {
        addProperty("path_word", book.path); addProperty("name", book.name); addProperty(
        "cover",
        book.cover
    )
        fun tags(values: List<NovelTag>) = JsonArray().apply {
            values.forEach { tag ->
                add(JsonObject().apply {
                    addProperty(
                        "name",
                        tag.name
                    ); addProperty("path_word", tag.path)
                })
            }
        }
        add("author", tags(book.authors)); add("theme", tags(book.themes)); addProperty(
        "brief",
        book.brief
    )
        add(
            "status",
            JsonObject().apply {
                addProperty(
                    "display",
                    book.status
                )
            }); addProperty("datetime_updated", book.updated)
        addProperty("uuid", book.uuid); book.popular?.let { addProperty("popular", it) }
    }

    private suspend fun api(
        mode: RequestMode,
        owner: String,
        segments: List<String>,
        query: Map<String, String> = emptyMap(),
        fallback: Boolean = true,
        forDownload: Boolean = false
    ): JsonObject {
        val session = session(mode, owner)
        val url =
            CopyMangaNetworkConfig.API_BASE_URL.toHttpUrl().newBuilder().addPathSegments("api/v3")
                .apply {
                    segments.forEach { require(it.isNotBlank()); addPathSegment(it) }
                    query.forEach { (key, value) -> addQueryParameter(key, value) }
                    if (mode == RequestMode.OFFICIAL_APP) setQueryParameter("platform", "3")
                }.build()
        val key = NovelTextDocument.sha256(owner + "\u0000" + mode.name + "\u0000" + url)
        val cacheGeneration = cache.generation
        val request = Request.Builder().url(url).tag(RequestMode::class.java, mode).apply {
            CopyMangaNetworkConfig.apiHeaders().forEach { (name, value) -> header(name, value) }
        }.build()
        try {
            val body = fetch(request, NovelCacheStore.JSON_LIMIT, forDownload)
            val envelope = parse(body)
            if (envelope["code"]?.asInt != 200) throw OfficialHttpException(
                NovelJson.text(
                    envelope,
                    "message",
                    "官方接口请求失败"
                )
            )
            val result =
                envelope.getAsJsonObject("results") ?: throw IOException("官方接口返回空结果")
            ensureSession(session)
            if (fallback) runCatching { cache.writeMetadata(key, body, cacheGeneration) }
                .onFailure { android.util.Log.w("NovelCache", "小说元数据缓存写入失败", it) }
            return result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!fallback || e !is IOException || e is OfficialHttpException) throw e
            ensureSession(session)
            return runCatching {
                cache.readMetadata(key)?.let { parse(it).getAsJsonObject("results") }
            }.getOrNull() ?: throw e
        }
    }

    private suspend fun session(mode: RequestMode, owner: String): LocalReadingSession {
        auth.awaitReady()
        val session = auth.getReadingSessionSync() ?: throw IOException("账号状态尚未就绪")
        if (session.mode != mode || session.owner != owner) throw OfficialHttpException("阅读会话已改变，请重新打开")
        return session
    }

    private fun ensureSession(expected: LocalReadingSession) {
        if (auth.getReadingSessionSync() != expected) throw OfficialHttpException("阅读会话已改变，请重新打开")
    }

    private suspend fun resource(
        address: String,
        limit: Int,
        forDownload: Boolean,
        onBytes: (Long) -> Unit
    ): ByteArray {
        val url = address.toHttpUrl()
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty()) { "无效的官方资源地址" }
        require(!url.encodedPath.startsWith("/api/") && url.host != CopyMangaNetworkConfig.API_HOST && url.host != CopyMangaNetworkConfig.WWW_HOST) { "资源地址不能是会员接口" }
        return fetch(Request.Builder().url(url).build(), limit, forDownload, onBytes)
    }

    private suspend fun fetch(
        request: Request,
        limit: Int,
        forDownload: Boolean,
        onBytes: (Long) -> Unit = {}
    ): ByteArray = suspendCancellableCoroutine { continuation ->
        val selected = try {
            if (forDownload) downloadNetwork.client(client) else client
        } catch (e: Exception) {
            continuation.resumeWithException(e); return@suspendCancellableCoroutine
        }
        val call = selected.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val bytes = response.use {
                        if (!it.isSuccessful) throw OfficialHttpException("官方请求失败：HTTP ${it.code}")
                        val body = it.body
                        if (body.contentLength() > limit) throw IOException("官方资源过大")
                        body.byteStream().use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                if (continuation.isCancelled) throw CancellationException("小说请求已取消")
                                if (forDownload && !downloadNetwork.state.value.allowed) throw IOException(
                                    "当前网络不允许下载"
                                )
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (output.size() > limit - count) throw IOException("官方资源过大")
                                output.write(buffer, 0, count)
                                onBytes(output.size().toLong())
                            }
                            output.toByteArray()
                        }
                    }
                    continuation.resume(bytes) { _, _, _ -> }
                } catch (e: Exception) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }
            }
        })
    }

    private fun parse(bytes: ByteArray) =
        JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject

    private class OfficialHttpException(message: String) : IOException(message)

    private suspend fun <T> result(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
