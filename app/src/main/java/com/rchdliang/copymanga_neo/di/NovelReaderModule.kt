package com.rchdliang.copymanga_neo.di

import com.rchdliang.copymanga_neo.data.local.NovelAnnotationStore
import com.rchdliang.copymanga_neo.data.local.NovelReaderPreferencesStore
import com.rchdliang.copymanga_neo.domain.repository.NovelAnnotationRepository
import com.rchdliang.copymanga_neo.domain.repository.NovelReaderPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NovelReaderModule {
    @Binds
    @Singleton
    abstract fun bindNovelAnnotations(store: NovelAnnotationStore): NovelAnnotationRepository

    @Binds
    @Singleton
    abstract fun bindNovelReaderPreferences(store: NovelReaderPreferencesStore): NovelReaderPreferencesRepository
}
