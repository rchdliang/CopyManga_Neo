package com.rchdliang.copymanga_neo.multiplatform.ui.reader
import androidx.compose.runtime.Composable
import coil3.ImageLoader
import com.rchdliang.copymanga_neo.domain.reading.*
@Composable
internal actual fun rememberReaderSrPlatform(loader: ImageLoader): ReaderSrPlatform = UnsupportedReaderSrPlatform
