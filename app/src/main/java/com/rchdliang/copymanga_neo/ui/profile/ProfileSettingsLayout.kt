package com.rchdliang.copymanga_neo.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rchdliang.copymanga_neo.domain.model.RequestMode

/** 设置各级页面共用标题、滚动与宽度约束，平板上保持可读的行长。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProfileSettingsScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0)
        )
        val body: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(ProfileListDefaults.Gap),
                    content = content
                )
            }
        }
        if (onRefresh == null) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { body() }
        } else {
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing, onRefresh = onRefresh, state = pullState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullState, isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            ) { body() }
        }
    }
}

@Composable
internal fun ProfileSectionTitle(title: String) {
    Text(
        title, modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary
    )
}

internal val RequestMode.profileLabel: String
    get() = if (this == RequestMode.WEB) "网页模式" else "App 模式"
