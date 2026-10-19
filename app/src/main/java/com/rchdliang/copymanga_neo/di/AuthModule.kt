package com.rchdliang.copymanga_neo.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.rchdliang.copymanga_neo.data.local.security.createSecureAuthDataStore
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

    // 此绑定由 Hilt 生成代码使用。
    @Suppress("unused")
    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository

    companion object {

        @Provides
        @Singleton
        fun provideAuthDataStore(
            dataStore: DataStore<Preferences>,
            networkSettings: com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore,
            cookieJar: PersistentCookieJar,
            @ApplicationContext context: Context
        ): AuthDataStore {
            return AuthDataStore(dataStore, networkSettings, cookieJar::clearAll, clearLegacyAuth = {
                // 未发布的明文认证文件不再读取；安全库成功初始化后清理。
                for (suffix in listOf("", ".tmp", ".bak")) {
                    val legacy =
                        java.io.File(context.filesDir, "datastore/auth_prefs.preferences_pb$suffix")
                    if (legacy.exists() && !legacy.delete()) {
                        android.util.Log.w("Auth", "旧认证文件清理未完成，已排除系统备份")
                    }
                }
            }, validateStorage = cookieJar::requireReady)
        }

        @Provides
        @Singleton
        fun provideAuthPreferencesDataStore(
            @ApplicationContext context: Context
        ): DataStore<Preferences> {
            return createSecureAuthDataStore(context)
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