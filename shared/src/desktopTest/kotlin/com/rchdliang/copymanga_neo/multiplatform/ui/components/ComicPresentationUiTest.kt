package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.multiplatform.ui.downloads.DownloadingCard
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ComicPresentationUiTest {
    private val pending = ComicPresentationContext(
        ComicPresentationUiState(ready = true, blocked = listOf(Theme("Blocked", "blocked"))),
        { _, _ -> }, {}, {}, null, {})

    @Test fun pendingMetadataKeepsLocalCardAndRecordClickable() = runSkikoComposeUiTest {
        var comicClicks = 0
        var recordClicks = 0
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalComicPresentation provides pending) {
                    Column(Modifier.width(360.dp)) {
                        ComicCard("本地漫画", null, { comicClicks++ }, Modifier.width(120.dp),
                            comicPathWord = "local", allowPendingContent = true)
                        ReadingHistoryRow("本地记录", null, "", { recordClicks++ },
                            comicPathWord = "record", allowPendingContent = true) {
                            Text("阅读进度", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        onNodeWithText("本地漫画").performClick()
        onNodeWithText("本地记录").performClick()
        assertEquals(1, comicClicks)
        assertEquals(1, recordClicks)
    }

    @Test fun pendingMetadataDoesNotHideDownloadControls() = runSkikoComposeUiTest {
        var paused = false
        var cancelled = false
        val progress = DownloadProgressInfo("local", "下载漫画", null, 0, 1, 0f, 1, null, 0f, DownloadTaskStatus.DOWNLOADING)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalComicPresentation provides pending) {
                    DownloadingCard("下载漫画", null, progress, false, { paused = true }, { cancelled = true },
                        Modifier.width(400.dp))
                }
            }
        }
        onNodeWithContentDescription("暂停").performClick()
        onNodeWithContentDescription("取消下载").performClick()
        assertTrue(paused)
        assertTrue(cancelled)
    }
}
