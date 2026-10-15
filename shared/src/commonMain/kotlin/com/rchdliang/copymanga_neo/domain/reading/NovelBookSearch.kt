package com.rchdliang.copymanga_neo.domain.reading

import com.rchdliang.copymanga_neo.domain.model.NovelReadingVolume
import com.rchdliang.copymanga_neo.domain.model.NovelVolume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

enum class NovelSearchScope { VOLUME, BOOK }

data class NovelBookMatch(
    val volumeId: String, val volumeName: String, val chapterName: String,
    val match: NovelTextMatch
) {
    val key get() = volumeId + ":" + match.range.startLine + ":" + match.range.startCharacter
    val paragraphKey get() = volumeId + ":" + match.range.startLine
}

data class NovelBookSearchProgress(
    val matches: List<NovelBookMatch> = emptyList(),
    val totalHits: Int = 0, val paragraphs: Int = 0,
    val completedVolumes: Int = 0, val totalVolumes: Int = 0,
    val currentVolume: String = "", val complete: Boolean = false,
    val error: String? = null
)

/** 先检索已打开的正文；其他分卷串行按需加载。搜索不切换阅读器或记录阅读进度。 */
suspend fun searchNovelVolumes(
    query: String,
    current: NovelReadingVolume,
    catalogue: List<NovelVolume>,
    load: suspend (NovelVolume) -> Result<NovelReadingVolume>,
    onProgress: (NovelBookSearchProgress) -> Unit,
    limit: Int = 500
): NovelBookSearchProgress {
    require(limit > 0)
    if (query.isBlank()) return NovelBookSearchProgress(complete = true)
    val volumes = (catalogue + current.volume).distinctBy { it.id }.sortedBy { it.index }
    val order = volumes.mapIndexed { index, volume -> volume.id to index }.toMap()
    val scanOrder = listOf(current.volume) + volumes.filter { it.id != current.volume.id }
    val retained = ArrayList<NovelBookMatch>()
    var state = NovelBookSearchProgress(totalVolumes = volumes.size)
    fun sorted() =
        retained.sortedWith(compareBy<NovelBookMatch> { order[it.volumeId] ?: Int.MAX_VALUE }
            .thenBy { it.match.range.startLine }.thenBy { it.match.range.startCharacter })
    for (volume in scanOrder) {
        currentCoroutineContext().ensureActive()
        state = state.copy(currentVolume = volume.name)
        onProgress(state)
        var reading: NovelReadingVolume? = null
        var failure: Throwable? = null
        if (volume.id == current.volume.id) reading = current
        else load(volume).fold(onSuccess = { reading = it }, onFailure = {
            if (it is CancellationException) throw it
            failure = it
        })
        currentCoroutineContext().ensureActive()
        val document = reading
        if (document == null) {
            state = state.copy(
                error = "“" + volume.name + "”读取失败：" + (failure?.message ?: "请稍后重试")
            )
            onProgress(state)
            return state
        }
        val result = withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            document.document.findMatches(
                query,
                (limit - retained.size).coerceAtLeast(1)
            ) { context.ensureActive() }
        }
        val chapters =
            document.sections.filter { it.type == 1 && it.start in document.document.lines.indices }
                .sortedBy { it.start }
        result.matches.take((limit - retained.size).coerceAtLeast(0)).forEach { match ->
            val chapter =
                chapters.lastOrNull { it.start <= match.range.startLine }?.name?.trim().orEmpty()
            retained += NovelBookMatch(volume.id, volume.name, chapter.ifBlank { "卷首" }, match)
        }
        state = state.copy(
            matches = sorted(), totalHits = state.totalHits + result.total,
            paragraphs = state.paragraphs + result.paragraphs,
            completedVolumes = state.completedVolumes + 1
        )
        onProgress(state)
    }
    state = state.copy(complete = true, currentVolume = "")
    onProgress(state)
    return state
}
