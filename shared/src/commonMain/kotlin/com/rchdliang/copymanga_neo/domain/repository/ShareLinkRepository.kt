package com.rchdliang.copymanga_neo.domain.repository

interface ShareLinkRepository {
    suspend fun baseUrl(): Result<String>
}
