package com.rchdliang.copymanga_neo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "comic_theme_cache")
data class ComicThemeCacheEntity(
    @PrimaryKey val pathWord: String,
    val themesJson: String,
    val refreshAfterMillis: Long
)
