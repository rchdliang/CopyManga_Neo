package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.BrowseHistoryPage
import com.rchdliang.copymanga_neo.domain.model.CachedComicThemes
import com.rchdliang.copymanga_neo.domain.model.ChapterCatalog
import com.rchdliang.copymanga_neo.domain.model.ChapterContent
import com.rchdliang.copymanga_neo.domain.model.ChapterInfo
import com.rchdliang.copymanga_neo.domain.model.ChapterList
import com.rchdliang.copymanga_neo.domain.model.ComicDetail
import com.rchdliang.copymanga_neo.domain.model.ComicPage
import com.rchdliang.copymanga_neo.domain.model.ComicStatus
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.model.FavoriteItem
import com.rchdliang.copymanga_neo.domain.model.HomeComicsData
import com.rchdliang.copymanga_neo.domain.model.QueryType
import com.rchdliang.copymanga_neo.domain.model.Theme
import com.rchdliang.copymanga_neo.domain.model.TrialContent
import kotlinx.coroutines.flow.Flow

/**
 * 漫画数据仓库接口
 */
interface MangaRepository {
    /**
     * 获取首页漫画数据
     */
    suspend fun getHomeComics(): Result<HomeComicsData>

    /**
     * 获取漫画详情
     */
    suspend fun getComicDetail(pathWord: String): Result<ComicDetail>

    /** 获取完整题材信息，复用详情标签缓存；空列表表示详情确认没有题材。 */
    suspend fun getComicThemes(pathWord: String): Result<List<Theme>>

    val cachedComicThemes: Flow<Map<String, CachedComicThemes>>

    /** 后台标签补全共用受控并发与起始间隔，未到期缓存不产生网络请求。 */
    suspend fun refreshComicThemes(pathWord: String): Result<List<Theme>>

    /** 获取 WWW 完整章节目录；刷新详情时强制更新，阅读和下载共用短期缓存。 */
    suspend fun getChapterCatalog(
        pathWord: String,
        forceRefresh: Boolean = false
    ): Result<ChapterCatalog>

    /**
     * 获取章节列表（单页）
     * @param pathWord 漫画路径标识
     * @param group 分组（不同汉化组）
     * @param offset 偏移量
     * @param limit 每页数量
     */
    suspend fun getChapters(
        pathWord: String,
        group: String = "default",
        offset: Int = 0,
        limit: Int = 20
    ): Result<ChapterList>

    /** 优先查找完整目录，失败后使用原 API 分页；不下载图片或请求章节 HTML。 */
    suspend fun getChapterInfo(
        comicPathWord: String, chapterUuid: String, group: String = "default"
    ): Result<ChapterInfo>

    /**
     * 获取漫画前五页（用于记录阅读进度）
     *
     */
    suspend fun trialChapterContent(
        comicPathWord: String,
        chapterUuid: String,
    ): Result<TrialContent>

    /**
     * 获取章节具体内容（图片列表）
     * @param group 汉化组 pathWord
     */
    suspend fun getChapterContent(
        comicPathWord: String,
        chapterUuid: String,
        group: String = "default"
    ): Result<ChapterContent>

    /** 下载内容与阅读分离；App 模式请求会消耗额度，网页模式沿用网页内容。 */
    suspend fun getChapterDownloadContent(
        comicPathWord: String, chapterUuid: String, group: String = "default"
    ): Result<ChapterContent> = getChapterContent(comicPathWord, chapterUuid, group)

    /**
     * 搜索漫画
     */
    suspend fun searchComics(
        query: String,
        page: Int,
        queryType: QueryType? = null
    ): Result<List<ComicSummary>>

    /** 返回服务端总数与游标；兼容仅实现旧列表方法的仓库。 */
    suspend fun searchComicPage(
        query: String, page: Int, queryType: QueryType? = null, offset: Int? = null
    ): Result<ComicPage> = searchComics(query, page, queryType).map {
        ComicPage(it, if (it.size >= 20) page * 20 else null)
    }

    /**
     * 获取推荐漫画
     */
    suspend fun getRecComics(limit: Int = 10, offset: Int = 0): Result<List<ComicSummary>>

    /** 按题材标识筛选漫画。 */
    suspend fun getThemeComics(theme: String, offset: Int = 0): Result<ComicPage>

    /**
     * 分页获取完整书架，供列表和更新角标共用。
     */
    suspend fun getFavoriteComics(): Result<List<FavoriteItem>>

    /** 获取一页云端浏览记录。 */
    suspend fun getBrowseHistory(offset: Int = 0): Result<BrowseHistoryPage>

    /** 清空当前账号的全部漫画浏览记录。 */
    suspend fun clearBrowseHistory(): Result<Unit>

    /**
     * 查询漫画收藏状态和阅读进度
     */
    suspend fun queryComicStatus(pathWord: String): Result<ComicStatus>

    /**
     * 添加漫画到书架
     * @param comicId 漫画 String 类型 UUID
     */
    suspend fun addFavorite(comicId: String): Result<Unit>

    /**
     * 从书架移除漫画
     * @param collectId 收藏的 Int 类型 ID
     */
    suspend fun removeFavorite(collectId: Int): Result<Unit>

    /**
     * 从书架批量移除漫画
     * @param collectIds 要移除的收藏 Int 类型 ID 列表
     */
    suspend fun removeFavorites(collectIds: List<Int>): Result<Unit>
}
