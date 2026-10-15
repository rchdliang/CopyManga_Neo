package com.rchdliang.copymanga_neo.data.export.portable

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.io.File
import kotlin.test.AfterTest
import kotlin.uuid.Uuid
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

class ComicArchiveWriterTest {
    private val temporary = Files.createTempDirectory("comic-export-regression").toFile()
    private val io = FileComicExportIo(temporary.absolutePath)
    @AfterTest fun cleanup() { temporary.deleteRecursively() }

    private fun page(name: String, bytes: ByteArray): ComicExportPage {
        val file = File(temporary, name).apply { writeBytes(bytes) }
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
        val target = File(temporary, "${Uuid.random()}.zip")
        runBlocking { io.write(unit, target.absolutePath, false, format, {}, {}, {}) }
        return ZipInputStream(target.inputStream()).use { input ->
            buildMap { while (true) { val entry = input.nextEntry ?: break; put(entry.name, input.readBytes()) } }
        }
    }

    @Test
    fun approvedReplacementPublishesArchiveAndCleanupCannotDeleteIt() = runBlocking {
        val target = File(temporary, "replace.zip").apply { writeText("original") }
        DesktopExportDestinations.approve(DesktopExportDestinations.capture(target.toPath()))
        val unit = exportUnits(plan(listOf(page("page.jpg", byteArrayOf(-1, -40, -1, 0)))), ComicExportFormat.ZIP).single()
        var active = ""
        val output = io.write(unit, target.absolutePath, false, ComicExportFormat.ZIP, { active = it }, {}, {})
        assertEquals(target.toURI().toString(), output.uri)
        assertNotEquals(output.uri, active)
        assertTrue(io.delete(active))
        assertTrue(target.exists())
        ZipInputStream(target.inputStream()).use { assertTrue(it.nextEntry != null) }
    }

    @Test
    fun failedReplacementPreservesOriginalAndRemovesStaging() = runBlocking {
        val target = File(temporary, "replace-failure.zip").apply { writeText("original") }
        DesktopExportDestinations.approve(DesktopExportDestinations.capture(target.toPath()))
        val unit = exportUnits(plan(listOf(page("page-failure.jpg", byteArrayOf(-1, -40, -1, 0)))), ComicExportFormat.ZIP).single()
        var active = ""
        assertFailsWith<IOException> {
            io.write(unit, target.absolutePath, false, ComicExportFormat.ZIP, { active = it }, { throw IOException("disk failure") }, {})
        }
        assertEquals("original", target.readText())
        assertFalse(File(java.net.URI(active)).exists())
        assertTrue(io.delete(active))
        assertEquals("original", target.readText())
    }

    @Test
    fun cancelledReplacementPreservesOriginal() = runBlocking {
        val target = File(temporary, "replace-cancel.zip").apply { writeText("original") }
        DesktopExportDestinations.approve(DesktopExportDestinations.capture(target.toPath()))
        val unit = exportUnits(plan(listOf(page("page-cancel.jpg", byteArrayOf(-1, -40, -1, 0)))), ComicExportFormat.ZIP).single()
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            io.write(unit, target.absolutePath, false, ComicExportFormat.ZIP, {}, { throw kotlinx.coroutines.CancellationException("cancel") }, {})
        }
        assertEquals("original", target.readText())
        assertTrue(temporary.listFiles().orEmpty().none { it.name.startsWith(".copymanga-export-") })
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
        assertContentEquals(jpg, entries.values.elementAt(0))
        assertContentEquals(webp, entries.values.elementAt(1))
        val manifest = entries.getValue("manifest.json").toString(Charsets.UTF_8)
        assertTrue(manifest.contains("漫画名字"))
        assertFalse(manifest.contains(temporary.absolutePath))
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
        assertFailsWith<IOException> {
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
            io.write(
                unit, File(temporary, "cancelled.zip").absolutePath, false,
                ComicExportFormat.ZIP, {},
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
    fun imageTypeUsesBytesAndRejectsHtmlErrors() {
        assertEquals("webp", imageExtension("RIFF0000WEBPpayload".toByteArray()))
        assertFailsWith<IOException> { imageExtension("<html>blocked</html>".toByteArray()) }
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
        val restored = Json.decodeFromString(ComicExportState.serializer(), Json.encodeToString(ComicExportState.serializer(), state))
        assertEquals(1, restored.plan!!.chapterCount)
        assertEquals(4L, restored.plan.bytes)
        assertEquals("comic-id", restored.outputs.single().id)
        assertEquals("task-1", restored.taskId)
        assertTrue(restored.running)
    }
}
