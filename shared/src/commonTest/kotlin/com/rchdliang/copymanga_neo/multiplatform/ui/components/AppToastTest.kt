package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class AppToastTest {
    @Test
    fun refreshFailureIsShortAndOnlyQueuedOnceUntilDismissed() = runTest {
        val host = SnackbarHostState()
        val toast = ToastState(backgroundScope, host)

        toast.showToast("已保存")
        repeat(3) { toast.showToast("刷新失败") }
        toast.showToast("下载完成")
        runCurrent()
        val first = assertNotNull(host.currentSnackbarData)
        assertEquals("已保存", first.visuals.message)
        first.dismiss()
        runCurrent()

        val failure = assertNotNull(host.currentSnackbarData)
        assertEquals("刷新失败", failure.visuals.message)
        assertEquals(SnackbarDuration.Short, failure.visuals.duration)
        toast.showToast("刷新失败")
        runCurrent()
        assertSame(failure, host.currentSnackbarData)
        failure.dismiss()
        runCurrent()

        val next = assertNotNull(host.currentSnackbarData)
        assertEquals("下载完成", next.visuals.message)
        next.dismiss()
        runCurrent()
        assertNull(host.currentSnackbarData)

        toast.showToast("刷新失败")
        runCurrent()
        assertEquals("刷新失败", host.currentSnackbarData?.visuals?.message)
    }
}
