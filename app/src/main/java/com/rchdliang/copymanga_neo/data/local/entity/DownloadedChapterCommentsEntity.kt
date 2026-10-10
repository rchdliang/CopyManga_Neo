package com.rchdliang.copymanga_neo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** 评论快照随所属章节删除，不把未完成的分页误当成完整下载。 */
@Entity(
    tableName = "downloaded_chapter_comments",
    foreignKeys = [ForeignKey(
        entity = DownloadedChapterEntity::class,
        parentColumns = ["uuid"], childColumns = ["chapterUuid"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class DownloadedChapterCommentsEntity(
    @PrimaryKey val chapterUuid: String,
    val commentsJson: String,
    val total: Int,
    val complete: Boolean,
    val closed: Boolean,
    val savedAt: Long
)
