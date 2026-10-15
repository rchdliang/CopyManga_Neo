package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.NovelAnnotation
import kotlinx.coroutines.flow.Flow

interface NovelAnnotationRepository {
    val annotations: Flow<List<NovelAnnotation>>
    suspend fun save(annotation: NovelAnnotation): Result<Unit>
    suspend fun remove(annotation: NovelAnnotation): Result<Unit>
}
