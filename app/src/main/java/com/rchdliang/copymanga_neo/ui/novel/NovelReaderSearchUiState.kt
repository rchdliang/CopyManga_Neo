package com.rchdliang.copymanga_neo.ui.novel

import com.rchdliang.copymanga_neo.domain.reading.NovelBookMatch
import com.rchdliang.copymanga_neo.domain.reading.NovelBookSearchProgress
import com.rchdliang.copymanga_neo.domain.reading.NovelSearchScope

data class NovelReaderSearchUiState(
    val bookPath: String = "",
    val query: String = "",
    val scope: NovelSearchScope = NovelSearchScope.VOLUME,
    val visible: Boolean = false,
    val running: Boolean = false,
    val progress: NovelBookSearchProgress = NovelBookSearchProgress(),
    val selected: NovelBookMatch? = null
)
