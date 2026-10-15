@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package com.rchdliang.copymanga_neo.data.storage

import kotlin.JsFun
import kotlin.js.JsString
import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@JsFun("""(name, action, path, payload) => new Promise((resolve, reject) => {
  let blocked = false;
  const open = globalThis.indexedDB.open(name, 1);
  open.onupgradeneeded = () => open.result.createObjectStore('files');
  open.onerror = () => reject(open.error);
  open.onblocked = () => { blocked = true; reject(new Error('Browser cache upgrade is blocked by another tab')); };
  open.onsuccess = () => {
    const db = open.result;
    if (blocked) { db.close(); return; }
    const writable = action === 'write' || action === 'remove';
    const tx = db.transaction('files', writable ? 'readwrite' : 'readonly');
    const store = tx.objectStore('files');
    let result = null;
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => { db.close(); reject(tx.error || new Error('Browser cache transaction aborted')); };
    tx.oncomplete = () => { db.close(); resolve(result); };
    const binary = bytes => {
      let value = ''; const data = new Uint8Array(bytes);
      for (let i = 0; i < data.length; i += 8192) value += String.fromCharCode(...data.subarray(i, i + 8192));
      return value;
    };
    if (action === 'write') store.put(Uint8Array.from(payload, c => c.charCodeAt(0)).buffer, path);
    else if (action === 'read' || action === 'size' || action === 'url') {
      const req = store.get(path);
      req.onsuccess = () => {
        if (req.result === undefined) return;
        if (action === 'size') result = String(req.result.byteLength);
        else if (action === 'url') {
          const key = name + ':' + path;
          const urls = globalThis.__copyMangaCacheUrls || (globalThis.__copyMangaCacheUrls = new Map());
          const previous = urls.get(key);
          const bytes = new Uint8Array(req.result);
          if (previous && previous.bytes.length === bytes.length && previous.bytes.every((value, index) => value === bytes[index])) {
            result = previous.url;
          } else {
            if (previous) URL.revokeObjectURL(previous.url);
            result = URL.createObjectURL(new Blob([bytes]));
            urls.set(key, { url: result, bytes });
          }
        } else result = binary(req.result);
      };
    } else {
      const keys = []; const req = store.openCursor();
      req.onsuccess = () => {
        const cursor = req.result;
        if (!cursor) { result = JSON.stringify(keys); return; }
        if (path === '' || cursor.key === path || cursor.key.startsWith(path + '/')) {
          if (action === 'remove') {
            cursor.delete();
            const urls = globalThis.__copyMangaCacheUrls;
            const key = name + ':' + cursor.key;
            if (urls && urls.has(key)) { URL.revokeObjectURL(urls.get(key).url); urls.delete(key); }
          } else keys.push(cursor.key);
        }
        cursor.continue();
      };
    }
  };
})""")
private external fun browserCacheOperation(name: String, action: String, path: String, payload: String): Promise<JsString?>

@JsFun("""async () => {
  if (!globalThis.navigator?.storage?.estimate) throw new Error('Browser storage quota information is unavailable');
  const value = await navigator.storage.estimate();
  if (!Number.isFinite(value.quota) || !Number.isFinite(value.usage)) throw new Error('Browser storage quota is unknown');
  return String(Math.max(0, value.quota - value.usage));
}""")
private external fun browserAvailableStorage(): Promise<JsString>

/** IndexedDB transactions acknowledge writes only after commit. Quota failures propagate. */
class BrowserCacheStorage(namespace: String) : CacheStorage {
    private val name = "copymanga-neo-cache:$namespace"
    private suspend fun operation(action: String, path: String, value: String = ""): String? {
        validateCachePath(path, allowEmpty = action == "list" || action == "remove")
        return browserCacheOperation(name, action, path, value).await()?.toString()
    }
    override suspend fun read(path: String, limit: Int): ByteArray? {
        require(limit >= 0)
        val size = operation("size", path)?.toLong() ?: return null
        require(size <= limit) { "Cache file exceeds limit" }
        return operation("read", path)?.let { binary ->
            require(binary.length <= limit) { "Cache file exceeds limit" }
            ByteArray(binary.length) { binary[it].code.toByte() }
        }
    }
    override suspend fun write(path: String, bytes: ByteArray) {
        operation("write", path, buildString(bytes.size) { bytes.forEach { append((it.toInt() and 255).toChar()) } })
    }
    override suspend fun list(prefix: String): List<String> = Json.parseToJsonElement(operation("list", prefix) ?: "[]")
        .jsonArray.map { it.jsonPrimitive.content }
    override suspend fun remove(path: String) { operation("remove", path) }
    override suspend fun size(path: String): Long = operation("size", path)?.toLong() ?: error("Cache file is missing")
    override suspend fun availableBytes(): Long = browserAvailableStorage().await().toString().toDouble().toLong()
    override suspend fun imageLocation(path: String): String = operation("url", path) ?: error("Cache image is missing")
}

actual fun platformCacheStorage(directory: String): CacheStorage = BrowserCacheStorage(directory)
