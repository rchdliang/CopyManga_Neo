package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest

/** Keep cached/local covers visible during transitions without initiating a resource GET. */
@Composable
internal fun routeImageModel(model: Any?): Any? {
    val active = LocalRouteActive.current
    val context = LocalPlatformContext.current
    return remember(model, context, active) { routeImageModel(model, context, active) }
}

internal fun routeImageModel(model: Any?, context: PlatformContext, active: Boolean): Any? {
    if (active || model == null) return model
    return (if (model is ImageRequest) model.newBuilder(context) else ImageRequest.Builder(context).data(model))
        .diskCachePolicy(CachePolicy.READ_ONLY)
        .networkCachePolicy(CachePolicy.DISABLED)
        .build()
}

/**
 * Coil 3.6.3 Default equality omits cache policies; preview misses must retry on activation.
 * Verified against coil-compose-core/LocalAsyncImageModelEqualityDelegate.kt at tag 3.6.3.
 * Only equality changes: never alter memoryCacheKey/extras or diskCacheKey to force a retry.
 */
@OptIn(coil3.annotation.ExperimentalCoilApi::class)
internal object RouteImageModelEquality : coil3.compose.AsyncImageModelEqualityDelegate {
    private val base = coil3.compose.AsyncImageModelEqualityDelegate.Default
    override fun equals(self: Any?, other: Any?): Boolean = base.equals(self, other) &&
        (self !is ImageRequest || other !is ImageRequest ||
            (self.networkCachePolicy == other.networkCachePolicy && self.diskCachePolicy == other.diskCachePolicy))
    override fun hashCode(self: Any?): Int = if (self is ImageRequest)
        31 * (31 * base.hashCode(self) + self.networkCachePolicy.hashCode()) + self.diskCachePolicy.hashCode()
        else base.hashCode(self)
}

@OptIn(coil3.annotation.ExperimentalCoilApi::class)
@Composable
internal fun RouteImageScope(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        coil3.compose.LocalAsyncImageModelEqualityDelegate provides RouteImageModelEquality,
        content = content
    )
}
