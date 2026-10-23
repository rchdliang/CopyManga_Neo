package com.rchdliang.copymanga_neo.multiplatform.ui.export

import androidx.lifecycle.SavedStateHandle
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.ComicExportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ComicExportSelectionTest {
    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun chapter(id: String, index: Int, group: String = "default") = ComicExportChapter(
        id, "第 $index 章", index, group, listOf(ComicExportPage("$id.jpg", 10, "jpg"))
    )
    private val plan = ComicExportPlan(listOf(
        ComicExportComic("a", "漫画 A", "", listOf(chapter("3", 3), chapter("1", 1), chapter("2", 2), chapter("extra", 1, "extra"))),
        ComicExportComic("b", "漫画 B", "", listOf(chapter("1", 1)))
    ), skipped = listOf("漫画 A／第 4 章：图片未完整下载"))
    private fun savedState() = SavedStateHandle(mapOf("comics" to Json.encodeToString(listOf("a", "b"))))

    @Test fun rangeUsesChapterOrderAndPreservesOtherGroupsAndComics() {
        val groups = plan.selectionGroups()
        val all = groups.flatMap { it.keys }.toSet()
        val selected = selectExportRange(all, groups.first { it.comic == "a" && it.name == "default" }, 2, 3)
        assertEquals(setOf(ExportChapterKey("a", "2"), ExportChapterKey("a", "3"),
            ExportChapterKey("a", "extra"), ExportChapterKey("b", "1")), selected)
        assertFailsWith<IllegalArgumentException> { selectExportRange(all, groups.first(), 0, 2) }
        assertFailsWith<IllegalArgumentException> { selectExportRange(all, groups.first(), 3, 2) }
    }

    @Test fun filteringDoesNotIncludeSameUuidFromAnotherComicOrEmptyComics() {
        val selected = plan.onlySelected(setOf(ExportChapterKey("b", "1")))
        assertEquals(listOf("b"), selected.comics.map { it.pathWord })
        assertEquals(1, selected.chapterCount)
        assertEquals(10L, selected.bytes)
        assertEquals(plan.skipped, selected.skipped)
    }

    @Test fun allCompleteChaptersAreInitiallySelectedAndGroupTogglesAreIndependent() {
        val vm = ComicExportSelectionViewModel(FakeExports(plan), savedState())
        assertEquals(5, vm.state.value.selected.size)
        vm.toggleGroup(vm.state.value.groups.first { it.comic == "a" && it.name == "default" })
        assertEquals(setOf(ExportChapterKey("a", "extra"), ExportChapterKey("b", "1")), vm.state.value.selected)
        vm.invert()
        assertEquals(setOf(ExportChapterKey("a", "1"), ExportChapterKey("a", "2"), ExportChapterKey("a", "3")), vm.state.value.selected)
        vm.clear()
        assertNull(vm.selectedPlan())
    }

    @Test fun restoredSelectionRemovesMissingChaptersWithoutSelectingNewDownloads() {
        val repository = FakeExports(plan)
        val handle = savedState()
        val first = ComicExportSelectionViewModel(repository, handle)
        first.clear()
        first.toggle(ExportChapterKey("a", "1"))
        first.toggle(ExportChapterKey("b", "1"))
        repository.plan = plan.copy(comics = listOf(plan.comics.last().copy(chapters = listOf(chapter("1", 1), chapter("new", 2)))))
        val restored = ComicExportSelectionViewModel(repository, handle)
        assertEquals(setOf(ExportChapterKey("b", "1")), restored.state.value.selected)
        restored.clear()
        assertTrue(ComicExportSelectionViewModel(repository, handle).state.value.selected.isEmpty())
    }

    @Test fun cancelAndRestoreFormatFlowKeepExactSelectedPlanAndStartOnlyAfterDestination() {
        val repository = FakeExports(plan)
        val selection = ComicExportSelectionViewModel(repository, savedState())
        selection.clear()
        selection.toggle(ExportChapterKey("b", "1"))
        val handle = SavedStateHandle()
        val export = ComicExportViewModel(repository, handle)
        export.preparePlan(selection.selectedPlan()!!)
        export.setFormat(ComicExportFormat.CBZ)
        export.saveDestination(null)
        assertNull(repository.startedPlan)
        assertEquals(setOf(ExportChapterKey("b", "1")), selection.state.value.selected)
        val restored = ComicExportViewModel(repository, handle)
        assertEquals(ComicExportFormat.CBZ, restored.uiState.value.format)
        assertEquals(listOf("b"), restored.uiState.value.plan!!.comics.map { it.pathWord })
        restored.saveDestination("test-destination")
        assertEquals(1, repository.startedPlan!!.chapterCount)
        assertTrue(restored.uiState.value.started)
        assertFalse(restored.uiState.value.visible)
    }

    @Test fun failedStartKeepsFormatAndSelectionForRetry() {
        val repository = FakeExports(plan).apply { failStart = true }
        val export = ComicExportViewModel(repository, SavedStateHandle())
        export.preparePlan(plan.onlySelected(setOf(ExportChapterKey("b", "1"))))
        export.saveDestination("test-destination")
        assertFalse(export.uiState.value.started)
        assertTrue(export.uiState.value.visible)
        assertEquals(1, export.uiState.value.plan!!.chapterCount)
        assertEquals("无法写入", export.uiState.value.error)
    }

    internal class FakeExports(var plan: ComicExportPlan) : ComicExportRepository {
        override val state = MutableStateFlow(ComicExportState())
        override val messages = emptyFlow<String>()
        var draft: ComicExportDraft? = null
        var startedPlan: ComicExportPlan? = null
        var failStart = false
        override suspend fun prepare(request: ComicExportRequest) = Result.success(plan)
        override suspend fun saveDraft(plan: ComicExportPlan, outputs: List<ComicExportOutput>): Result<String> {
            draft = ComicExportDraft(plan, outputs)
            return Result.success("draft")
        }
        override suspend fun readDraft(id: String) = Result.success(draft)
        override suspend fun start(plan: ComicExportPlan, format: ComicExportFormat, destination: String,
            directory: Boolean, previousOutputs: List<ComicExportOutput>): Result<Unit> {
            if (failStart) return Result.failure(IllegalStateException("无法写入"))
            startedPlan = plan
            return Result.success(Unit)
        }
        override suspend fun awaitRestoration() = Result.success(Unit)
        override fun cancel() = Unit
        override suspend fun dismiss() = Result.success(Unit)
        override fun describeLocation(plan: ComicExportPlan, format: ComicExportFormat, previousOutputs: List<ComicExportOutput>) =
            ComicExportLocation(true, "export", format.mimeType)
    }
}
