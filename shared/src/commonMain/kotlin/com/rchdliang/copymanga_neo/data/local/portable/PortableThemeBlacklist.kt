package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.domain.model.Theme
import com.rchdliang.copymanga_neo.domain.model.matches
import com.rchdliang.copymanga_neo.domain.repository.ThemeBlacklistRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class BlockedTheme(val name: String, @SerialName("path_word") val path: String? = null) {
    fun model() = Theme(name, path)
}
@Serializable
internal data class BlacklistDocument(
    val enabled: Boolean = true,
    @SerialName("blocked_themes") val themes: List<BlockedTheme> = emptyList()
)

/** Disabling filtering retains the saved tags; unknown or corrupt storage never gets overwritten. */
class PortableThemeBlacklist(storage: DocumentStorage) : ThemeBlacklistRepository {
    private val document = DocumentStore(storage, "theme-blacklist.json", BlacklistDocument.serializer(), BlacklistDocument())
    override val isEnabled = document.state.map { it.enabled }.distinctUntilChanged()
    override val blockedThemes = document.state.map { value -> value.themes.map { it.model() } }.distinctUntilChanged()
    val error = document.error
    suspend fun initialize() = document.load()
    override suspend fun setEnabled(enabled: Boolean) = document.update { it.copy(enabled = enabled) }
    override suspend fun setBlocked(theme: Theme, blocked: Boolean) = document.update { value ->
        require(theme.name.isNotBlank()) { "标签名称不能为空" }
        val retained = value.themes.filterNot { it.model().matches(theme) }
        value.copy(themes = if (blocked) retained + BlockedTheme(theme.name, theme.pathWord) else retained)
    }
}
