package com.rchdliang.copymanga_neo.data.repository

import com.rchdliang.copymanga_neo.data.remote.GitHubMetadata
import com.rchdliang.copymanga_neo.domain.repository.AppInfoRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInfoRepositoryImpl @Inject constructor(private val metadata: GitHubMetadata) :
    AppInfoRepository {
    override suspend fun latestRelease() = try {
        Result.success(metadata.latestApp())
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Exception) {
        Result.failure(error)
    }
}
