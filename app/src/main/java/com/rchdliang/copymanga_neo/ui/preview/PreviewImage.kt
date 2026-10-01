package com.rchdliang.copymanga_neo.ui.preview

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.rchdliang.copymanga_neo.R

/** 直接绘制本地资源，静态预览不依赖 Coil 的异步状态或网络请求。 */
@Composable
fun PreviewImage(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    @DrawableRes drawable: Int = R.drawable.preview_comic_cover
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
    @DrawableRes previewDrawable: Int = R.drawable.preview_comic_cover,
    placeholder: Painter? = null,
    error: Painter? = null,
    fallback: Painter? = error
) {
    if (LocalInspectionMode.current) {
        PreviewImage(contentDescription, modifier, contentScale, previewDrawable)
    } else {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            placeholder = placeholder,
            error = error,
            fallback = fallback
        )
    }
}
