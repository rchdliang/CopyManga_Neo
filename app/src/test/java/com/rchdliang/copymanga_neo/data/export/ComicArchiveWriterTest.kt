package com.rchdliang.copymanga_neo.data.export

import com.google.gson.Gson
import com.rchdliang.copymanga_neo.domain.model.ComicExportChapter
import com.rchdliang.copymanga_neo.domain.model.ComicExportComic
import com.rchdliang.copymanga_neo.domain.model.ComicExportFormat
import com.rchdliang.copymanga_neo.domain.model.ComicExportOutput
import com.rchdliang.copymanga_neo.domain.model.ComicExportPage
import com.rchdliang.copymanga_neo.domain.model.ComicExportPlan
import com.rchdliang.copymanga_neo.domain.model.ComicExportState
import com.rchdliang.copymanga_neo.domain.model.ComicExportStatus
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

class ComicArchiveWriterTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun page(name: String, bytes: ByteArray): ComicExportPage {
        val file = temporary.newFile(name).apply { writeBytes(bytes) }
        return ComicExportPage(file.absolutePath, bytes.size.toLong(), imageExtension(bytes))
    }

    private fun plan(
        pages: List<ComicExportPage>,
        groups: List<String> = listOf("正篇")
    ): ComicExportPlan =
        ComicExportPlan(
            listOf(
                ComicExportComic(
                    "comic-id",
                    "漫画名字",
                    "作者",
                    groups.mapIndexed { index, group ->
                        ComicExportChapter("chapter-$index", "第1话", 1, group, pages)
                    })
            )
        )

    private fun archive(unit: ExportUnit, format: ComicExportFormat): Map<String, ByteArray> {
        val output = ByteArrayOutputStream()
        runBlocking { ComicArchiveWriter.write(unit, format, output, {}, {}) }
        return ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { input ->
            buildMap {
                while (true) {
                    val entry = input.nextEntry ?: break; put(entry.name, input.readBytes())
                }
            }
        }
    }

    @Test
    fun zipKeepsOriginalBytesAndPageOrderAndDoesNotLeakLocalPaths() {
        val jpg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 3, 2, 1)
        val webp = "RIFF0000WEBPoriginal".toByteArray()
        val plan = plan(listOf(page("z-last.jpg", jpg), page("a-first.webp", webp)))
        val entries =
            archive(exportUnits(plan, ComicExportFormat.ZIP).single(), ComicExportFormat.ZIP)
        assertEquals(
            listOf("00001.jpg", "00002.webp", "manifest.json"),
            entries.keys.map { it.substringAfterLast('/') })
        assertArrayEquals(jpg, entries.values.elementAt(0))
        assertArrayEquals(webp, entries.values.elementAt(1))
        val manifest = entries.getValue("manifest.json").toString(Charsets.UTF_8)
        assertTrue(manifest.contains("漫画名字"))
        assertFalse(manifest.contains(temporary.root.absolutePath))
    }

    @Test
    fun cbzCreatesOneFlatArchivePerChapter() {
        val plan =
            plan(listOf(page("one.jpg", byteArrayOf(-1, -40, -1, 0))), listOf("正篇", "番外"))
        val units = exportUnits(plan, ComicExportFormat.CBZ)
        assertEquals(2, units.size)
        assertTrue(units.all { it.name.endsWith(".cbz") })
        val entries = archive(units.first(), ComicExportFormat.CBZ)
        assertEquals(listOf("00001.jpg", "manifest.json"), entries.keys.toList())
    }

    @Test
    fun sanitizedGroupsAndSameNamedChaptersDoNotCollide() {
        val plan = plan(listOf(page("one.jpg", byteArrayOf(-1, -40, -1, 0))), listOf("a/b", "a:b"))
        val entries =
            archive(exportUnits(plan, ComicExportFormat.ZIP).single(), ComicExportFormat.ZIP)
        val imageNames = entries.keys.filter { it.endsWith(".jpg") }
        assertEquals(2, imageNames.size)
        assertNotEquals(imageNames[0], imageNames[1])
        assertEquals(2, imageNames.map { it.substringBefore('/') }.distinct().size)
    }

    @Test
    fun longUnicodeOutputNamesFitFilesystemLimitsWithoutSplittingCharacters() {
        val name = exportFileName("漫画😀".repeat(100), "cbz")
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 220)
        assertTrue(name.endsWith(".cbz"))
        assertFalse(name.contains('\uFFFD'))
        assertNotEquals(
            exportFileName("x".repeat(300) + "A", "zip"),
            exportFileName("x".repeat(300) + "B", "zip")
        )
    }

    @Test
    fun changedSourceFailsInsteadOfProducingSuccessfulArchive() {
        val page = page("one.jpg", byteArrayOf(-1, -40, -1, 0))
        java.io.File(page.source).appendBytes(byteArrayOf(1))
        assertThrows(IOException::class.java) {
            archive(
                exportUnits(plan(listOf(page)), ComicExportFormat.ZIP).single(),
                ComicExportFormat.ZIP
            )
        }
    }

    @Test
    fun cancelledArchiveDoesNotReportSuccessfulCompletion() = runBlocking {
        val bytes = ByteArray(1024 * 1024).apply { this[0] = -1; this[1] = -40; this[2] = -1 }
        val unit =
            exportUnits(plan(listOf(page("large.jpg", bytes))), ComicExportFormat.ZIP).single()
        var finished = false
        var copied = 0L
        val job = launch {
            ComicArchiveWriter.write(
                unit,
                ComicExportFormat.ZIP,
                ByteArrayOutputStream(),
                { count ->
                    copied += count
                    currentCoroutineContext().cancel()
                },
                {})
            finished = true
        }
        job.join()
        assertFalse(finished)
        assertTrue(copied in 1 until bytes.size.toLong())
    }

    @Test
    fun headerReaderHandlesShortReadsAndUsesActualFormat() {
        val bytes = "RIFF0000WEBPpayload".toByteArray()
        val input = object : InputStream() {
            var index = 0
            override fun read(): Int =
                if (index == bytes.size) -1 else bytes[index++].toInt() and 255

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val value = read()
                if (value < 0) return -1
                buffer[offset] = value.toByte()
                return 1
            }
        }
        assertEquals("webp", imageExtension(readImageHeader(input)))
        assertThrows(IOException::class.java) { imageExtension("<html>blocked</html>".toByteArray()) }
    }

    @Test
    fun taskSnapshotRoundTripKeepsCountsAndCompletedOutputIds() {
        val plan = plan(listOf(page("one.jpg", byteArrayOf(-1, -40, -1, 0))))
        val state = ComicExportState(
            taskId = "task-1",
            status = ComicExportStatus.RUNNING, plan = plan,
            outputs = listOf(
                ComicExportOutput(
                    "comic-id",
                    "content://test/1",
                    "one.zip",
                    "application/zip"
                )
            )
        )
        val gson = Gson()
        val restored = gson.fromJson(gson.toJson(state), ComicExportState::class.java)
        assertEquals(1, restored.plan!!.chapterCount)
        assertEquals(4L, restored.plan.bytes)
        assertEquals("comic-id", restored.outputs.single().id)
        assertEquals("task-1", restored.taskId)
        assertTrue(restored.running)
    }
}
