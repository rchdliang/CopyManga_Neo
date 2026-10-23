package com.rchdliang.copymanga_neo.desktop

import java.awt.image.BaseMultiResolutionImage
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/** Rendered directly from SVG at each physical size, including common Windows DPI scales. */
internal class DesktopAppIcons private constructor(val variants: List<BufferedImage>) {
    // AWT asks for 16 logical pixels, then selects the physical variant for the screen's DPI.
    val trayImage = BaseMultiResolutionImage(*variants.toTypedArray())

    companion object {
        fun load(): DesktopAppIcons = DesktopAppIcons(
            listOf(16, 20, 24, 28, 32, 36, 40, 48, 56, 64, 72, 80, 96, 128, 256, 512).map { size ->
                val path = if (size == 512) "/copymanga-neo.png" else "/icons/copymanga-neo-$size.png"
                val stream = checkNotNull(DesktopAppIcons::class.java.getResourceAsStream(path)) { "Missing app icon: $path" }
                stream.use { checkNotNull(ImageIO.read(it)) { "Invalid app icon: $path" } }
            }
        )
    }
}
