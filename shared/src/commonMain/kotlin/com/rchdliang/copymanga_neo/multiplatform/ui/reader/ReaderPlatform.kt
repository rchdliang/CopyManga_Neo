package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import okio.Path.Companion.toPath
import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage
import com.rchdliang.copymanga_neo.multiplatform.ui.comments.CommentsViewModel

/** Reader OS effects are scoped and restored by each host when the reader leaves composition. */
interface ReaderPlatform {
    /** Android: hide status bars and apply the per-comic orientation. Desktop: reader window policy. */
    @Composable
    fun ImmersiveEffect(orientation: ScreenOrientationSetting?)

    /** Restore the previous awake state on disposal; false must not override another screen's policy. */
    @Composable
    fun KeepScreenOnEffect(enabled: Boolean)

    /** Null is a user cancellation. Destination is an opaque platform handle understood by ReaderImageRepository. */
    suspend fun chooseImageDestination(image: ReaderExportImage): Result<String?>
    suspend fun shareImage(image: ReaderExportImage): Result<Unit>
}

/**
 * Application-scoped wiring, explicitly passed by the route host.
 * Factories must retain models per chapter for this reader route, and clear them with its ViewModelStore.
 * The image loader factory must retain request authentication, risk gates and the selected timeout.
 */
data class ReaderUiServices(
    val platform: ReaderPlatform,
    val imageLoader: (timeoutSeconds: Int) -> ImageLoader,
    val imageActions: ReaderImageActionsViewModel,
    val chapterComments: (chapterId: String) -> ReaderChapterCommentsViewModel,
    // Modal open/close resets its model. Never share it with retained inline drafts.
    val modalComments: () -> CommentsViewModel,
    val inlineComments: (chapterId: String) -> CommentsViewModel
)

/** Keys are local to a reader route; the modal lifecycle must not clear inline drafts. */
internal object ReaderCommentModelKeys {
    const val modal = "reader-modal"
    fun inline(chapterId: String) = "reader-inline:$chapterId"
}

/** A Windows drive letter is a file path, not a URI scheme; preserve browser object/data URLs. */
fun readerImageData(source: String): Any {
    val windowsPath = Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(source) || source.startsWith("\\\\")
    val uri = Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(source)
    return if (uri && !windowsPath) source else source.toPath()
}
