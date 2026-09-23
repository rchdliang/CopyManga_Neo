package com.rchdliang.copymanga_neo.data.download

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.data.local.db.DownloadQueueDao
import com.rchdliang.copymanga_neo.data.local.entity.DownloadQueueEntity
import com.rchdliang.copymanga_neo.domain.model.ChapterInfo
import javax.inject.Inject
import javax.inject.Singleton

/** 使用固定 JSON 字段，避免发布版代码混淆改变持久化格式。 */
@Singleton
class DownloadQueueStore @Inject constructor(private val dao: DownloadQueueDao) {
    suspend fun load(): List<ComicDownloadTask> = dao.getAll().map { decode(it.taskJson) }

    suspend fun save(task: ComicDownloadTask) {
        val createdAt = dao.get(task.pathWord)?.createdAt ?: System.currentTimeMillis()
        dao.save(DownloadQueueEntity(task.pathWord, encode(task), createdAt))
    }

    suspend fun remove(pathWord: String) = dao.remove(pathWord)
    suspend fun clear() = dao.clear()

    private fun encode(task: ComicDownloadTask): String = JsonObject().apply {
        addProperty("version", 1)
        addProperty("path", task.pathWord)
        addProperty("name", task.name)
        addProperty("cover", task.cover)
        addProperty("authors", task.authorNames)
        addProperty("status", task.statusDisplay)
        addProperty("updated", task.lastUpdate)
        addProperty("brief", task.brief)
        addProperty("task_id", task.taskId)
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

    private fun decode(json: String): ComicDownloadTask {
        val task = JsonParser.parseString(json).asJsonObject
        require(task["version"].asInt == 1) { "不支持的下载队列版本" }
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
