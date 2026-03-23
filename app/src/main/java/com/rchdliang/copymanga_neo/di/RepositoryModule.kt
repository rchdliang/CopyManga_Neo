package com.rchdliang.copymanga_neo.di

import com.rchdliang.copymanga_neo.data.repository.MangaRepositoryImpl
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt Repository模块
 * 绑定Repository接口和实现
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindMangaRepository(
        mangaRepositoryImpl: MangaRepositoryImpl
    ): MangaRepository
}