package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.data.remote.dto.LoginResult
import com.rchdliang.copymanga_neo.data.remote.dto.MemberInfoResult
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
    suspend fun login(username: String, rawPassword: String): Result<LoginResult>

    /**
     * 获取用户信息
     */
    suspend fun getMemberInfo(): Result<MemberInfoResult>

    /**
     * 登出
     */
    suspend fun logout(): Result<Unit>

    /**
     * 是否已登录
     */
    fun isLoggedIn(): Flow<Boolean>

    /**
     * 是否已登录（同步，取缓存值，适用于初始化状态）
     */
    fun isLoggedInSync(): Boolean

    /**
     * 获取当前用户名
     */
    fun getUsername(): Flow<String?>

    fun getAvatar(): Flow<String?>
}