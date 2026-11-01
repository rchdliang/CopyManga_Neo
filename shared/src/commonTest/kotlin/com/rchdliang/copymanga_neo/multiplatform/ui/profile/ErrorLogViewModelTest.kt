package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.lifecycle.SavedStateHandle
import com.rchdliang.copymanga_neo.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ErrorLogViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun cleanup() { Dispatchers.resetMain() }

    private class Repository : ErrorLogRepository {
        override val state = MutableStateFlow(ErrorLogState(bytes = 12))
        var exports = 0
        var clears = 0
        override suspend fun refresh() {}
        override suspend fun clear(): Result<Unit> { clears++; return Result.success(Unit) }
        override suspend fun export(): Result<ByteArray> { exports++; return Result.success("saved crash".encodeToByteArray()) }
    }
    private class Platform : ProfilePlatform {
        var selected: String? = "chosen-file"
        var written: String? = null
        var writeFails = false
        var cancel = false
        override suspend fun createDocument(name: String, mimeType: String): Result<String?> {
            assertTrue(name.startsWith("CopyManga-errors-"))
            if (cancel) throw CancellationException()
            return Result.success(selected)
        }
        override suspend fun writeDocument(reference: String, bytes: ByteArray): Result<Unit> {
            assertEquals("chosen-file", reference)
            return if (writeFails) Result.failure(IllegalStateException("read-only")) else {
                written = bytes.decodeToString()
                Result.success(Unit)
            }
        }
        override suspend fun readClipboardText(): Result<String?> = error("unused")
        override suspend fun copySensitiveText(label: String, text: String): Result<Unit> = error("unused")
        override suspend fun openDocument(mimeTypes: List<String>): Result<String?> = error("unused")
        override suspend fun readDocument(reference: String, maxBytes: Int): Result<ByteArray> = error("unused")
        override suspend fun exportNetworkLog(reference: String, repository: NetworkLogRepository): Result<Unit> = error("unused")
        override suspend fun pickAvatarJpeg(): Result<ByteArray?> = error("unused")
        override suspend fun openNotificationSettings(): Result<Unit> = error("unused")
    }

    @Test fun exportWritesExistingLogsToSelectedFile() {
        val repository = Repository()
        val platform = Platform()
        val model = ErrorLogViewModel(repository, platform)
        model.chooseExport()
        assertEquals("saved crash", platform.written)
        assertEquals(1, repository.exports)
        assertEquals("错误与崩溃日志已导出", model.message.value)
        assertFalse(model.busy.value)
    }
    @Test fun dismissingPickerNeitherReadsNorReportsSuccess() {
        val repository = Repository()
        val platform = Platform().apply { selected = null }
        val saved = SavedStateHandle()
        val model = ErrorLogViewModel(repository, platform, saved)
        model.chooseExport()
        assertEquals(0, repository.exports)
        assertNull(model.message.value)
        assertNull(saved.get<String>("pending_error_export"))
        assertFalse(model.busy.value)
    }
    @Test fun failedWriteDoesNotReportSuccessOrClearLogs() {
        val repository = Repository()
        val model = ErrorLogViewModel(repository, Platform().apply { writeFails = true })
        model.chooseExport()
        assertEquals("导出失败，请重试", model.message.value)
        assertEquals(0, repository.clears)
        assertFalse(model.busy.value)
    }
    @Test fun cancellationClearsBusyAndPendingExport() {
        val saved = SavedStateHandle()
        val model = ErrorLogViewModel(Repository(), Platform().apply { cancel = true }, saved)
        model.chooseExport()
        assertFalse(model.busy.value)
        assertNull(saved.get<String>("pending_error_export"))
        assertNull(model.message.value)
    }
}
