package com.rchdliang.copymanga_neo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.rchdliang.copymanga_neo.R
import com.rchdliang.copymanga_neo.domain.model.ComicTopic
import com.rchdliang.copymanga_neo.ui.preview.PreviewImage

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
            drawable = R.drawable.preview_topic_banner
        )
        return
    }

    val originalUrl = topic.cover
    val fullResolutionUrl = topic.fullResolutionCover
    var useOriginalUrl by remember(originalUrl, fullResolutionUrl) { mutableStateOf(false) }
    val imageUrl = if (useOriginalUrl) originalUrl else fullResolutionUrl
    val context = LocalContext.current
    val request = remember(context, imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .size(Size.ORIGINAL)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier,
        onError = {
            if (!useOriginalUrl && !originalUrl.isNullOrBlank() && imageUrl != originalUrl) {
                useOriginalUrl = true
            }
        }
    )
}
