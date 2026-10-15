package com.rchdliang.copymanga_neo.di

import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.usecase.FilterHomeComics
import com.rchdliang.copymanga_neo.domain.reading.NovelImagePositionDebugStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SharedDomainModule {
    @Provides @Singleton fun novelImagePositionDebugStore() = NovelImagePositionDebugStore()
    @Provides fun filterHomeComics(repository: MangaRepository) = FilterHomeComics(repository)
}
