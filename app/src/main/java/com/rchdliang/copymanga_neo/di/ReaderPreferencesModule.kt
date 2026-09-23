package com.rchdliang.copymanga_neo.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.rchdliang.copymanga_neo.data.local.ReaderPreferencesStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReaderPreferencesModule {
    @Provides
    @Singleton
    fun provideReaderPreferencesRepository(store: ReaderPreferencesStore):
            com.rchdliang.copymanga_neo.domain.repository.ReaderPreferencesRepository = store


    private val Context.readerPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "reader_prefs"
    )

    @Provides
    @Singleton
    @ReaderDataStore
    fun provideReaderPreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return context.readerPreferencesDataStore
    }

    @Provides
    @Singleton
    fun provideReaderPreferencesStore(
        @ReaderDataStore dataStore: DataStore<Preferences>
    ): ReaderPreferencesStore {
        return ReaderPreferencesStore(dataStore)
    }
}
