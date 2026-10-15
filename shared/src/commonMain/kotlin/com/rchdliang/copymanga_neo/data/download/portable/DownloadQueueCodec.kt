package com.rchdliang.copymanga_neo.data.download.portable

import com.rchdliang.copymanga_neo.domain.model.*
import kotlinx.serialization.json.*

internal object DownloadQueueCodec {
    fun encode(task: BookDownloadTask): String = buildJsonObject {
        put("version", 2)
        put("kind", task.kind.name)
        put("path", task.pathWord)
        put("name", task.name)
        put("cover", task.cover)
        put("task_id", task.taskId)
        if (task is NovelDownloadTask) {
            put("mode", task.mode.name)
            put("owner", task.owner)
            put("uuid", task.book.uuid)
            put("popular", task.book.popular)
            put("status", task.book.status)
            put("updated", task.book.updated)
            put("brief", task.book.brief)
            fun tags(values: List<NovelTag>) = buildJsonArray {
                values.forEach {
                    add(buildJsonObject {
                        put("name", it.name); put(
                        "path",
                        it.path
                    )
                    })
                }
            }
            put("authors", tags(task.book.authors)); put("themes", tags(task.book.themes))
            put("volumes", buildJsonArray {
                task.volumes.forEach { item ->
                    add(buildJsonObject {
                        put("id", item.volume.id); put("name", item.volume.name)
                        put("index", item.volume.index); put(
                        "prev",
                        item.volume.previous
                    )
                        put("next", item.volume.next)
                    })
                }
            })
            return@buildJsonObject
        }
        require(task is ComicDownloadTask)
        put("authors", task.authorNames)
        put("status", task.statusDisplay)
        put("updated", task.lastUpdate)
        put("brief", task.brief)
        put("chapters", buildJsonArray {
            task.chapters.forEach { item ->
                val chapter = item.chapter
                add(buildJsonObject {
                    put("index", chapter.index)
                    put("uuid", chapter.uuid)
                    put("count", chapter.count)
                    put("ordered", chapter.ordered)
                    put("size", chapter.size)
                    put("name", chapter.name)
                    put("comic_id", chapter.comicId)
                    put("comic_path", chapter.comicPathWord)
                    put("group_id", chapter.groupId)
                    put("group_path", chapter.groupPathWord)
                    put("type", chapter.type)
                    put("news", chapter.news)
                    put("created", chapter.datetimeCreated)
                    put("prev", chapter.prevChapter)
                    put("next", chapter.nextChapter)
                })
            }
        })
    }.toString()

    fun decode(json: String): BookDownloadTask {
        val task = Json.parseToJsonElement(json).jsonObject
        require(task.getValue("version").jsonPrimitive.int == 2) { "不支持的下载队列版本" }
        if (DownloadKind.valueOf(task.getValue("kind").jsonPrimitive.content) == DownloadKind.NOVEL) {
            fun tags(key: String) = task.getValue(key).jsonArray.map { value ->
                value.jsonObject.let {
                    NovelTag(it.getValue("name").jsonPrimitive.content, it.getValue("path").jsonPrimitive.content)
                }
            }
            return NovelDownloadTask(
                mode = RequestMode.valueOf(task.getValue("mode").jsonPrimitive.content), owner = task.getValue("owner").jsonPrimitive.content,
                book = NovelBook(
                    task.getValue("path").jsonPrimitive.content,
                    task.getValue("name").jsonPrimitive.content,
                    task.stringOrNull("cover").orEmpty(),
                    tags("authors"),
                    tags("themes"),
                    task.getValue("brief").jsonPrimitive.content,
                    task.getValue("status").jsonPrimitive.content,
                    task.getValue("updated").jsonPrimitive.content,
                    task.getValue("uuid").jsonPrimitive.content,
                    task.stringOrNull("popular")?.toIntOrNull()
                ),
                volumes = task.getValue("volumes").jsonArray.map { value ->
                    value.jsonObject.let {
                        NovelDownloadItem(
                            NovelVolume(
                                it.getValue("id").jsonPrimitive.content, it.getValue("name").jsonPrimitive.content, it.getValue("index").jsonPrimitive.int,
                                it.stringOrNull("prev"), it.stringOrNull("next")
                            )
                        )
                    }
                }, taskId = task.getValue("task_id").jsonPrimitive.content
            )
        }
        return ComicDownloadTask(
            pathWord = task.getValue("path").jsonPrimitive.content, name = task.getValue("name").jsonPrimitive.content,
            cover = task.stringOrNull("cover"), authorNames = task.getValue("authors").jsonPrimitive.content,
            statusDisplay = task.getValue("status").jsonPrimitive.content, lastUpdate = task.stringOrNull("updated"),
            brief = task.stringOrNull("brief"), taskId = task.getValue("task_id").jsonPrimitive.content,
            chapters = task.getValue("chapters").jsonArray.map { value ->
                val chapter = value.jsonObject
                ChapterDownloadItem(
                    ChapterInfo(
                        index = chapter.getValue("index").jsonPrimitive.int,
                        uuid = chapter.getValue("uuid").jsonPrimitive.content,
                        count = chapter.getValue("count").jsonPrimitive.int,
                        ordered = chapter.getValue("ordered").jsonPrimitive.int,
                        size = chapter["size"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.int,
                        name = chapter.getValue("name").jsonPrimitive.content,
                        comicId = chapter.getValue("comic_id").jsonPrimitive.content,
                        comicPathWord = chapter.getValue("comic_path").jsonPrimitive.content,
                        groupId = chapter.stringOrNull("group_id"),
                        groupPathWord = chapter.stringOrNull("group_path"),
                        type = chapter.getValue("type").jsonPrimitive.int,
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
        get(key)?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
}
