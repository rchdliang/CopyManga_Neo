package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.*
import com.rchdliang.copymanga_neo.domain.reading.ImportRollbackPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll

@Serializable
internal data class AppPreferenceDocument(
    val app: AppSettings = AppSettings(),
    val ranks: HomeRankPreferences = HomeRankPreferences(),
    val compactSearch: Boolean = false,
    val compactDiscovery: Boolean = false,
    val favoritesNovelSelected: Boolean = false,
    val chineseDefaultInitialized: Boolean = false
)

class PortableAppSettings(private val storage: DocumentStorage) : AppSettingsRepository {
    companion object {
        /** Read only the startup palette; graph creation owns recovery, migration and errors. */
        suspend fun readStartupThemeMode(storage: DocumentStorage): AppThemeMode {
            val raw = storage.read("app-settings.json") ?: return AppThemeMode.SYSTEM
            val payload = DocumentEnvelope.decode(raw, "copymanga.document/app-settings.json", 1, emptyMap()).payload
            return storageJson.decodeFromJsonElement(AppPreferenceDocument.serializer(), payload).app.themeMode
        }
    }

    private val document = DocumentStore(storage, "app-settings.json", AppPreferenceDocument.serializer(), AppPreferenceDocument())
    override val settings: Flow<AppSettings> = document.state.map { it.app.copy(comicImageConcurrency = it.app.comicImageConcurrency.coerceIn(1, 10), comicDownloadLimitKiB = it.app.comicDownloadLimitKiB.coerceAtLeast(0)) }
    suspend fun initialize() = document.ensureLoaded()
    override suspend fun setAutoCheckUpdates(enabled: Boolean) = document.update { it.copy(app = it.app.copy(autoCheckUpdates = enabled)) }
    override suspend fun setThemeMode(mode: AppThemeMode) = document.update { it.copy(app = it.app.copy(themeMode = mode)) }
    override suspend fun setSimplifyChinese(enabled: Boolean) = document.update {
        it.copy(app = it.app.copy(simplifyChinese = enabled), chineseDefaultInitialized = true)
    }
    override suspend fun initializeChineseDefault(languageTag: String): Result<Unit> {
        val existing = try { storage.read("app-settings.json") != null }
        catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (error: Exception) { return Result.failure(error) }
        return document.update {
            if (it.chineseDefaultInitialized) it
            else it.copy(app = if (existing) it.app else it.app.copy(simplifyChinese = ChineseLanguageDefault.enabled(languageTag)),
                chineseDefaultInitialized = true)
        }
    }
    override suspend fun setWifiOnlyDownloads(enabled: Boolean) = document.update { it.copy(app = it.app.copy(wifiOnlyDownloads = enabled)) }
    override suspend fun setBlurNovelIllustrationPreviews(enabled: Boolean) = document.update {
        it.copy(app = it.app.copy(blurNovelIllustrationPreviews = enabled))
    }
    override suspend fun setComicImageConcurrency(count: Int): Result<Unit> = document.update {
        require(count in 1..10) { "漫画图片并发数须为 1–10" }
        it.copy(app = it.app.copy(comicImageConcurrency = count))
    }
    override suspend fun setComicDownloadLimitKiB(limit: Int): Result<Unit> = document.update {
        require(limit >= 0) { "漫画图片限速不能为负数" }
        it.copy(app = it.app.copy(comicDownloadLimitKiB = limit))
    }
    override suspend fun setCredentialExportPassword(enabled: Boolean) = document.update { it.copy(app = it.app.copy(includePasswordInCredentialExport = enabled)) }
    override suspend fun getHomeRankPreferences(): Result<HomeRankPreferences> = document.ensureLoaded().map { document.state.value.ranks }
    override suspend fun setHomeRankPreferences(preferences: HomeRankPreferences) = document.update { it.copy(ranks = preferences) }
    override suspend fun getSearchCompactLayout(keywordSearch: Boolean): Result<Boolean> = document.ensureLoaded().map {
        if (keywordSearch) document.state.value.compactSearch else document.state.value.compactDiscovery
    }
    override suspend fun setSearchCompactLayout(keywordSearch: Boolean, compact: Boolean) = document.update {
        if (keywordSearch) it.copy(compactSearch = compact) else it.copy(compactDiscovery = compact)
    }
    override suspend fun getFavoritesNovelSelected(): Result<Boolean> = document.ensureLoaded().map {
        document.state.value.favoritesNovelSelected
    }
    override suspend fun setFavoritesNovelSelected(selected: Boolean) = document.update {
        it.copy(favoritesNovelSelected = selected)
    }
    override suspend fun resetDefaults() = document.update { AppPreferenceDocument(chineseDefaultInitialized = true) }
}

@Serializable
internal data class ComicPreferenceDocument(
    val global: ReaderPreferences = ReaderPreferences(),
    val comics: Map<String, ReaderPreferences> = emptyMap()
)
class PortableReaderPreferences(storage: DocumentStorage) : ReaderPreferencesRepository {
    private val document = DocumentStore(storage, "comic-reader-settings.json", ComicPreferenceDocument.serializer(), ComicPreferenceDocument())
    override val globalPreferences: Flow<ReaderPreferences> = document.loadedState().map { it.global }
    override fun comicPreferences(comicPathWord: String) = document.loadedState().map { it.comics[comicPathWord] ?: it.global }
    suspend fun initialize() = document.ensureLoaded()
    override suspend fun initializeComic(comicPathWord: String): Result<ReaderPreferences> = document.update {
        if (comicPathWord in it.comics) it else it.copy(comics = it.comics + (comicPathWord to it.global))
    }.map { document.state.value.comics.getValue(comicPathWord) }
    override suspend fun updateGlobal(transform: (ReaderPreferences) -> ReaderPreferences) = document.update { it.copy(global = transform(it.global).normalized()) }
    override suspend fun updateComic(comicPathWord: String, transform: (ReaderPreferences) -> ReaderPreferences) = document.update {
        it.copy(comics = it.comics + (comicPathWord to transform(it.comics[comicPathWord] ?: it.global).normalized()))
    }
    override suspend fun exportComicPreferences(): Result<Map<String, ReaderPreferences>> = document.ensureLoaded().map { document.state.value.comics }
    override suspend fun mergeComicPreferences(values: Map<String, ReaderPreferences>): Result<ComicPreferencesChange> {
        var change = ComicPreferencesChange(emptyMap(), emptyMap())
        return document.update { current ->
            val before = current.comics.mapValues { storageJson.encodeToString(ReaderPreferences.serializer(), it.value) }
            val applied = before + values.mapValues { storageJson.encodeToString(ReaderPreferences.serializer(), it.value.normalized()) }
            change = ComicPreferencesChange(before, applied)
            current.copy(comics = current.comics + values.mapValues { it.value.normalized() })
        }.map { change }
    }
    override suspend fun rollbackComicPreferences(change: ComicPreferencesChange) = document.update { current ->
        val now = current.comics.mapValues { storageJson.encodeToString(ReaderPreferences.serializer(), it.value) }
        current.copy(comics = ImportRollbackPolicy.restore(change.before, change.applied, now).mapValues {
            storageJson.decodeFromString(ReaderPreferences.serializer(), it.value)
        })
    }
    override suspend fun resetGlobal() = document.update { it.copy(global = ReaderPreferences()) }
    override suspend fun resetComic(comicPathWord: String) = document.update { it.copy(comics = it.comics + (comicPathWord to it.global.copy(pageLayout = "SINGLE"))) }
}

class PortableNovelPreferences(storage: DocumentStorage) : NovelReaderPreferencesRepository {
    private val blur = DocumentStore(storage, "novel-illustration-blur.json",
        MapSerializer(String.serializer(), Boolean.serializer()), emptyMap())
    override fun illustrationBlur(path: String): Flow<Boolean?> = flow {
        blur.ensureLoaded().fold({}, { throw it })
        emitAll(blur.state.map { it[path] })
    }
    override suspend fun setIllustrationBlur(path: String, enabled: Boolean): Result<Unit> = blur.update {
        require(path.isNotBlank())
        it + (path to enabled)
    }
    private val document = DocumentStore(storage, "novel-reader-settings.json", NovelReaderPreferences.serializer(), NovelReaderPreferences())
    override val settings = document.loadedState()
    suspend fun initialize() = document.ensureLoaded()
    override suspend fun update(value: NovelReaderPreferences) = document.update { value.normalized() }
}
class PortableNovelAnnotations(storage: DocumentStorage) : NovelAnnotationRepository {
    private val document = DocumentStore(storage, "novel-annotations.json", ListSerializer(NovelAnnotation.serializer()), emptyList())
    override val annotations = document.loadedState()
    suspend fun initialize() = document.ensureLoaded()
    override suspend fun save(annotation: NovelAnnotation) = document.update { current ->
        require(annotation.range.valid && annotation.id.isNotBlank() && annotation.owner.isNotBlank() &&
            annotation.mode.isNotBlank() && annotation.path.isNotBlank() && annotation.volume.isNotBlank() &&
            annotation.text.isNotBlank() && annotation.style in setOf("PRIMARY", "SECONDARY", "TERTIARY")) { "标注内容无效" }
        val same = current.firstOrNull {
            it.owner == annotation.owner && it.path == annotation.path &&
                it.volume == annotation.volume && (it.id == annotation.id || it.range == annotation.range)
        }
        val updated = annotation.copy(id = same?.id ?: annotation.id, createdAt = same?.createdAt ?: annotation.createdAt)
        current.filterNot { it.id == updated.id && it.owner == updated.owner &&
            it.path == updated.path && it.volume == updated.volume } + updated
    }
    override suspend fun remove(annotation: NovelAnnotation) = document.update { current ->
        current.filterNot { it.id == annotation.id && it.owner == annotation.owner && it.path == annotation.path && it.volume == annotation.volume }
    }
}
