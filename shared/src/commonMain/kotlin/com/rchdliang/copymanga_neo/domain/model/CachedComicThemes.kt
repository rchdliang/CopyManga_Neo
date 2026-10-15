package com.rchdliang.copymanga_neo.domain.model

/** 到期时间仅决定何时后台刷新，不代表标签失效；空列表也属于有效缓存。 */
@kotlinx.serialization.Serializable
data class CachedComicThemes(
    val themes: List<Theme>,
    val refreshAfterMillis: Long
)
