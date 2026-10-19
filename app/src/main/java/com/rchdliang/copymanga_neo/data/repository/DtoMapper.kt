package com.rchdliang.copymanga_neo.data.repository

import com.rchdliang.copymanga_neo.data.local.entity.DownloadedChapterEntity
import com.rchdliang.copymanga_neo.data.local.entity.DownloadedComicEntity
import com.rchdliang.copymanga_neo.data.remote.dto.AuthorDto
import com.rchdliang.copymanga_neo.data.remote.dto.BrowseDto
import com.rchdliang.copymanga_neo.data.remote.dto.BrowseHistoryDto
import com.rchdliang.copymanga_neo.data.remote.dto.ChapterDto
import com.rchdliang.copymanga_neo.data.remote.dto.ChapterListResult
import com.rchdliang.copymanga_neo.data.remote.dto.CharacterDto
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDetailDto
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDetailResult
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDto
import com.rchdliang.copymanga_neo.data.remote.dto.ComicQueryResult
import com.rchdliang.copymanga_neo.data.remote.dto.DisplayAndValue
import com.rchdliang.copymanga_neo.data.remote.dto.FavComicDto
import com.rchdliang.copymanga_neo.data.remote.dto.FavoritesDto
import com.rchdliang.copymanga_neo.data.remote.dto.GroupsDto
import com.rchdliang.copymanga_neo.data.remote.dto.LastChapterDto
import com.rchdliang.copymanga_neo.data.remote.dto.LoginResult
import com.rchdliang.copymanga_neo.data.remote.dto.MemberInfoResult
import com.rchdliang.copymanga_neo.data.remote.dto.ThemeDto
import com.rchdliang.copymanga_neo.data.remote.dto.TrialContentResult
import com.rchdliang.copymanga_neo.domain.model.Author
import com.rchdliang.copymanga_neo.domain.model.BrowseHistoryItem
import com.rchdliang.copymanga_neo.domain.model.BrowseInfo
import com.rchdliang.copymanga_neo.domain.model.ChapterGroup
import com.rchdliang.copymanga_neo.domain.model.ChapterInfo
import com.rchdliang.copymanga_neo.domain.model.ChapterList
import com.rchdliang.copymanga_neo.domain.model.Character
import com.rchdliang.copymanga_neo.domain.model.ComicDetail
import com.rchdliang.copymanga_neo.domain.model.ComicInfo
import com.rchdliang.copymanga_neo.domain.model.ComicStatus
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.model.DisplayValue
import com.rchdliang.copymanga_neo.domain.model.DownloadedChapterInfo
import com.rchdliang.copymanga_neo.domain.model.DownloadedComicInfo
import com.rchdliang.copymanga_neo.domain.model.FavoriteComic
import com.rchdliang.copymanga_neo.domain.model.FavoriteItem
import com.rchdliang.copymanga_neo.domain.model.LastChapter
import com.rchdliang.copymanga_neo.domain.model.LoginInfo
import com.rchdliang.copymanga_neo.domain.model.MemberInfo
import com.rchdliang.copymanga_neo.domain.model.Theme
import com.rchdliang.copymanga_neo.domain.model.TrialContent

// region Entity → domain 映射

fun DownloadedComicEntity.toInfo() = DownloadedComicInfo(
    pathWord = pathWord,
    name = name,
    cover = cover,
    authorNames = authorNames,
    statusDisplay = statusDisplay,
    lastUpdate = lastUpdate,
    lastChapterName = lastChapterName,
    brief = brief,
    totalChapters = totalChapters,
    downloadedChapters = downloadedChapters,
    downloadAt = downloadAt
)

fun DownloadedChapterEntity.toInfo() = DownloadedChapterInfo(
    uuid = uuid,
    comicPathWord = comicPathWord,
    name = name,
    index = index,
    imageCount = imageCount,
    imagePaths = imagePaths.split(",").filter { it.isNotBlank() },
    groupPathWord = groupPathWord,
    prevChapter = prevChapter,
    nextChapter = nextChapter
)

// endregion

// region DTO → domain 映射

fun AuthorDto.toAuthor() = Author(name, pathWord)
fun ThemeDto.toTheme() = Theme(name, pathWord)
fun CharacterDto.toCharacter() = Character(name, pathWord)
fun DisplayAndValue.toDisplayValue() = DisplayValue(display, value)
fun LastChapterDto.toLastChapter() = LastChapter(lastChapterId, lastChapterName)
fun GroupsDto.toGroup() = ChapterGroup(pathWord, total, name)
fun BrowseDto.toBrowseInfo() = BrowseInfo(comicUuid, pathWord, chapterUuid, chapterName)

fun BrowseHistoryDto.toHistoryItem() = BrowseHistoryItem(
    id = id,
    name = comic.name,
    pathWord = comic.pathWord,
    cover = comic.cover,
    authors = comic.authors.orEmpty().map { it.toAuthor() },
    lastReadChapterId = lastReadChapterId,
    lastReadChapterName = lastReadChapterName,
    latestChapterId = comic.latestChapterId,
    latestChapterName = comic.latestChapterName
)

fun ComicDto.toSummary() = ComicSummary(
    name = name,
    pathWord = pathWord,
    cover = cover,
    author = author?.map { it.toAuthor() },
    popular = popular,
    females = females?.map { it.toCharacter() },
    males = males?.map { it.toCharacter() },
    theme = theme?.map { it.toTheme() },
    authorAlias = authorAlias,
    lastUpdate = lastUpdate
)

fun ComicDetailDto.toInfo() = ComicInfo(
    uuid = uuid,
    b404 = b404,
    bHidden = bHidden,
    ban = ban,
    name = name,
    comicAlias = comicAlias,
    pathWord = pathWord,
    closeComment = closeComment,
    closeRoast = closeRoast,
    freeType = freeType.toDisplayValue(),
    restrict = restrict.toDisplayValue(),
    reclass = reclass.toDisplayValue(),
    females = females,
    males = males,
    clubs = clubs,
    region = region.toDisplayValue(),
    status = status.toDisplayValue(),
    author = author?.map { it.toAuthor() },
    theme = theme?.map { it.toTheme() },
    parodies = parodies,
    brief = brief,
    lastUpdate = lastUpdate,
    cover = cover,
    lastChapter = lastChapter?.toLastChapter(),
    popular = popular,
    channel = channel
)

fun ComicDetailResult.toDetail() = ComicDetail(
    comic = comicDetail.toInfo(),
    groups = groups.mapValues { it.value.toGroup() },
    isBanned = isBanned,
    isLock = isLock,
    isLogin = isLogin,
    isMobileBind = isMobileBind,
    isVip = isVip
)

fun ChapterDto.toInfo() = ChapterInfo(
    index = index,
    uuid = uuid,
    count = count,
    ordered = ordered,
    size = size,
    name = name,
    comicId = comicId,
    comicPathWord = comicPathWord,
    groupId = groupId,
    groupPathWord = groupPathWord,
    type = type,
    news = news,
    datetimeCreated = datetimeCreated,
    prevChapter = prevChapter,
    nextChapter = nextChapter
)

fun ChapterListResult.toList() = ChapterList(
    list = list.map { it.toInfo() },
    total = total
)

fun TrialContentResult.toContent() = TrialContent(
    showApp = showApp,
    isLock = isLock,
    isLogin = isLogin,
    isVip = isVip,
    isBanned = isBanned
)

fun FavComicDto.toFavoriteComic() = FavoriteComic(
    uuid = uuid,
    bDisplay = bDisplay,
    name = name,
    pathWord = pathWord,
    author = author?.map { it.toAuthor() },
    theme = theme?.map { it.toTheme() },
    cover = cover,
    status = status,
    popular = popular,
    datetimeUpdated = datetimeUpdated,
    lastChapterId = lastChapterId,
    lastChapterName = lastChapterName,
    browse = browse?.toBrowseInfo()
)

fun FavoritesDto.toFavoriteItem() = FavoriteItem(
    uuid = uuid,
    name = name,
    bFolder = bFolder,
    folderId = folderId,
    comic = favComic.toFavoriteComic()
)

fun ComicQueryResult.toStatus() = ComicStatus(
    browse = browse?.toBrowseInfo(),
    collect = collect
)

fun LoginResult.toLoginInfo() = LoginInfo(
    token = token,
    userId = userId,
    username = username,
    nickname = nickname,
    avatar = avatar,
    ticket = ticket,
    rewardTicket = rewardTicket
)

fun MemberInfoResult.toMemberInfo() = MemberInfo(
    token = token,
    userId = userId,
    username = username,
    nickname = nickname,
    avatar = avatar,
    ticket = ticket,
    rewardTicket = rewardTicket,
    dayDownloads = dayDownloads,
    dayDownloadsRefresh = dayDownloadsRefresh
)

// endregion
