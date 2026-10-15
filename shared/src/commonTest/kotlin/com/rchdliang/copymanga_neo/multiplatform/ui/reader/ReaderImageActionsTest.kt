package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage
import com.rchdliang.copymanga_neo.domain.repository.ReaderImageRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderImageActionsTest {
    @Test
    fun consumingPendingDoesNotCancelSuspendedPickerAndDoesNotLaunchTwice() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = RecordingImages()
            val platform = DeferredPicker()
            val model = ReaderImageActionsViewModel(repository, SavedStateHandle())
            model.perform(ReaderImageAction.SAVE_AS, "https://example.invalid/page.webp", "Page")
            runCurrent()
            model.launchPending(platform)
            assertNull(model.uiState.value.pending)
            runCurrent()
            assertEquals(1, platform.pickerCount)
            assertTrue(model.uiState.value.busy)
            model.launchPending(platform)
            runCurrent()
            assertEquals(1, platform.pickerCount)
            platform.destination.complete(Result.success("destination-handle"))
            advanceUntilIdle()
            assertEquals(listOf("destination-handle"), repository.destinations)
            assertFalse(model.uiState.value.busy)
            assertTrue(model.uiState.value.closeAfterMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun cancelWhilePickerIsOpenDoesNotSaveImage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = RecordingImages()
            val platform = DeferredPicker()
            val model = ReaderImageActionsViewModel(repository, SavedStateHandle())
            model.perform(ReaderImageAction.SAVE_AS, "page.webp", "Page")
            runCurrent()
            model.launchPending(platform)
            runCurrent()
            model.cancel()
            platform.destination.complete(Result.success("cancelled-destination"))
            advanceUntilIdle()
            assertTrue(repository.destinations.isEmpty())
            assertFalse(model.uiState.value.busy)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun restoredSaveAsRejoinsChooserUsingOriginalImage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = RecordingImages()
            val platform = DeferredPicker()
            val saved = SavedStateHandle(mapOf(
                "document_image_path" to "prepared.webp",
                "document_image_mime" to "image/webp",
                "document_image_name" to "page.webp"
            ))
            val model = ReaderImageActionsViewModel(repository, saved)
            assertEquals(ReaderImageAction.SAVE_AS, model.uiState.value.pending?.action)
            model.launchPending(platform)
            runCurrent()
            platform.destination.complete(Result.success("restored-destination"))
            advanceUntilIdle()
            assertEquals(listOf("restored-destination"), repository.destinations)
            assertFalse(model.uiState.value.busy)
            assertNull(saved.get<String>("document_image_path"))
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun windowsPathsUseFilePathMapperWhileBrowserUrlsRemainUrls() {
        assertIs<Path>(readerImageData("C:\\Comics\\page.webp"))
        assertIs<Path>(readerImageData("C:/Comics/page.webp"))
        assertIs<Path>(readerImageData("\\\\server\\Comics\\page.webp"))
        assertIs<Path>(readerImageData("/tmp/page.webp"))
        assertEquals("blob:https://example.invalid/id", readerImageData("blob:https://example.invalid/id"))
        assertEquals("https://example.invalid/page.webp", readerImageData("https://example.invalid/page.webp"))
    }

    @Test
    fun cancelledGallerySaveReturnsToIdleWithoutSuccessOrErrorAndAllowsRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = RecordingImages()
            repository.galleryResult = Result.failure(com.rchdliang.copymanga_neo.domain.model.UserActionCancelled())
            val model = ReaderImageActionsViewModel(repository, SavedStateHandle())
            model.perform(ReaderImageAction.SAVE, "page.webp", "Page")
            advanceUntilIdle()
            assertEquals(ReaderImageActionsUiState(), model.uiState.value)
            repository.galleryResult = Result.success(Unit)
            model.perform(ReaderImageAction.SAVE, "page.webp", "Page")
            advanceUntilIdle()
            assertFalse(model.uiState.value.busy)
            assertEquals("图片已保存", model.uiState.value.message)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun dismissedShareReturnsIdleWithoutDismissingImageMenuAndCanRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val platform = DeferredPicker()
            platform.shareResult = Result.failure(com.rchdliang.copymanga_neo.domain.model.UserActionCancelled())
            val model = ReaderImageActionsViewModel(RecordingImages(), SavedStateHandle())
            model.perform(ReaderImageAction.SHARE, "page.webp", "Page")
            runCurrent(); model.launchPending(platform); advanceUntilIdle()
            assertEquals(ReaderImageActionsUiState(), model.uiState.value)
            platform.shareResult = Result.success(Unit)
            model.perform(ReaderImageAction.SHARE, "page.webp", "Page")
            runCurrent(); model.launchPending(platform); advanceUntilIdle()
            assertTrue(model.uiState.value.dismissRequested)
            assertNull(model.uiState.value.message)
        } finally { Dispatchers.resetMain() }
    }

    private class RecordingImages : ReaderImageRepository {
        var galleryResult: Result<Unit> = Result.success(Unit)
        val destinations = mutableListOf<String>()
        override suspend fun prepare(source: String, title: String) =
            Result.success(ReaderExportImage("prepared.webp", "image/webp", "page.webp"))
        override suspend fun saveToGallery(image: ReaderExportImage) = galleryResult
        override suspend fun saveToDocument(image: ReaderExportImage, destination: String): Result<Unit> {
            destinations += destination
            return Result.success(Unit)
        }
    }

    private class DeferredPicker : ReaderPlatform {
        val destination = CompletableDeferred<Result<String?>>()
        var pickerCount = 0
        var shareResult: Result<Unit> = Result.success(Unit)
        @Composable override fun ImmersiveEffect(orientation: ScreenOrientationSetting?) = Unit
        @Composable override fun KeepScreenOnEffect(enabled: Boolean) = Unit
        override suspend fun chooseImageDestination(image: ReaderExportImage): Result<String?> {
            pickerCount++
            return destination.await()
        }
        override suspend fun shareImage(image: ReaderExportImage) = shareResult
    }
}
