package com.rchdliang.copymanga_neo.multiplatform.ui.preview

import com.rchdliang.copymanga_neo.multiplatform.ui.components.RouteImageScope
import com.rchdliang.copymanga_neo.multiplatform.ui.components.routeImageModel
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import coil3.compose.AsyncImage
import com.rchdliang.copymanga_neo.multiplatform.ui.components.requireAppImageLoader
import com.rchdliang.copymanga_neo.multiplatform.resources.Res
import com.rchdliang.copymanga_neo.multiplatform.resources.preview_comic_cover

/** 直接绘制本地资源，静态预览不依赖 Coil 的异步状态或网络请求。 */
@Composable
fun PreviewImage(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    drawable: DrawableResource = Res.drawable.preview_comic_cover
) {
    Image(
        painter = painterResource(drawable),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}

/** 预览同步绘图；运行时原样沿用 Coil 的请求、占位和错误图片。 */
@Composable
fun PreviewAwareAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    previewDrawable: DrawableResource = Res.drawable.preview_comic_cover,
    placeholder: Painter? = null,
    error: Painter? = null,
    fallback: Painter? = error
) {
    if (LocalInspectionMode.current) {
        PreviewImage(contentDescription, modifier, contentScale, previewDrawable)
    } else {
        RouteImageScope {
            AsyncImage(
                imageLoader = requireAppImageLoader(),
                model = routeImageModel(model),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                placeholder = placeholder,
                error = error,
                fallback = fallback
            )

        }
    }
}
