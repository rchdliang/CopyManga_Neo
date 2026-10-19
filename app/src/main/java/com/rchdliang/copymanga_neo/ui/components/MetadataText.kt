package com.rchdliang.copymanga_neo.ui.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import com.rchdliang.copymanga_neo.domain.text.TraditionalChineseConverter
import com.rchdliang.copymanga_neo.domain.repository.ChineseDictionaryRepository

private val LocalMetadataConverter = compositionLocalOf<TraditionalChineseConverter?> { null }

/** 仅用于元数据展示，禁止将结果写回模型或用于黑名单、请求参数和列表标识。 */
@Composable
fun metadataText(original: String): String {
    val converter = LocalMetadataConverter.current
    return remember(original, converter) { converter?.convert(original) ?: original }
}

/** 开启时恢复本地词典；缺失时自动下载，关闭开关立即恢复原文。 */
@Composable
fun MetadataTextHost(
    enabled: Boolean,
    dictionary: ChineseDictionaryRepository,
    content: @Composable () -> Unit
) {
    val converter by dictionary.converter.collectAsState()
    LaunchedEffect(enabled, dictionary) {
        if (enabled) {
            dictionary.install().onFailure { error ->
                Log.w("MetadataText", "本地繁转简词典不可用", error)
            }
        }
    }
    CompositionLocalProvider(
        LocalMetadataConverter provides if (enabled) converter else null,
        content = content
    )
}
