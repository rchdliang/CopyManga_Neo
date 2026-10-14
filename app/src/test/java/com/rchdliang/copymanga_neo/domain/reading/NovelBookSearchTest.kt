package com.rchdliang.copymanga_neo.domain.reading

import com.rchdliang.copymanga_neo.domain.model.NovelBook
import com.rchdliang.copymanga_neo.domain.model.NovelReadingVolume
import com.rchdliang.copymanga_neo.domain.model.NovelSection
import com.rchdliang.copymanga_neo.domain.model.NovelVolume
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class NovelBookSearchTest {
    private fun volume(id: String, text: String): NovelReadingVolume {
        val document = NovelTextDocument.decode(text.toByteArray(), "UTF-8")
        return NovelReadingVolume(
            NovelBook("book", "示例", "", emptyList()),
            NovelVolume(id, "第" + id + "卷", id.toInt(), null, null),
            listOf(NovelSection("chapter", "章节", 1, 0, document.lines.size, null)),
            document,
            emptyMap(),
            true
        )
    }

    @Test
    fun currentVolumeIsImmediateAndOtherVolumesAreLoadedOnceWithResultsInBookOrder() = runBlocking {
        val current = volume("2", "风风\n风")
        val volumes =
            listOf(volume("1", "风"), current, volume("3", "风")).associateBy { it.volume.id }
        val loaded = mutableListOf<String>()
        val updates = mutableListOf<NovelBookSearchProgress>()
        val result = searchNovelVolumes(
            "风",
            current,
            volumes.values.map { it.volume },
            load = { loaded += it.id; Result.success(volumes.getValue(it.id)) },
            onProgress = { updates += it })
        assertEquals(listOf("1", "3"), loaded)
        assertEquals(listOf("1", "2", "2", "2", "3"), result.matches.map { it.volumeId })
        assertEquals(5, result.totalHits)
        assertEquals(4, result.paragraphs)
        assertTrue(result.complete)
        assertEquals("2", updates.first { it.completedVolumes == 1 }.matches.first().volumeId)
        assertEquals(3, result.completedVolumes)
    }

    @Test
    fun loadFailurePreservesPartialHitsAndStopsFurtherRequests() = runBlocking {
        val current = volume("2", "目标")
        val loaded = mutableListOf<String>()
        val result = searchNovelVolumes(
            "目标", current,
            listOf(
                current.volume.copy(id = "1", index = 1),
                current.volume,
                current.volume.copy(id = "3", index = 3)
            ),
            load = { loaded += it.id; Result.failure(IOException("离线")) }, onProgress = {})
        assertEquals(listOf("1"), loaded)
        assertEquals(1, result.totalHits)
        assertEquals(1, result.completedVolumes)
        assertFalse(result.complete)
        assertTrue(result.error!!.contains("离线"))
    }

    @Test
    fun cancellingSearchPreventsLoadingNextVolume() = runBlocking {
        val current = volume("1", "目标")
        val loaded = mutableListOf<String>()
        val worker = launch {
            searchNovelVolumes(
                "目标", current, listOf(current.volume, current.volume.copy(id = "2", index = 2)),
                load = { loaded += it.id; Result.success(volume(it.id, "目标")) },
                onProgress = { if (it.completedVolumes == 1) cancel() })
        }
        worker.join()
        assertTrue(worker.isCancelled)
        assertTrue(loaded.isEmpty())
    }
}
