package com.rchdliang.copymanga_neo.data.local.portable

import kotlin.JsFun

@JsFun("(key) => globalThis.localStorage.getItem(key)")
private external fun readItem(key: String): String?
@JsFun("(key, value) => { globalThis.localStorage.setItem(key, value); }")
private external fun writeItem(key: String, value: String)
@JsFun("(key) => { globalThis.localStorage.removeItem(key); }")
private external fun removeItem(key: String)

/** Browser quota failures propagate; users never see a successful save that was discarded. */
actual fun platformDocumentStorage(directory: String): DocumentStorage = object : DocumentStorage {
    private fun storageKey(value: String): String = "copymanga-neo:$directory:$value"
    override suspend fun read(key: String): String? = readItem(storageKey(key))
    override suspend fun write(key: String, value: String) = writeItem(storageKey(key), value)
    override suspend fun remove(key: String) = removeItem(storageKey(key))
}
