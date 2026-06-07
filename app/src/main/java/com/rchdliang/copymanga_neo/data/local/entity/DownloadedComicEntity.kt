package com.rchdliang.copymanga_neo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_comics")
data class DownloadedComicEntity(
    @PrimaryKey val pathWord: String,
    val name: String,
    val cover: String?,
    val authorNames: String,
    val statusDisplay: String,
    val lastUpdate: String?,
    val lastChapterName: String?,
    val brief: String?,
    val totalChapters: Int,
    val downloadedChapters: Int,
    val downloadAt: Long
)
