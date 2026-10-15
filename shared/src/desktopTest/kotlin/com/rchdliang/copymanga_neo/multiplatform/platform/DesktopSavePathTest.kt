package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.data.export.portable.DesktopExportDestinations
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSavePathTest {
    @Test fun bareNamesKeepTheSuggestedFormatIncludingUnmappedMimeTypes() {
        val parent = Path.of("folder.with.dots")
        listOf(
            Triple("chapter", "original.cbz", "chapter.cbz"),
            Triple("picture", "original.png", "picture.png"),
            Triple("settings", "original.json", "settings.json"),
            Triple("logs", "original.jsonl", "logs.jsonl"),
            Triple("picture", "original.heic", "picture.heic"),
            Triple("picture", "original.avif", "picture.avif"),
            Triple("chapter", "old/path/original.zip", "chapter.zip"),
            Triple("chapter", "old\\path\\original.zip", "chapter.zip"),
        ).forEach { (chosen, suggested, expected) ->
            assertEquals(parent.resolve(expected), resolveDesktopSavePath(parent.resolve(chosen), suggested))
        }
    }

    @Test fun explicitSuffixesAndDotfilesArePreserved() {
        listOf("picture.jpeg", "picture.JPG", "chapter.custom", ".hidden", "chapter.").forEach { chosen ->
            val selected = Path.of(chosen)
            assertEquals(selected, resolveDesktopSavePath(selected, "original.png"))
        }
    }

    @Test fun suggestionsWithoutASuffixDoNotInventOne() {
        val selected = Path.of("chapter")
        listOf("original", ".hidden", "original.", "parent.with.dots/original").forEach { suggested ->
            assertEquals(selected, resolveDesktopSavePath(selected, suggested))
        }
    }

    @Test fun resolvedExistingArchiveNeedsApprovalAndRetainsChangeProtection() {
        val directory = Files.createTempDirectory("desktop-save-path")
        try {
            val original = Files.writeString(directory.resolve("book.zip"), "original")
            val chosen = directory.resolve("book")
            val resolved = resolveDesktopSavePath(chosen, "suggested.zip")
            assertEquals(original, resolved)
            assertTrue(Files.exists(resolved)) // The chooser must confirm this final target.
            assertFailsWith<IOException> { DesktopExportDestinations.claim(resolved) }
            assertEquals("original", Files.readString(original))

            DesktopExportDestinations.approve(DesktopExportDestinations.capture(resolved))
            val reservation = DesktopExportDestinations.claim(resolved)
            val staging = Files.writeString(directory.resolve("staging"), "new archive")
            Files.writeString(original, "changed after approval")
            assertFailsWith<IOException> { reservation.publish(staging) }
            assertEquals("changed after approval", Files.readString(original))
            assertEquals("new archive", Files.readString(staging))
            assertFalse(Files.exists(chosen))
        } finally { directory.toFile().deleteRecursively() }
    }
}
