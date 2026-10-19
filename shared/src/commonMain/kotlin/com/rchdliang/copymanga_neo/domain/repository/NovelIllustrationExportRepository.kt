package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.NovelReadingVolume
import com.rchdliang.copymanga_neo.domain.model.NovelSection
import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage

interface NovelIllustrationExportRepository {
    suspend fun prepareImage(
        volume: NovelReadingVolume,
        image: NovelSection
    ): Result<ReaderExportImage>

    suspend fun prepareVolume(volume: NovelReadingVolume): Result<ReaderExportImage>
    suspend fun saveToDocument(file: ReaderExportImage, destination: String): Result<Unit>
    suspend fun discard(file: ReaderExportImage)
}
