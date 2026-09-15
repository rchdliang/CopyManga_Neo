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
    val downloadAt: Long,
    // 旧版本没有保存分组及上下章关系；未知时保持 null，不能由下载子集推算。
    val groupPathWord: String? = null,
    val prevChapter: String? = null,
    val nextChapter: String? = null
)
