package com.rchdliang.copymanga_neo.data.download

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.data.local.db.DownloadQueueDao
import com.rchdliang.copymanga_neo.data.local.entity.DownloadQueueEntity
import com.rchdliang.copymanga_neo.domain.model.BookDownloadTask
import com.rchdliang.copymanga_neo.domain.model.ChapterDownloadItem
import com.rchdliang.copymanga_neo.domain.model.ChapterDownloadStatus
import com.rchdliang.copymanga_neo.domain.model.ChapterInfo
import com.rchdliang.copymanga_neo.domain.model.ComicDownloadTask
import com.rchdliang.copymanga_neo.domain.model.DownloadKind
import com.rchdliang.copymanga_neo.domain.model.NovelBook
import com.rchdliang.copymanga_neo.domain.model.NovelDownloadItem
import com.rchdliang.copymanga_neo.domain.model.NovelDownloadTask
import com.rchdliang.copymanga_neo.domain.model.NovelTag
import com.rchdliang.copymanga_neo.domain.model.NovelVolume
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import javax.inject.Inject
import javax.inject.Singleton

/** 使用固定 JSON 字段，避免发布版代码混淆改变持久化格式。 */
@Singleton
class DownloadQueueStore @Inject constructor(private val dao: DownloadQueueDao) {
    suspend fun load(): List<BookDownloadTask> = dao.getAll().mapNotNull { row ->
        try {
            DownloadQueueCodec.decode(row.taskJson)
        } catch (error: Exception) {
            android.util.Log.e("DownloadQueue", "下载队列条目无法读取，保留原记录", error)
            null
        }
    }

    suspend fun save(task: BookDownloadTask) {
        val createdAt = dao.get(task.taskKey)?.createdAt ?: System.currentTimeMillis()
        dao.save(DownloadQueueEntity(task.taskKey, DownloadQueueCodec.encode(task), createdAt))
    }

    suspend fun remove(pathWord: String) = dao.remove(pathWord)
    suspend fun clear() = dao.clear()
}

/** 固定字段编码，不把小说分卷伪装成漫画章节，也不依赖 Gson 反射字段名。 */
internal object DownloadQueueCodec {
    fun encode(task: BookDownloadTask): String = JsonObject().apply {
        addProperty("version", 2)
        addProperty("kind", task.kind.name)
        addProperty("path", task.pathWord)
        addProperty("name", task.name)
        addProperty("cover", task.cover)
        addProperty("task_id", task.taskId)
        if (task is NovelDownloadTask) {
            addProperty("mode", task.mode.name)
            addProperty("owner", task.owner)
            addProperty("uuid", task.book.uuid)
            addProperty("popular", task.book.popular)
            addProperty("status", task.book.status)
            addProperty("updated", task.book.updated)
            addProperty("brief", task.book.brief)
            fun tags(values: List<NovelTag>) = JsonArray().apply {
                values.forEach {
                    add(JsonObject().apply {
                        addProperty("name", it.name); addProperty(
                        "path",
                        it.path
                    )
                    })
                }
            }
            add("authors", tags(task.book.authors)); add("themes", tags(task.book.themes))
            add("volumes", JsonArray().apply {
                task.volumes.forEach { item ->
                    add(JsonObject().apply {
                        addProperty("id", item.volume.id); addProperty("name", item.volume.name)
                        addProperty("index", item.volume.index); addProperty(
                        "prev",
                        item.volume.previous
                    )
                        addProperty("next", item.volume.next)
                    })
                }
            })
            return@apply
        }
        require(task is ComicDownloadTask)
        addProperty("authors", task.authorNames)
        addProperty("status", task.statusDisplay)
        addProperty("updated", task.lastUpdate)
        addProperty("brief", task.brief)
        add("chapters", JsonArray().apply {
            task.chapters.forEach { item ->
                val chapter = item.chapter
                add(JsonObject().apply {
                    addProperty("index", chapter.index)
                    addProperty("uuid", chapter.uuid)
                    addProperty("count", chapter.count)
                    addProperty("ordered", chapter.ordered)
                    addProperty("size", chapter.size)
                    addProperty("name", chapter.name)
                    addProperty("comic_id", chapter.comicId)
                    addProperty("comic_path", chapter.comicPathWord)
                    addProperty("group_id", chapter.groupId)
                    addProperty("group_path", chapter.groupPathWord)
                    addProperty("type", chapter.type)
                    addProperty("news", chapter.news)
                    addProperty("created", chapter.datetimeCreated)
                    addProperty("prev", chapter.prevChapter)
                    addProperty("next", chapter.nextChapter)
                })
            }
        })
    }.toString()

    fun decode(json: String): BookDownloadTask {
        val task = JsonParser.parseString(json).asJsonObject
        require(task["version"].asInt == 2) { "不支持的下载队列版本" }
        if (DownloadKind.valueOf(task["kind"].asString) == DownloadKind.NOVEL) {
            fun tags(key: String) = task.getAsJsonArray(key).map { value ->
                value.asJsonObject.let {
                    NovelTag(it["name"].asString, it["path"].asString)
                }
            }
            return NovelDownloadTask(
                mode = RequestMode.valueOf(task["mode"].asString), owner = task["owner"].asString,
                book = NovelBook(
                    task["path"].asString,
                    task["name"].asString,
                    task.stringOrNull("cover").orEmpty(),
                    tags("authors"),
                    tags("themes"),
                    task["brief"].asString,
                    task["status"].asString,
                    task["updated"].asString,
                    task["uuid"].asString,
                    task.stringOrNull("popular")?.toIntOrNull()
                ),
                volumes = task.getAsJsonArray("volumes").map { value ->
                    value.asJsonObject.let {
                        NovelDownloadItem(
                            NovelVolume(
                                it["id"].asString, it["name"].asString, it["index"].asInt,
                                it.stringOrNull("prev"), it.stringOrNull("next")
                            )
                        )
                    }
                }, taskId = task["task_id"].asString
            )
        }
        return ComicDownloadTask(
            pathWord = task["path"].asString, name = task["name"].asString,
            cover = task.stringOrNull("cover"), authorNames = task["authors"].asString,
            statusDisplay = task["status"].asString, lastUpdate = task.stringOrNull("updated"),
            brief = task.stringOrNull("brief"), taskId = task["task_id"].asString,
            chapters = task.getAsJsonArray("chapters").map { value ->
                val chapter = value.asJsonObject
                ChapterDownloadItem(
                    ChapterInfo(
                        index = chapter["index"].asInt,
                        uuid = chapter["uuid"].asString,
                        count = chapter["count"].asInt,
                        ordered = chapter["ordered"].asInt,
                        size = chapter["size"]?.takeUnless { it.isJsonNull }?.asInt,
                        name = chapter["name"].asString,
                        comicId = chapter["comic_id"].asString,
                        comicPathWord = chapter["comic_path"].asString,
                        groupId = chapter.stringOrNull("group_id"),
                        groupPathWord = chapter.stringOrNull("group_path"),
                        type = chapter["type"].asInt,
                        news = chapter.stringOrNull("news"),
                        datetimeCreated = chapter.stringOrNull("created"),
                        prevChapter = chapter.stringOrNull("prev"),
                        nextChapter = chapter.stringOrNull("next")
                    ), status = ChapterDownloadStatus.WAITING
                )
            }
        )
    }

    private fun JsonObject.stringOrNull(key: String) =
        get(key)?.takeUnless { it.isJsonNull }?.asString
}
