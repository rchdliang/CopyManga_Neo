package com.rchdliang.copymanga_neo.data.local

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings

/** 阅读工具只备份偏好；漫画和小说书签统一由 LocalReadingCodec 编码。 */
internal object ReaderToolsBackupCodec {
    fun encode(settings: ReaderToolsSettings): String = JsonObject().apply {
        addProperty("inlineComments", settings.inlineComments)
        addProperty("continuousChapters", settings.continuousChapters)
        addProperty("volumeKeys", settings.volumeKeys)
        addProperty("autoInterval", settings.autoIntervalSeconds)
        addProperty("imageRetries", settings.imageRetries)
        addProperty("imageTimeout", settings.imageTimeoutSeconds)
        addProperty("preload", settings.preloadPages)
        addProperty("pageLayout", settings.pageLayout)
        addProperty("pageAnimation", settings.pageAnimation)
        add("pairingRestarts", JsonParser.parseString(encodePairing(settings.pairingRestarts)))
    }.toString()

    fun encodePairing(value: Map<String, List<Int>>): String = com.google.gson.Gson().toJson(value)

    fun decodePairing(raw: String): Map<String, List<Int>> =
        JsonParser.parseString(raw).asJsonObject.entrySet().associate { (key, value) ->
            key to value.asJsonArray.map { it.asString.toInt() }
        }

    fun decode(raw: String): ReaderToolsSettings {
        val json = JsonParser.parseString(raw).asJsonObject
        fun boolean(key: String): Boolean {
            val value = json[key]
            require(value?.isJsonPrimitive == true && value.asJsonPrimitive.isBoolean) { "阅读工具设置无效：$key" }
            return value.asBoolean
        }

        fun integer(key: String): Int {
            val value = json[key]
            require(value?.isJsonPrimitive == true && value.asJsonPrimitive.isNumber) { "阅读工具设置无效：$key" }
            return value.asString.toIntOrNull() ?: error("阅读工具设置必须为整数：$key")
        }
        return ReaderToolsSettings(
            boolean("inlineComments"),
            boolean("continuousChapters"),
            boolean("volumeKeys"),
            integer("autoInterval"),
            integer("imageRetries"),
            integer("imageTimeout"),
            integer("preload"),
            pageLayout = json["pageLayout"]?.asString ?: "SINGLE",
            pageAnimation = if (json.has("pageAnimation")) boolean("pageAnimation") else true,
            pairingRestarts = json["pairingRestarts"]?.let { decodePairing(it.toString()) } ?: emptyMap()
        ).also { require(it == it.normalized()) { "阅读工具设置无效" } }
    }
}
