package com.rchdliang.copymanga_neo.domain.text

/**
 * 使用可选下载的 OpenCC tw2s 词典，按词组最长匹配转换显示文字。
 * 输入始终保留在业务模型中；此转换器不参与标识、查询参数或黑名单匹配。
 */
class TraditionalChineseConverter private constructor(
    private val normalization: Dictionary,
    private val phrases: Dictionary,
    private val taiwanPhrases: Dictionary,
    private val taiwanCharacters: Dictionary,
    private val characters: Dictionary
) {
    fun convert(original: String): String {
        val text = replace(original, normalization, null)
        return convertNormalized(text)
    }

    private fun convertNormalized(text: String): String = buildString(text.length) {
        var start = 0
        var cursor = 0
        while (cursor < text.length) {
            val match = phrases.match(text, cursor)
            if (match == null) {
                cursor++
            } else {
                if (start < cursor) append(convertSegment(text.substring(start, cursor)))
                append(convertSegment(text.substring(cursor, match.end)))
                cursor = match.end
                start = cursor
            }
        }
        if (start < text.length) append(convertSegment(text.substring(start)))
    }

    private fun convertSegment(text: String): String {
        val normalized = replace(text, taiwanPhrases, taiwanCharacters)
        return replace(normalized, phrases, characters)
    }

    private fun replace(text: String, preferred: Dictionary, fallback: Dictionary?): String =
        buildString(text.length) {
            var cursor = 0
            while (cursor < text.length) {
                val match = preferred.match(text, cursor) ?: fallback?.match(text, cursor)
                if (match == null) append(text[cursor++])
                else {
                    append(match.value)
                    cursor = match.end
                }
            }
        }

    private data class Match(val end: Int, val value: String)

    private class Dictionary(entries: List<Pair<String, String>>) {
        private class Node {
            val children = mutableMapOf<Char, Node>()
            var value: String? = null
        }

        private val root = Node()

        init {
            entries.forEach { (key, value) ->
                var node = root
                key.forEach { character -> node = node.children.getOrPut(character) { Node() } }
                node.value = value
            }
        }

        fun match(text: String, start: Int): Match? {
            var node = root
            var cursor = start
            var result: Match? = null
            while (cursor < text.length) {
                node = node.children[text[cursor]] ?: break
                cursor++
                node.value?.let { result = Match(cursor, it) }
            }
            return result
        }
    }

    companion object {
        val dictionaryFiles = listOf(
            "TWVariantsRevPhrases.txt", "TWVariants.txt", "TSPhrases.txt", "TSCharacters.txt"
        )

        fun fromDictionaries(files: Map<String, String>): TraditionalChineseConverter {
            fun entries(name: String, reverse: Boolean = false): List<Pair<String, String>> =
                files.getValue(name).lineSequence()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .flatMap { line ->
                        val key = line.substringBefore('\t')
                        val value = line.substringAfter('\t', "").trim()
                        require(key.isNotEmpty() && value.isNotEmpty()) { "繁转简词典格式无效：$name" }
                        if (reverse) value.split(' ').filter { it.isNotEmpty() }.map { it to key }
                        else listOf(key to value.substringBefore(' '))
                    }.toList()
            return TraditionalChineseConverter(
                normalization = Dictionary(
                    if ("CJK_Compatibility_Ideographs.txt" in files) entries(
                        "CJK_Compatibility_Ideographs.txt"
                    ) else emptyList()
                ),
                phrases = Dictionary(entries("TSPhrases.txt")),
                taiwanPhrases = Dictionary(entries("TWVariantsRevPhrases.txt")),
                // OpenCC 的 TWVariantsRev 由 TWVariants 反转生成。
                taiwanCharacters = Dictionary(entries("TWVariants.txt", reverse = true)),
                characters = Dictionary(entries("TSCharacters.txt"))
            )
        }
    }
}
