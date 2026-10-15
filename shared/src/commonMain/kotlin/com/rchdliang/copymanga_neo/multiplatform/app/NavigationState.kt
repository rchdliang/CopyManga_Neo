package com.rchdliang.copymanga_neo.multiplatform.app

import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid
import io.ktor.http.parseQueryString

@Serializable
enum class MainTab(val title: String) { HOME("首页"), DOWNLOADS("下载"), FAVORITES("书架"), PROFILE("我的") }
@Serializable
data class AppRoute(
    val destination: String,
    val id: String = Uuid.random().toString(),
    val path: String = "",
    val chapter: String = "",
    val chapterName: String = "",
    val group: String = "default",
    val page: Int = -1,
    val fraction: Double = 0.0,
    val arguments: Map<String, String> = emptyMap(),
    val resume: LocalReadingRecord? = null
)

/** Screen callbacks independent of the stack implementation. */
interface AppNavigation {
    val tab: MainTab
    fun open(route: AppRoute)
    fun back(): Boolean
    fun select(tab: MainTab)
    fun explore(sectionAndQuery: String) = open(AppRoute("explore", arguments = exploreArguments(sectionAndQuery)))
    fun comic(path: String) = open(AppRoute("detail", path = path))
    fun novel(path: String? = null, resume: LocalReadingRecord? = null) = open(AppRoute("novel", path = path.orEmpty(), resume = resume))
    fun reader(path: String, chapter: String, name: String, group: String, page: Int = -1, fraction: Double = 0.0) =
        open(AppRoute("reader", path = path, chapter = chapter, chapterName = name, group = group, page = page, fraction = fraction))
}

/** Home rank links already carry audience/period; keep them separate from the section identifier. */
internal fun exploreArguments(sectionAndQuery: String): Map<String, String> = buildMap {
    put("section", sectionAndQuery.substringBefore('?'))
    val query = sectionAndQuery.substringAfter('?', "")
    parseQueryString(query).entries().forEach { (name, values) ->
        if (name != "section" && values.isNotEmpty()) put(name, values.last())
    }
}
