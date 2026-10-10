package com.rchdliang.copymanga_neo.data.local.db

import androidx.room.Dao
import androidx.room.Query
import com.rchdliang.copymanga_neo.data.local.entity.DownloadedChapterCommentsEntity

@Dao
interface DownloadedChapterCommentsDao {
    @Query("SELECT * FROM downloaded_chapter_comments WHERE chapterUuid = :uuid")
    suspend fun get(uuid: String): DownloadedChapterCommentsEntity?

    /** 与章节存在性检查在同一条语句中执行，删除后的迟到请求不能恢复快照。 */
    @Query(
        """
        INSERT OR REPLACE INTO downloaded_chapter_comments
        (chapterUuid, commentsJson, total, complete, closed, savedAt)
        SELECT :uuid, :json, :total, :complete, :closed, :savedAt
        WHERE EXISTS (SELECT 1 FROM downloaded_chapters WHERE uuid = :uuid AND status = 'downloaded')
        AND (:complete = 1 OR NOT EXISTS (
            SELECT 1 FROM downloaded_chapter_comments WHERE chapterUuid = :uuid AND complete = 1
        ))
    """
    )
    suspend fun save(
        uuid: String,
        json: String,
        total: Int,
        complete: Boolean,
        closed: Boolean,
        savedAt: Long
    )
}
