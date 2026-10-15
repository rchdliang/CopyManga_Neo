package com.rchdliang.copymanga_neo.data.download.portable

import com.rchdliang.copymanga_neo.data.repository.portable.PortableDownloadedCommentStore
import com.rchdliang.copymanga_neo.domain.model.DownloadedChapterComments

class DownloadedCommentStore(private val records: DownloadRecords) : PortableDownloadedCommentStore {
    override suspend fun hasCompleteChapter(uuid: String): Boolean =
        records.getChapter(uuid)?.let { records.hasCompleteFiles(it) } == true
    override suspend fun read(uuid: String): DownloadedChapterComments? =
        if (hasCompleteChapter(uuid)) records.readComments(uuid) else null
    override suspend fun save(uuid: String, value: DownloadedChapterComments) = records.saveComments(uuid, value)
}
