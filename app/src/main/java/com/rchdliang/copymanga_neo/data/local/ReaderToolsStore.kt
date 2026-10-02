package com.rchdliang.copymanga_neo.data.local

import android.annotation.SuppressLint
import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.domain.model.ReaderBookmark
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import com.rchdliang.copymanga_neo.domain.repository.ReaderToolsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 固定 JSON 字段避免混淆漂移；设置修改和书签写入串行提交。 */
@Singleton
class ReaderToolsStore @Inject constructor(@ApplicationContext context: Context) :
    ReaderToolsRepository {
    private val prefs = context.getSharedPreferences("reader_tools", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutableSettings = MutableStateFlow(
        ReaderToolsSettings(
            inlineComments = prefs.getBoolean("inline_comments", true),
            continuousChapters = prefs.getBoolean("continuous_chapters", true),
            volumeKeys = prefs.getBoolean("volume_keys", false),
            autoIntervalSeconds = prefs.getInt("auto_interval", 8),
            imageRetries = prefs.getInt("image_retries", 2),
            imageTimeoutSeconds = prefs.getInt("image_timeout", 15),
            preloadPages = prefs.getInt("preload_pages", 2)
        ).normalized()
    )
    override val settings = mutableSettings.asStateFlow()
    private val mutableBookmarks = MutableStateFlow(readBookmarks())
    override val bookmarks = mutableBookmarks.asStateFlow()

    // KTX edit 不返回 commit 的成败，这里必须在写入成功后发布状态。
    @SuppressLint("UseKtx")
    override suspend fun updateSettings(transform: (ReaderToolsSettings) -> ReaderToolsSettings): Result<Unit> =
        result {
            mutex.withLock {
                val next = transform(mutableSettings.value).normalized()
                if (!prefs.edit().putBoolean("inline_comments", next.inlineComments)
                        .putBoolean("continuous_chapters", next.continuousChapters)
                        .putBoolean("volume_keys", next.volumeKeys)
                        .putInt("auto_interval", next.autoIntervalSeconds)
                        .putInt("image_retries", next.imageRetries)
                        .putInt("image_timeout", next.imageTimeoutSeconds)
                        .putInt("preload_pages", next.preloadPages).commit()
                ) throw IOException("阅读设置保存失败")
                mutableSettings.value = next
            }
        }

    @SuppressLint("UseKtx")
    override suspend fun toggleBookmark(bookmark: ReaderBookmark): Result<Unit> = result {
        mutex.withLock {
            val old = mutableBookmarks.value
            val match: (ReaderBookmark) -> Boolean = {
                it.comicPathWord == bookmark.comicPathWord && it.chapterUuid == bookmark.chapterUuid && it.page == bookmark.page
            }
            val next =
                if (old.any(match)) old.filterNot(match) else (old + bookmark).sortedByDescending { it.createdAt }
            val json = JsonArray().apply {
                next.forEach { mark ->
                    add(JsonObject().apply {
                        addProperty("comic", mark.comicPathWord); addProperty(
                        "chapter",
                        mark.chapterUuid
                    )
                        addProperty("name", mark.chapterName); addProperty("group", mark.group)
                        addProperty("page", mark.page); addProperty("at", mark.createdAt)
                    })
                }
            }
            if (!prefs.edit().putString("bookmarks", json.toString())
                    .commit()
            ) throw IOException("书签保存失败")
            mutableBookmarks.value = next
        }
    }

    private fun readBookmarks(): List<ReaderBookmark> = try {
        JsonParser.parseString(
            prefs.getString(
                "bookmarks",
                "[]"
            )
        ).asJsonArray.mapNotNull { element ->
            runCatching {
                val item = element.asJsonObject
                ReaderBookmark(
                    item["comic"].asString, item["chapter"].asString, item["name"].asString,
                    item["group"].asString, item["page"].asInt.coerceAtLeast(0), item["at"].asLong
                )
            }.getOrNull()
        }
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun result(block: suspend () -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                block(); Result.success(Unit)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
}
