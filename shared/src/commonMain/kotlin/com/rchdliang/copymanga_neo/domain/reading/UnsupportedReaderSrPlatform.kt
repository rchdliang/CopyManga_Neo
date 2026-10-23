package com.rchdliang.copymanga_neo.domain.reading

import kotlinx.coroutines.flow.MutableStateFlow

internal object UnsupportedReaderSrPlatform : ReaderSrPlatform {
    override val unavailableReason = "此平台暂不支持本地超分，保留原图阅读"
    override val budgetBytes = 96L*1024*1024
    override val workingBytes = 0L
    override val detail = MutableStateFlow(unavailableReason)
    override suspend fun source(page: SrPage): SrSource = error(unavailableReason)
    override suspend fun upscale(request: SrRequest): SrArtifact = error(unavailableReason)
}
