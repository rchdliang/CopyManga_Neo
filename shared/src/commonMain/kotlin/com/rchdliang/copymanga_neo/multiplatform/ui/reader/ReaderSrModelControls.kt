package com.rchdliang.copymanga_neo.multiplatform.ui.reader
import androidx.compose.runtime.*
import androidx.compose.material3.*
import com.rchdliang.copymanga_neo.domain.reading.*
import kotlinx.coroutines.launch

internal fun modelMiB(bytes: Long): String {
    val rounded = (bytes * 100 + 524288) / 1048576
    return "${rounded / 100}.${(rounded % 100).toString().padStart(2, '0')} MiB"
}
@Composable
internal fun ReaderSrModelControls(session: ReaderSrSession, content: @Composable ((SrExecutionMode) -> Unit, () -> Unit) -> Unit) {
    val store = session.models
    val state = store?.state?.collectAsState()?.value
    val navigate = LocalReaderSettingsNavigation.current
    val enable: (SrExecutionMode) -> Unit = { mode ->
        if (session.unavailableReason == null) {
            if (store == null || state == SrModelState.Ready || state is SrModelState.Downloading || state == SrModelState.Preparing)
                session.settings.value = session.settings.value.copy(enabled = true, mode = mode)
            else navigate(ReaderSettingsPage("下载超分模型？") {
                Text("需下载 ${modelMiB(store.downloadBytes)}，下载完成后启用。", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = {
                    session.settings.value = session.settings.value.copy(enabled = true, mode = mode)
                    store.startDownload()
                    navigate(null)
                }) { Text("下载并启用") }
                TextButton(onClick = { navigate(null) }) { Text("暂不") }
            })
        }
    }
    content(enable) {
        if (store != null) navigate(ReaderSettingsPage("模型管理") { ReaderModelManagement(session, store) })
    }
    if (store != null && session.unavailableReason == null) ReaderModelProgress(store) {
        // Retry is an explicit action. It never changes a later user choice to disable enhancement.
        store.startDownload()
    }
}
@Composable
private fun ReaderModelProgress(store: ReaderSrModels, retry: () -> Unit) {
    val state by store.state.collectAsState()
    when (val value = state) {
        SrModelState.Ready -> Text("模型已就绪", style = MaterialTheme.typography.bodySmall)
        SrModelState.Preparing -> { Text("正在准备模型…"); LinearProgressIndicator() }
        SrModelState.Cancelled -> { Text("已取消下载"); TextButton(onClick = retry) { Text("重新下载") } }
        is SrModelState.Downloading -> {
            Text(if (value.total > 0) "正在下载模型 · ${modelMiB(value.bytes)} / ${modelMiB(value.total)}" else "正在下载模型")
            if (value.total > 0) LinearProgressIndicator(progress = { (value.bytes.toFloat() / value.total).coerceIn(0f, 1f) }) else LinearProgressIndicator()
            TextButton(onClick = store::cancelDownload) { Text("取消下载") }
        }
        is SrModelState.Failed -> { Text("模型下载失败"); Text(value.message, style = MaterialTheme.typography.bodySmall); TextButton(onClick = retry) { Text("重试") } }
        else -> Text("模型未下载 · ${modelMiB(store.downloadBytes)}", style = MaterialTheme.typography.bodySmall)
    }
}
@Composable
private fun ReaderModelManagement(session: ReaderSrSession, store: ReaderSrModels) {
    val state by store.state.collectAsState()
    val navigate = LocalReaderSettingsNavigation.current
    val scope = rememberCoroutineScope()
    Text("waifu2x cunet 2× · ${ReaderSrModel.model.version}")
    ReaderModelProgress(store, store::startDownload)
    if (state == SrModelState.Ready) {
        Text("模型文件占用 ${modelMiB(store.downloadBytes)}", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { navigate(ReaderSettingsPage("删除模型？") {
            val deleteScope = rememberCoroutineScope()
            Text("删除后需要重新下载。")
            TextButton(onClick = {
                session.settings.value = session.settings.value.copy(enabled = false)
                deleteScope.launch { store.delete(); navigate(null) }
            }) { Text("删除") }
            TextButton(onClick = { navigate(null) }) { Text("保留") }
        }) }) { Text("删除模型") }
    }
}
