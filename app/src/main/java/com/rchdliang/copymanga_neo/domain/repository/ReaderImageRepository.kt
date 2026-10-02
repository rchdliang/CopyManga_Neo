package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage

interface ReaderImageRepository {
    suspend fun prepare(source: String, title: String): Result<ReaderExportImage>
    suspend fun saveToGallery(image: ReaderExportImage): Result<Unit>
    suspend fun saveToDocument(image: ReaderExportImage, destination: String): Result<Unit>
}
