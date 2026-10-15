package com.rchdliang.copymanga_neo.multiplatform.ui.export

import androidx.compose.runtime.staticCompositionLocalOf
import com.rchdliang.copymanga_neo.domain.model.ComicExportLocation

/** The host owns the native document picker and any persistent access grant. */
interface ComicExportPlatform {
    /** A null successful result is user cancellation; failures must never look like saved exports. */
    suspend fun pickDestination(location: ComicExportLocation): Result<String?>
    fun formatFileSize(bytes: Long): String
}

val LocalComicExportPlatform = staticCompositionLocalOf<ComicExportPlatform?> { null }
