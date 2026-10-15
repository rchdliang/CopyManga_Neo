package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.ComicTopic
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme

/** 专题卡片独立预览，使用本地横幅资源。 */
@Preview(name = "组件 - 专题卡片", showBackground = true, widthDp = 360)
@Composable
private fun TopicCardPreview() {
    CopyManga_NeoTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            TopicCard(
                topic = ComicTopic(
                    pathWord = "preview-summer",
                    title = "夏番漫畫2026 必看熱門漫畫推薦第41期",
                    cover = null,
                    brief = null
                ),
                onClick = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

/** 完整横幅配独立标题，避免图片内文案遮挡专题主题。 */
@Composable
fun TopicCard(topic: ComicTopic, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column {
            TopicCoverImage(
                topic = topic,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1920f / 412f)
            )
            Text(
                topic.title.replace(Regex(" +"), "\n"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                minLines = 2,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
