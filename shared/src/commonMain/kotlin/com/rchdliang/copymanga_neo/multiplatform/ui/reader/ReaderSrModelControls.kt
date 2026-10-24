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
internal fun ReaderSrModelControls(session: ReaderSrSession, content: @Composable ((SrExecutionMode) -> Unit, () -> Unit, @Composable () -> Unit) -> Unit) {
    val store = session.models
    val state = store?.state?.collectAsState()?.value
    val navigate = LocalReaderSettingsNavigation.current
    val enable: (SrExecutionMode) -> Unit = { mode ->
        if (session.unavailableReason == null && state != SrModelState.Deleting) {
            if (store == null || state == SrModelState.Ready || state is SrModelState.Downloading || state == SrModelState.Preparing)
                session.settings.value = session.settings.value.copy(enabled = true, mode = mode)
            else navigate(ReaderSettingsPage("下载超分模型？") {
                ReaderModelDownloadConfirmation(store.downloadBytes, onConfirm = {
                    session.settings.value = session.settings.value.copy(enabled = true, mode = mode)
                    store.startDownload(); navigate(null)
                }, onCancel = { navigate(null) })
            })
        }
    }
    val scope = rememberCoroutineScope()
    content(enable, {
        if (store != null) navigate(ReaderSettingsPage("模型管理") { ReaderModelManagement(session, store) })
    }, {
        if (store != null && session.unavailableReason == null && state != SrModelState.Ready && state != SrModelState.Missing)
            ReaderModelProgress(store) { scope.launch { store.delete() } }
    })
}
@Composable
internal fun ReaderModelProgress(store: ReaderSrModels, retryDelete: () -> Unit) {
    val state by store.state.collectAsState()
    when (val value = state) {
        SrModelState.Ready -> Text("模型已就绪", style = MaterialTheme.typography.bodySmall)
        SrModelState.Preparing -> { Text("正在准备模型…"); LinearProgressIndicator() }
        SrModelState.Cancelled -> { Text("已取消下载"); TextButton(onClick = store::startDownload) { Text("重新下载") } }
        SrModelState.Deleting -> {
            Text("正在删除…")
            LinearProgressIndicator()
            Text("等待当前增强结束，返回后继续删除。", style = MaterialTheme.typography.bodySmall)
        }
        is SrModelState.Downloading -> {
            Text(if (value.total > 0) "正在下载模型 · ${modelMiB(value.bytes)} / ${modelMiB(value.total)}" else "正在下载模型")
            if (value.total > 0) LinearProgressIndicator(progress = { (value.bytes.toFloat() / value.total).coerceIn(0f, 1f) }) else LinearProgressIndicator()
            TextButton(onClick = store::cancelDownload) { Text("取消下载") }
        }
        is SrModelState.Failed -> {
            Text(if (value.operation == SrModelOperation.DELETE) "模型删除失败" else "模型下载失败")
            Text(value.message, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = if (value.operation == SrModelOperation.DELETE) retryDelete else store::startDownload) { Text("重试") }
        }
        else -> Text("模型未下载 · ${modelMiB(store.downloadBytes)}", style = MaterialTheme.typography.bodySmall)
    }
}
@Composable
internal fun ReaderModelManagement(session: ReaderSrSession, store: ReaderSrModels) {
    val state by store.state.collectAsState()
    val navigate = LocalReaderSettingsNavigation.current
    val scope = rememberCoroutineScope()
    Text("waifu2x cunet 2× · ${ReaderSrModel.model.version}")
    ReaderModelProgress(store) { scope.launch { store.delete() } }
    if (state == SrModelState.Ready) {
        Text("模型文件占用 ${modelMiB(store.downloadBytes)}", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { navigate(ReaderSettingsPage("删除模型？") {
            ReaderModelDeleteConfirmation(session, store)
        }) }) { Text("删除模型") }
    }
}

@Composable
internal fun ReaderModelDeleteConfirmation(session: ReaderSrSession, store: ReaderSrModels) {
    val state by store.state.collectAsState()
    val navigate = LocalReaderSettingsNavigation.current
    val scope = rememberCoroutineScope()
    var requested by remember { mutableStateOf(false) }
    val remove = {
        requested = true
        session.settings.value = session.settings.value.copy(enabled = false)
        scope.launch {
            store.delete()
            if (store.state.value != SrModelState.Missing && store.state.value != SrModelState.Deleting) requested = false
        }
        Unit
    }
    LaunchedEffect(state, requested) {
        // 仅成功返回；失败留在当前页，返回离开也不会被后台完成事件拉回。
        if (requested && state == SrModelState.Missing) navigate(null)
    }
    Text("删除后需要重新下载。")
    when {
        state == SrModelState.Deleting || (state as? SrModelState.Failed)?.operation == SrModelOperation.DELETE ->
            ReaderModelProgress(store, remove)
        !requested -> TextButton(onClick = remove) { Text("删除") }
    }
    if (!requested && state != SrModelState.Deleting) TextButton(onClick = { navigate(null) }) { Text("保留") }
}

@Composable
internal fun ReaderModelDownloadConfirmation(bytes: Long, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Text("需下载 ${modelMiB(bytes)}，下载完成后启用。", style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = onConfirm) { Text("下载并启用") }
    TextButton(onClick = onCancel) { Text("暂不") }
}
