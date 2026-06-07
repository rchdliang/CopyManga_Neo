package com.rchdliang.copymanga_neo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "downloaded_chapters",
    foreignKeys = [
        ForeignKey(
            entity = DownloadedComicEntity::class,
            parentColumns = ["pathWord"],
            childColumns = ["comicPathWord"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("comicPathWord")]
)
data class DownloadedChapterEntity(
    @PrimaryKey val uuid: String,
    val comicPathWord: String,
    val name: String,
    val index: Int,
    val imageCount: Int,
    val imagePaths: String,
    val status: String,
    val downloadAt: Long
)
