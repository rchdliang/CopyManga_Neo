package com.rchdliang.copymanga_neo.data.export.portable

import java.io.IOException
import java.nio.file.Files
import kotlin.test.*

class DesktopExportDestinationsTest {
    private val directory = Files.createTempDirectory("desktop-export-destination")
    @AfterTest fun cleanup() { directory.toFile().deleteRecursively() }

    @Test fun existingFileNeedsOneUseApproval() {
        val target = Files.writeString(directory.resolve("book.zip"), "original")
        assertFailsWith<IOException> { DesktopExportDestinations.claim(target) }
        DesktopExportDestinations.approve(DesktopExportDestinations.capture(target))
        DesktopExportDestinations.claim(target)
        assertFailsWith<IOException> { DesktopExportDestinations.claim(target) }
        assertEquals("original", Files.readString(target))
    }

    @Test fun changesDuringConfirmationAreRejected() {
        val target = Files.writeString(directory.resolve("book.zip"), "original")
        val pending = DesktopExportDestinations.capture(target)
        Files.writeString(target, "changed by another application")
        assertFailsWith<IOException> { DesktopExportDestinations.approve(pending) }
    }

    @Test fun changesDuringExportAreNotOverwritten() {
        val target = Files.writeString(directory.resolve("book.zip"), "original")
        DesktopExportDestinations.approve(DesktopExportDestinations.capture(target))
        val reservation = DesktopExportDestinations.claim(target)
        val staging = Files.writeString(directory.resolve("staging"), "new archive")
        Files.writeString(target, "new user data")
        assertFailsWith<IOException> { reservation.publish(staging) }
        assertEquals("new user data", Files.readString(target))
        assertEquals("new archive", Files.readString(staging))
    }

    @Test fun replacedFileWithMatchingSizeAndModificationTimeIsRejected() {
        val target = Files.writeString(directory.resolve("book.zip"), "original")
        val attributes = Files.readAttributes(target, java.nio.file.attribute.BasicFileAttributes::class.java)
        DesktopExportDestinations.approve(DesktopExportDestinations.capture(target))
        val reservation = DesktopExportDestinations.claim(target)
        // Keep the old file alive so its ID cannot be recycled for the replacement.
        Files.move(target, directory.resolve("old.zip"))
        Files.writeString(target, "new data")
        Files.getFileAttributeView(target, java.nio.file.attribute.BasicFileAttributeView::class.java)
            .setTimes(attributes.lastModifiedTime(), null, attributes.creationTime())
        val staging = Files.writeString(directory.resolve("staging"), "new archive")
        assertFailsWith<IOException> { reservation.publish(staging) }
        assertEquals("new data", Files.readString(target))
        assertEquals("new archive", Files.readString(staging))
    }

    @Test fun newlyAppearingDestinationIsNotOverwritten() {
        val target = directory.resolve("book.zip")
        DesktopExportDestinations.approve(DesktopExportDestinations.capture(target))
        val reservation = DesktopExportDestinations.claim(target)
        val staging = Files.writeString(directory.resolve("staging"), "new archive")
        Files.writeString(target, "another application")
        assertFailsWith<IOException> { reservation.publish(staging) }
        assertEquals("another application", Files.readString(target))
    }
}
