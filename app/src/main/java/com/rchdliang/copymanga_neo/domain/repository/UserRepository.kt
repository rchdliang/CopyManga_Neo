package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.LoginInfo
import com.rchdliang.copymanga_neo.domain.model.MemberInfo
import kotlinx.coroutines.flow.Flow

/** 已保存但刷新失败，界面不能误导用户再次上传或提交。 */
class ProfileSavedRefreshException(message: String, cause: Throwable) : Exception(message, cause)

/**
 * 用户仓库接口
 * 处理登录/登出/认证状态
 */
interface UserRepository {
    /** 获取编辑用的当前资料，避免沿用上次上传但未保存的头像路径。 */
    suspend fun getEditableProfile(): Result<com.rchdliang.copymanga_neo.domain.model.EditableProfile>

    /** 头像为处理后的 JPEG；仅在用户选择新图片时上传。 */
    suspend fun updateProfile(
        nickname: String,
        avatarJpeg: ByteArray?
    ): Result<MemberInfo>
    /**
     * 登录
     * @param username 账号
     * @param rawPassword 原始密码（未编码）
     * @return 登录结果
     */
    suspend fun login(
        username: String,
        rawPassword: String,
        expectedGeneration: Long? = null
    ): Result<LoginInfo>

    /** 根据登录页选项登录当前模式或两种模式，并为成功模式保存登录偏好。 */
    suspend fun loginWithOptions(
        username: String,
        rawPassword: String,
        bothModes: Boolean,
        rememberPassword: Boolean,
        autoLogin: Boolean,
        expectedGeneration: Long
    ): Result<com.rchdliang.copymanga_neo.domain.model.ModeLoginResult>

    /** 只创建账号；成功后仍须登录，不把注册响应当作已登录。 */
    suspend fun register(username: String, rawPassword: String):
            Result<com.rchdliang.copymanga_neo.domain.model.RegistrationInfo>

    /** 两种模式都有会话时一起导出，否则只导出当前模式。 */
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

    /** 登录、退出、模式或网络变更时递增，用于取消旧请求及重置页面状态。 */
    fun sessionChanges(): Flow<Long>
    fun sessionVersionSync(): Long

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
        autoLogin: Boolean,
        expectedGeneration: Long
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
