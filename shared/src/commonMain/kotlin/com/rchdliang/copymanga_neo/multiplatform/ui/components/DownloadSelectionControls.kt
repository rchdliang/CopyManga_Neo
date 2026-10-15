package com.rchdliang.copymanga_neo.multiplatform.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 漫画章节与小说分卷共用下载多选栏，所有操作数量使用调用方的单位。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadSelectionTopBar(
    selectedCount: Int,
    canSelectBetween: Boolean,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onSelectToLatest: () -> Unit,
    onUnselectToLatest: () -> Unit,
    onSelectToFirst: () -> Unit,
    onUnselectToFirst: () -> Unit,
    onSelectBetween: () -> Unit,
    onUnselectBetween: () -> Unit,
    onClearSelection: () -> Unit,
    unitLabel: String = "章",
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onExit) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "取消选择",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "已选 $selectedCount $unitLabel",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = "全选",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onInvertSelection) {
                Icon(
                    imageVector = Icons.Default.Deselect,
                    contentDescription = "反选",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                DropdownMenuPopup(
                    expanded = menuExpanded && LocalRouteActive.current,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShape(0, 3)
                    ) {
                        DownloadMenuGroupLabel {
                            Text(
                                "选中",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "选中本${unitLabel}到最新",
                                    color = LocalContentColor.current
                                )
                            },
                            onClick = {
                                onSelectToLatest()
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "选中第一${unitLabel}到本${unitLabel}",
                                    color = LocalContentColor.current
                                )
                            },
                            onClick = {
                                onSelectToFirst()
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("选中之间", color = LocalContentColor.current) },
                            onClick = {
                                onSelectBetween()
                                menuExpanded = false
                            },
                            enabled = canSelectBetween
                        )
                    }
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShape(1, 3)
                    ) {
                        DownloadMenuGroupLabel {
                            Text(
                                "取消选中",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "取消选中本${unitLabel}到最新",
                                    color = LocalContentColor.current
                                )
                            },
                            onClick = {
                                onUnselectToLatest()
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "取消选中第一${unitLabel}到本${unitLabel}",
                                    color = LocalContentColor.current
                                )
                            },
                            onClick = {
                                onUnselectToFirst()
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("取消选中之间", color = LocalContentColor.current) },
                            onClick = {
                                onUnselectBetween()
                                menuExpanded = false
                            },
                            enabled = canSelectBetween
                        )
                    }
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShape(2, 3)
                    ) {
                        DropdownMenuItem(
                            text = { Text("清空选择", color = LocalContentColor.current) },
                            onClick = {
                                onClearSelection()
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadManageBottomBar(
    downloadCount: Int,
    deleteCount: Int,
    cancelCount: Int,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
    unitLabel: String = "章"
) {
    val hasChanges = downloadCount > 0 || deleteCount > 0 || cancelCount > 0
    val parts = buildList {
        if (downloadCount > 0) add("下载 $downloadCount $unitLabel")
        if (cancelCount > 0) add("取消任务 $cancelCount $unitLabel")
        if (deleteCount > 0) add("删除本地 $deleteCount $unitLabel")
    }
    val label = when {
        parts.isEmpty() -> "没有变更"
        parts.size > 1 -> "应用下载更改"
        downloadCount > 0 -> "下载所选 $downloadCount $unitLabel"
        deleteCount > 0 -> "删除未选中的 $deleteCount $unitLabel"
        else -> "取消未选中的 $cancelCount ${unitLabel}下载"
    }
    Surface(
        modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (parts.size > 1) {
                Text(
                    parts.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth(),
                enabled = hasChanges,
                colors = if (deleteCount > 0) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.buttonColors()
            ) {
                Icon(
                    imageVector = when {
                        deleteCount > 0 -> Icons.Default.Delete
                        cancelCount > 0 && downloadCount == 0 -> Icons.Default.Close
                        else -> Icons.Default.Downloading
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(label, color = LocalContentColor.current)
            }
        }
    }
}
