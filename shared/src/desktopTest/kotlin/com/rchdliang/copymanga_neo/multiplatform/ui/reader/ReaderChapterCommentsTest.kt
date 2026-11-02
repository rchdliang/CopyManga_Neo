package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.lifecycle.ViewModelStore
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderChapterCommentsTest {
    private class FakeComments : CommentRepository {
        val calls = mutableListOf<String>()
        var cached: DownloadedChapterComments? = null
        var downloaded = false
        var answer: suspend (CommentTarget) -> Result<CommentPage> = { Result.success(CommentPage(emptyList(), 7, null)) }
        override suspend fun getComments(target: CommentTarget, offset: Int, replyId: String?): Result<CommentPage> {
            calls += target.id
            return answer(target)
        }
        override suspend fun getDownloadedChapterComments(uuid: String) = Result.success(cached)
        override suspend fun hasDownloadedChapter(uuid: String) = Result.success(downloaded || cached != null)
        override suspend fun downloadChapterComments(uuid: String): Result<DownloadedChapterComments> = error("unused")
        override suspend fun getReportConfig(target: CommentTarget): Result<CommentReportConfig> = error("unused")
        override suspend fun reportComment(target: CommentTarget, commentId: String, type: Int, reason: String): Result<Unit> = error("unused")
        override suspend fun getReplyPreview(target: CommentTarget, commentId: String): Result<CommentReplyPreview> = error("unused")
        override suspend fun postComment(target: CommentTarget, content: String, replyId: String?): Result<Unit> = error("unused")
    }

    @Test fun failedAutomaticLoadStaysRetryableWithoutReopeningRequestLoop() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeComments().apply { answer = { Result.failure(RiskControlException("Request was throttled")) } }
        val model = ReaderChapterCommentsViewModel(repo)
        val store = ViewModelStore().apply { put("comments", model) }
        try {
            model.load("A"); runCurrent()
            repeat(20) { model.load("A"); runCurrent() }
            assertEquals(listOf("A"), repo.calls)
            assertTrue(model.uiState.value.rateLimited)
            assertFalse(model.uiState.value.closed)
            assertTrue(model.uiState.value.error!!.contains("210"))
            repo.answer = { Result.success(CommentPage(emptyList(), 7, null)) }
            model.load("A", refresh = true); runCurrent()
            assertEquals(listOf("A", "A"), repo.calls)
            assertEquals(7, model.uiState.value.page!!.total)
            assertNull(model.uiState.value.error)
            assertFalse(model.uiState.value.rateLimited)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun changingChapterCancelsOldLoadAndRejectsLateResult() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val finish = CompletableDeferred<Unit>()
        val repo = FakeComments().apply { answer = { target ->
            if (target.id == "A") withContext(NonCancellable) { finish.await() }
            Result.success(CommentPage(emptyList(), if (target.id == "A") 1 else 2, null))
        } }
        val model = ReaderChapterCommentsViewModel(repo)
        val store = ViewModelStore().apply { put("comments", model) }
        try {
            model.load("A"); runCurrent()
            repeat(20) { model.load("A") }
            assertEquals(listOf("A"), repo.calls)
            model.load("B"); runCurrent()
            assertEquals(listOf("A", "B"), repo.calls)
            assertEquals(2, model.uiState.value.page!!.total)
            finish.complete(Unit); runCurrent()
            assertEquals(2, model.uiState.value.page!!.total)
        } finally { finish.complete(Unit); store.clear(); Dispatchers.resetMain() }
    }

    @Test fun closedChapterDoesNotCloseAnotherChapter() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeComments().apply { answer = { target ->
            if (target.id == "A") Result.failure(CommentClosedException("closed"))
            else Result.success(CommentPage(emptyList(), 2, null))
        } }
        val model = ReaderChapterCommentsViewModel(repo)
        val store = ViewModelStore().apply { put("comments", model) }
        try {
            model.load("A"); runCurrent()
            assertTrue(model.uiState.value.closed)
            model.load("B"); runCurrent()
            assertFalse(model.uiState.value.closed)
            assertEquals(2, model.uiState.value.page!!.total)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun downloadedSummaryNeverFetchesAutomaticallyIncludingEmptyAndPartialSnapshots() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = FakeComments().apply { downloaded = true }
        val model = ReaderChapterCommentsViewModel(repo)
        val store = ViewModelStore().apply { put("comments", model) }
        try {
            val comment = MangaComment("local", "reader", null, "离线评论", "2026-11-03")
            listOf(null, DownloadedChapterComments(listOf(comment), 10, false, savedAt = 1),
                DownloadedChapterComments(emptyList(), 0, true, closed = true, savedAt = 1)).forEachIndexed { index, snapshot ->
                repo.cached = snapshot
                model.load("chapter-$index"); runCurrent()
                model.load("chapter-$index", refresh = true); runCurrent()
                assertTrue(model.uiState.value.localOnly)
                assertFalse(model.uiState.value.loading)
                assertNull(model.uiState.value.error)
                assertFalse(model.uiState.value.closed)
                assertEquals(snapshot?.comments.orEmpty(), model.uiState.value.page!!.comments)
            }
            assertTrue(repo.calls.isEmpty())
            model.updateFeed("chapter-2", com.rchdliang.copymanga_neo.multiplatform.ui.comments.CommentFeed(total = 8))
            assertFalse(model.uiState.value.localOnly)
            model.load("chapter-2", refresh = true); runCurrent()
            assertEquals(listOf("chapter-2"), repo.calls)
            assertEquals(7, model.uiState.value.page!!.total)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

}
