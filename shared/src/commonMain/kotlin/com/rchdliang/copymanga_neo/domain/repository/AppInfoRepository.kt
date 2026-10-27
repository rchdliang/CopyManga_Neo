package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.AppRelease

class PublicReleaseUnavailableException :
    IllegalStateException("公开发布暂不可访问，请前往 GitHub 查看")

interface AppInfoRepository {
    suspend fun latestRelease(): Result<AppRelease>
}

object ProjectLinks {
    const val GITHUB = "https://github.com/rchdliang/CopyManga_Neo"
    const val RELEASES_API = "https://api.github.com/repos/rchdliang/CopyManga_Neo/releases"
}
