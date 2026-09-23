package com.rchdliang.copymanga_neo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 队列只保存下载意图，已完成章节以数据库记录和实际文件为准。 */
@Entity(tableName = "download_queue")
data class DownloadQueueEntity(
    @PrimaryKey val comicPathWord: String,
    val taskJson: String,
    val createdAt: Long
)
