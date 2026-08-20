package com.rchdliang.copymanga_neo.ui.preview

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalInspectionMode
import com.rchdliang.copymanga_neo.R

/**
 * Compose Preview 不执行远程图片请求，预览时统一切换为项目内的占位图。
 *
 * 真机运行时会原样返回 [model]，不会改变 Coil 的网络或本地图片加载逻辑。
 */
@Composable
fun previewImageModel(
    model: Any?,
    @DrawableRes placeholder: Int = R.drawable.preview_comic_cover
): Any? = if (LocalInspectionMode.current) placeholder else model
