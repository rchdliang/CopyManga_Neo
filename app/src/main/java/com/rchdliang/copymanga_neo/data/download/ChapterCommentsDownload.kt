package com.rchdliang.copymanga_neo.data.download

import com.rchdliang.copymanga_neo.domain.model.CommentPage
import com.rchdliang.copymanga_neo.domain.model.DownloadedChapterComments
import com.rchdliang.copymanga_neo.domain.model.MangaComment
import com.rchdliang.copymanga_neo.domain.repository.CommentClosedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

/** 顺序下载评论分页，每页保存快照；取消和错误不会把部分内容标记为完整。 */
internal suspend fun downloadChapterCommentPages(
    readPage: suspend (Int) -> Result<CommentPage>,
    saveSnapshot: suspend (DownloadedChapterComments) -> Unit,
    pageTimeoutMillis: Long = 15_000L
): Result<DownloadedChapterComments> {
    return try {
        val items = linkedMapOf<String, MangaComment>()
        var offset = 0
        var completed: DownloadedChapterComments? = null
        while (completed == null) {
            val response = withTimeoutOrNull(pageTimeoutMillis.milliseconds) { readPage(offset) }
                ?: Result.failure(IOException("评论下载超时，可稍后重试"))
            val page = response.fold(
                onSuccess = { it },
                onFailure = { error ->
                    if (error is CommentClosedException) {
                        val closed = DownloadedChapterComments(
                            emptyList(),
                            0,
                            true,
                            true,
                            System.currentTimeMillis()
                        )
                        saveSnapshot(closed)
                        return Result.success(closed)
                    }
                    throw error
                }
            )
            val next = page.nextOffset
            check(next == null || next > offset) { "评论分页未推进，请重试" }
            page.comments.forEach { items[it.id] = it }
            val snapshot = DownloadedChapterComments(
                items.values.toList(),
                page.total,
                next == null,
                savedAt = System.currentTimeMillis()
            )
            saveSnapshot(snapshot)
            if (next == null) completed = snapshot else offset = next
        }
        Result.success(completed)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}
