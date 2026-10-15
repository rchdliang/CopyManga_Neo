package com.rchdliang.copymanga_neo.data.export.portable

import kotlinx.serialization.json.Json
import com.rchdliang.copymanga_neo.domain.model.ComicExportDraft
import com.rchdliang.copymanga_neo.domain.model.ComicExportFailure
import com.rchdliang.copymanga_neo.domain.model.ComicExportFormat
import com.rchdliang.copymanga_neo.domain.model.ComicExportOutput
import com.rchdliang.copymanga_neo.domain.model.ComicExportPlan
import com.rchdliang.copymanga_neo.domain.model.ComicExportState
import com.rchdliang.copymanga_neo.domain.model.ComicExportStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class ComicExportResultTest {
    private val output = ComicExportOutput(
        "chapter-1",
        "content://test/comic",
        "comic.cbz",
        ComicExportFormat.CBZ.mimeType
    )

    @Test
    fun cancelledExportReportsCompletedFilesAndCleanupWarning() {
        val state = ComicExportState(
            status = ComicExportStatus.CANCELLED,
            outputs = listOf(output),
            cleanupWarning = "cleanup failed"
        )
        val message = state.resultMessage()
        assertTrue(message.contains("已取消"))
        assertTrue(message.contains("成功 1 项"))
        assertTrue(message.contains("保存位置检查"))
    }

    @Test
    fun failedExportShowsReasonRatherThanSuccessfulCompletion() {
        val message = ComicExportState(
            status = ComicExportStatus.COMPLETED,
            failures = listOf(ComicExportFailure("chapter-1", "comic.cbz", "保存空间不足"))
        ).resultMessage()
        assertTrue(message.startsWith("导出失败"))
        assertTrue(message.contains("失败 1 项"))
        assertTrue(message.contains("保存空间不足"))
    }

    @Test
    fun retryDraftRestoresItsOwnCompletedFiles() {
        val draft = ComicExportDraft(ComicExportPlan(emptyList()), listOf(output))
        val restored = Json.decodeFromString(ComicExportDraft.serializer(), Json.encodeToString(ComicExportDraft.serializer(), draft))
        assertEquals(listOf(output), restored.outputs)
    }
}
