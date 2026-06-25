package com.rchdliang.copymanga_neo.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.rchdliang.copymanga_neo.data.local.AuthDataStore
import com.rchdliang.copymanga_neo.data.repository.UserRepositoryImpl
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 认证模块
 * 提供 AuthDataStore、DataStore<Preferences>、PersistentCookieJar 等认证相关依赖
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository

    companion object {
        private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(
            name = "auth_prefs"
        )

        @Provides
        @Singleton
        fun provideAuthDataStore(
            dataStore: DataStore<Preferences>,
            encryptedPrefs: SharedPreferences
        ): AuthDataStore {
            return AuthDataStore(dataStore, encryptedPrefs)
        }

        @Provides
        @Singleton
        fun provideEncryptedSharedPreferences(
            @ApplicationContext context: Context
        ): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                "auth_encrypted_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        @Provides
        @Singleton
        fun provideAuthPreferencesDataStore(
            @ApplicationContext context: Context
        ): DataStore<Preferences> {
            return context.authDataStore
        }

        @Provides
        @Singleton
        fun providePersistentCookieJar(
            @ApplicationContext context: Context
        ): PersistentCookieJar {
            return PersistentCookieJar(context)
        }
    }
}