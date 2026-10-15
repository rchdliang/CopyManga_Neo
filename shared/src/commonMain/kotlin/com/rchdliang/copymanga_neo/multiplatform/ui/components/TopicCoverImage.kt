package com.rchdliang.copymanga_neo.multiplatform.ui.components

import com.rchdliang.copymanga_neo.multiplatform.ui.components.RouteImageScope
import com.rchdliang.copymanga_neo.multiplatform.ui.components.routeImageModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.rchdliang.copymanga_neo.multiplatform.resources.Res
import com.rchdliang.copymanga_neo.multiplatform.resources.preview_topic_banner
import com.rchdliang.copymanga_neo.domain.model.ComicTopic
import com.rchdliang.copymanga_neo.multiplatform.ui.preview.PreviewImage

/** 专题封面优先加载原图；原图失败时只回退一次到接口返回的原始地址。 */
@Composable
fun TopicCoverImage(
    topic: ComicTopic,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    if (LocalInspectionMode.current) {
        PreviewImage(
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            drawable = Res.drawable.preview_topic_banner
        )
        return
    }

    val routeActive = LocalRouteActive.current
    val originalUrl = topic.cover
    val fullResolutionUrl = topic.fullResolutionCover
    var useOriginalUrl by remember(originalUrl, fullResolutionUrl) { mutableStateOf(false) }
    val imageUrl = if (useOriginalUrl) originalUrl else fullResolutionUrl
    val context = LocalPlatformContext.current
    val request = remember(context, imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .size(Size.ORIGINAL)
            .build()
    }
    RouteImageScope {
        AsyncImage(
            imageLoader = requireAppImageLoader(),
            model = routeImageModel(request),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier,
            onError = {
                if (routeActive && !useOriginalUrl && !originalUrl.isNullOrBlank() && imageUrl != originalUrl) {
                    useOriginalUrl = true
                }
            }
        )
    }
}
