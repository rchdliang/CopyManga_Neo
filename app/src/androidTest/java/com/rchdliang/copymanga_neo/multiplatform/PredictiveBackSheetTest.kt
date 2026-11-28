package com.rchdliang.copymanga_neo.multiplatform

import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inspector.WindowInspector
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.findViewTreeNavigationEventDispatcherOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.rchdliang.copymanga_neo.data.download.DownloadNotifications
import com.rchdliang.copymanga_neo.data.export.ComicExportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class PredictiveBackSheetTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun disabledPreviewDismissesOnlyOnDialogCommitAndKeepsDownloadsOpen() = runBlocking {
        val target = instrumentation.targetContext
        val settings = withTimeout(30_000) { target.sharedContainer.awaitGraph().repositories.appSettings }
        val original = withTimeout(15_000) { settings.settings.first() }
        val launcher = requireNotNull(target.packageManager.getLaunchIntentForPackage(target.packageName)?.component)
        var launchedActivity: AndroidSharedActivity? = null
        try {
            settings.setAutoCheckUpdates(false).getOrThrow()
            settings.setPredictiveBackEnabled(false).getOrThrow()
            val startCommand = "am start -W -n ${launcher.flattenToString()}"
            startFromShell("$startCommand --activity-clear-task -a ${DownloadNotifications.OPEN_DOWNLOADS}")
            val activity = withTimeout(15_000) {
                withContext(Dispatchers.Main) {
                    val monitor = ActivityLifecycleMonitorRegistry.getInstance()
                    fun resumed() = monitor.getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<AndroidSharedActivity>().singleOrNull()
                    var current = resumed()
                    while (current == null) {
                        delay(50)
                        current = resumed()
                    }
                    current
                }
            }
            launchedActivity = activity
            await("下载页面未打开") { boundsOf("已下载") != null }
            // A stale notification opens the real export sheet in the existing downloads activity.
            startFromShell("$startCommand --activity-single-top -a ${ComicExportService.RETRY} " +
                "--es ${ComicExportService.TASK_ID} missing-instrumentation-task")
            val sheetText = "导出任务已更新，请重新选择要导出的漫画"
            val initial = settledBoundsOf(sheetText)
            val input = DirectNavigationEventInput()
            withContext(Dispatchers.Main) {
                val dialog = WindowInspector.getGlobalWindowViews().single { view ->
                    view !== activity.window.decorView && view.hasWindowFocus() &&
                        view.findViewTreeNavigationEventDispatcherOwner() != null
                }
                val owner = requireNotNull(dialog.findViewTreeNavigationEventDispatcherOwner())
                assertNotSame(activity.navigationEventDispatcher, owner.navigationEventDispatcher)
                owner.navigationEventDispatcher.addInput(input)
            }
            val start = NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT)
            val progress = NavigationEvent(swipeEdge = NavigationEvent.EDGE_LEFT, progress = 0.8f)
            withContext(Dispatchers.Main) {
                input.backStarted(start)
                input.backProgressed(progress)
            }
            instrumentation.uiAutomation.waitForIdle(250, 10_000)
            assertEquals("未提交时面板应保持原位", initial, boundsOf(sheetText))
            withContext(Dispatchers.Main) { input.backCancelled() }
            instrumentation.uiAutomation.waitForIdle(250, 10_000)
            assertEquals("取消后面板应保持打开", initial, boundsOf(sheetText))
            withContext(Dispatchers.Main) {
                input.backStarted(start)
                input.backProgressed(progress)
                input.backCompleted()
            }
            await("提交后应只关闭面板，保留下载页") {
                boundsOf(sheetText) == null && boundsOf("已下载") != null
            }
        } finally {
            try {
                withContext(Dispatchers.Main) { launchedActivity?.finish() }
            } finally {
                settings.setPredictiveBackEnabled(original.predictiveBackEnabled).getOrThrow()
                settings.setAutoCheckUpdates(original.autoCheckUpdates).getOrThrow()
            }
        }
    }

    private fun startFromShell(command: String) {
        // The instrumentation UID cannot start a background activity on some Android builds.
        val pipe = instrumentation.uiAutomation.executeShellCommand(command)
        val task = FutureTask {
            ParcelFileDescriptor.AutoCloseInputStream(pipe).bufferedReader().use { it.readText() }
        }
        Thread(task, "test-activity-launch").apply { isDaemon = true; start() }
        try {
            val output = task.get(20, TimeUnit.SECONDS)
            check("Status: ok" in output) { "Activity 启动失败：$output" }
        } finally {
            pipe.close()
            task.cancel(true)
        }
    }

    private fun boundsOf(text: String): Rect? {
        instrumentation.uiAutomation.clearCache()
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (node.isVisibleToUser && node.text?.contains(text) == true) {
                return Rect().apply { node.getBoundsInScreen(this) }
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let(pending::addLast)
        }
        return null
    }

    private fun settledBoundsOf(text: String): Rect {
        var previous: Rect? = null
        var lastChange = SystemClock.uptimeMillis()
        await("面板入场动画未完成") {
            val current = boundsOf(text)
            if (previous != current) {
                previous = current
                lastChange = SystemClock.uptimeMillis()
            }
            current != null && SystemClock.uptimeMillis() - lastChange >= 400
        }
        return requireNotNull(previous)
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(60)
        }
        fail(message)
    }
}
