package com.rchdliang.copymanga_neo.data.remote.util

/** 同一次漫画网页响应中的目录密钥和协议版本，不跨站点复用。 */
data class WwwCatalogProtocol(val key: String, val dnts: String) {
    companion object {
        fun fromHtml(html: String): WwwCatalogProtocol {
            val source = html.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            val scripts = Regex(
                "<script\\b[^>]*>(.*?)</script\\s*>",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
                .findAll(source).map { it.groupValues[1] }
            val declaration =
                Regex("(?m)^\\s*(?:var|let|const)\\s+ccz\\s*=\\s*(['\"])([^'\"\\r\\n]+)\\1\\s*;?")
            val keys =
                scripts.flatMap { declaration.findAll(it).map { match -> match.groupValues[2] } }
                    .distinct().toList()
            require(keys.size == 1) { "漫画网页缺少唯一的目录密钥" }
            val key = keys.single()
            require(key.toByteArray(Charsets.UTF_8).size == 16) { "漫画网页目录密钥长度无效" }
            val tags =
                Regex("<[^>]+\\bid\\s*=\\s*(['\"])dnt\\1[^>]*>", RegexOption.IGNORE_CASE).findAll(
                    source
                ).toList()
            require(tags.size == 1) { "漫画网页缺少唯一的目录协议版本" }
            val value = Regex("\\bvalue\\s*=\\s*(['\"])([0-9]+)\\1", RegexOption.IGNORE_CASE)
                .find(tags.single().value)?.groupValues?.get(2)
            require(value != null && value.length <= 10 && value.toLongOrNull() != null) { "漫画网页目录协议版本无效" }
            return WwwCatalogProtocol(key, value)
        }
    }
}
