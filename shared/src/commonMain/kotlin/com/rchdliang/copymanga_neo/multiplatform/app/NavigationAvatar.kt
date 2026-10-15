package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.rchdliang.copymanga_neo.multiplatform.ui.preview.PreviewAwareAsyncImage

/** The rail uses surfaceContainer, so its fallback glyph uses onSurface in either theme. */
@Composable
internal fun NavigationAvatar(loggedIn: Boolean, avatar: String?, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.onSurface
    if (!loggedIn || avatar.isNullOrBlank()) {
        Icon(Icons.Filled.AccountCircle, "用户头像", modifier, tint = tint)
    } else {
        // Only tint the loading/error painter. Applying a filter to AsyncImage itself would
        // incorrectly recolor the member's photo after the request succeeds.
        val vector = rememberVectorPainter(Icons.Filled.AccountCircle)
        val fallback = remember(vector, tint) {
            object : Painter() {
                override val intrinsicSize = vector.intrinsicSize
                override fun DrawScope.onDraw() {
                    with(vector) { draw(size, colorFilter = ColorFilter.tint(tint)) }
                }
            }
        }
        PreviewAwareAsyncImage(
            model = avatar, contentDescription = "用户头像", modifier = modifier,
            placeholder = fallback, error = fallback, fallback = fallback
        )
    }
}
