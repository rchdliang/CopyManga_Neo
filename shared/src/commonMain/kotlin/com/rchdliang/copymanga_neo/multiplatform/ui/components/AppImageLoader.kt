package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext

/** Scoped per application/window: images cannot silently bypass that graph's auth/proxy/risk client. */
val LocalAppImageLoader = staticCompositionLocalOf<ImageLoader?> { null }

@Composable
fun requireAppImageLoader(): ImageLoader = LocalAppImageLoader.current ?: run {
    check(LocalInspectionMode.current) {
        "Provide the application's authenticated ImageLoader before rendering shared image content."
    }
    // Static preview paths normally draw local resources; no global fallback is permitted at runtime.
    SingletonImageLoader.get(LocalPlatformContext.current)
}
