package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.LoginInfo
import com.rchdliang.copymanga_neo.domain.model.MemberInfo
import kotlinx.coroutines.flow.Flow

/**
 * 用户仓库接口
 * 处理登录/登出/认证状态
 */
interface UserRepository {
    /**
     * 登录
     * @param username 账号
     * @param rawPassword 原始密码（未编码）
     * @return 登录结果
     */
    suspend fun login(username: String, rawPassword: String): Result<LoginInfo>

    /** 只创建账号；成功后仍须登录，不把注册响应当作已登录。 */
    suspend fun register(username: String, rawPassword: String):
            Result<com.rchdliang.copymanga_neo.domain.model.RegistrationInfo>

    /** 将当前会话导出为可复制的版本化登录凭据。 */
    suspend fun exportLoginCredential(): Result<String>

    /** 验证并导入登录凭据；验证成功后才保存新的登录状态。 */
    suspend fun importLoginCredential(serializedCredential: String): Result<MemberInfo>

    /**
     * 获取用户信息
     */
    suspend fun getMemberInfo(): Result<MemberInfo>

    /**
     * 登出
     */
    suspend fun logout(): Result<Unit>

    /**
     * 是否已登录
     */
    fun isLoggedIn(): Flow<Boolean>
    fun isAuthCacheReady(): Flow<Boolean>

    /**
     * 是否已登录（同步，取缓存值，适用于初始化状态）
     */
    fun isLoggedInSync(): Boolean

    /**
     * 获取当前用户名
     */
    fun getUsername(): Flow<String?>

    fun getAvatar(): Flow<String?>

    /**
     * 保存登录凭据偏好
     * @param username 用户名
     * @param password 原始密码
     * @param rememberPassword 是否记住密码
     * @param autoLogin 是否自动登录
     */
    suspend fun saveCredentials(
        username: String,
        password: String,
        rememberPassword: Boolean,
        autoLogin: Boolean
    )

    /** 获取是否记住密码（Flow） */
    fun getRememberPassword(): Flow<Boolean>

    /** 获取是否自动登录（Flow） */
    fun getAutoLogin(): Flow<Boolean>

    /** 获取保存的用户名（Flow） */
    fun getSavedUsername(): Flow<String?>

    /** 获取保存的密码（Flow） */
    fun getSavedPassword(): Flow<String?>

    /** 是否自动登录（同步） */
    fun getAutoLoginSync(): Boolean

    /** 是否记住密码（同步） */
    fun getRememberPasswordSync(): Boolean

    /** 保存的用户名（同步） */
    fun getSavedUsernameSync(): String?

    /** 保存的密码（同步） */
    fun getSavedPasswordSync(): String?
}
