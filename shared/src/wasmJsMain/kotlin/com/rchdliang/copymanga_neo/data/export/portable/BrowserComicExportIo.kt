@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package com.rchdliang.copymanga_neo.data.export.portable

import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.platform.PlatformIOException
import kotlin.JsFun
import kotlin.js.JsString
import kotlin.js.Promise
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

@JsFun("""async (action, encoded, binary) => {
 const a = JSON.parse(encoded);
 const state = globalThis.__copyMangaExport || (globalThis.__copyMangaExport = {records:new Map(), streams:new Map(), sources:new Map()});
 const dbOp = (database, storeName, mode, key, value) => new Promise((resolve,reject) => {
   const request = indexedDB.open(database,1); let blocked = false;
   request.onupgradeneeded = () => request.result.createObjectStore(storeName);
   request.onerror = () => reject(request.error);
   request.onblocked = () => { blocked = true; reject(new Error('Export storage is blocked by another tab')); };
   request.onsuccess = () => {
     const db = request.result; if (blocked) { db.close(); return; }
     const tx = db.transaction(storeName, mode === 'get' ? 'readonly' : 'readwrite');
     const store = tx.objectStore(storeName);
     const req = mode === 'get' ? store.get(key) : mode === 'put' ? store.put(value,key) : store.delete(key);
     let result;
     req.onsuccess = () => { result = req.result; };
     tx.oncomplete = () => { db.close(); resolve(result); };
     tx.onabort = tx.onerror = () => { db.close(); reject(tx.error || new Error('Export handle persistence failed')); };
   };
 });
 const records = (mode,key,value) => dbOp('copymanga-comic-export','outputs',mode,key,value);
 const target = async token => {
   let value = state.records.get(token);
   if (!value) { value = await records('get',token); if (value) state.records.set(token,value); }
   if (!value) throw new Error('Export handle is unavailable; select the destination again');
   return value;
 };
 const permission = async handle => {
   if (handle.queryPermission && await handle.queryPermission({mode:'readwrite'}) !== 'granted')
     throw new Error('Export permission expired; select the destination again');
 };
 const selected = async token => {
   let value = globalThis.__copyMangaPlatform?.files?.get(token);
   if (!value) {
     value = await dbOp('copymanga-platform-handles','handles','get',token);
     if (value && globalThis.__copyMangaPlatform?.files) globalThis.__copyMangaPlatform.files.set(token,value);
   }
   if (!value || !value.kind) throw new Error('Select a file-system export destination again');
   await permission(value); return value;
 };
 const bytes = async () => {
   const prefix = 'copymanga-neo-cache:' + a.namespace + ':';
   let path;
   if(a.source.startsWith('browser-download:')) path=a.source.slice('browser-download:'.length);
   else for (const [key,value] of globalThis.__copyMangaCacheUrls || [])
     if (key.startsWith(prefix+'downloads/') && value.url === a.source) {path=key.slice(prefix.length);break;}
   if(!path || !path.startsWith('downloads/') || /[\\:\u0000]/.test(path) || path.split('/').some(p=>!p||p==='.'||p==='..'))
     throw new Error('Downloaded image is unavailable or changed; select the chapter again');
   const value=await dbOp('copymanga-neo-cache:'+a.namespace,'files','get',path);
   if(value === undefined)throw new Error('Downloaded image no longer exists');
   return {data:new Uint8Array(value),source:'browser-download:'+path};
 };
 const toBinary = data => {
   let result = ''; for (let i=0;i<data.length;i+=8192) result += String.fromCharCode(...data.subarray(i,i+8192));
   return result;
 };
 const safe = name => { if (!name || name === '.' || name === '..' || /[\\/\u0000]/.test(name)) throw new Error('Invalid export name'); return name; };
 const exists = async (parent,name) => {
   try { await parent.getFileHandle(name); return true; } catch (e) { if (e.name === 'TypeMismatchError') return true; if (e.name !== 'NotFoundError') throw e; }
   try { await parent.getDirectoryHandle(name); return true; } catch (e) { if (e.name !== 'NotFoundError') throw e; return false; }
 };
 const unique = async (parent,name,directory) => {
   safe(name); const dot = directory ? -1 : name.lastIndexOf('.');
   const stem = dot < 0 ? name : name.slice(0,dot), ext = dot < 0 ? '' : name.slice(dot);
   for(let i=1;;i++) {
     const candidate = i === 1 ? name : stem + ' (' + i + ')' + ext;
     if(await exists(parent,candidate)) continue;
     const handle = directory ? await parent.getDirectoryHandle(candidate,{create:true}) : await parent.getFileHandle(candidate,{create:true});
     if(!directory && (await handle.getFile()).size !== 0) continue;
     return {handle,parent,name:candidate};
   }
 };
 if(action === 'inspect' || action === 'open-source') {
   const {data,source}=await bytes();
   const token=action==='open-source' ? crypto.randomUUID() : null;
   if(token)state.sources.set(token,data);
   return JSON.stringify({size:data.length,header:toBinary(data.subarray(0,64)),source,token});
 }
 if(action === 'release-source') { state.sources.delete(a.readToken);return ''; }
 if(action === 'read') { const data=state.sources.get(a.readToken); if(!data || data.length !== a.size) throw new Error('Downloaded image snapshot is unavailable'); return toBinary(data.subarray(a.offset,a.offset+65536)); }
 if(action === 'create') {
   const destination = await selected(a.destination); let result;
   if(a.directory) {
     if(destination.kind !== 'directory') throw new Error('Select an export directory');
     let parent=destination;
     if(a.images) {
       parent=await parent.getDirectoryHandle(safe(a.comic),{create:true});
       parent=await parent.getDirectoryHandle(safe(a.group),{create:true});
     }
     result=await unique(parent,a.name,a.images);
   } else {
     if(a.images || destination.kind !== 'file') throw new Error('Select an export file');
     if((await destination.getFile()).size !== 0) throw new Error('Destination already contains data; select a new file');
     result={handle:destination,parent:null,name:destination.name};
   }
   result.directory=a.images;
   const token='web-export:'+crypto.randomUUID();
   try { await records('put',token,result); } catch(e) {
     try { if(result.parent) await result.parent.removeEntry(result.name,{recursive:result.directory}); else if(result.handle.remove) await result.handle.remove(); }
     catch(cleanup) { throw new Error('Cannot persist or remove incomplete export: '+cleanup.message); }
     throw e;
   }
   state.records.set(token,result); return JSON.stringify({token,name:result.name});
 }
 if(action === 'open' || action === 'member') {
   const result=await target(a.token); await permission(result.handle);
   let handle=result.handle;
   if(action === 'member') {
     if(!result.directory) throw new Error('Export is not a directory');
     safe(a.name); if(await exists(handle,a.name)) throw new Error('Export image already exists');
     handle=await handle.getFileHandle(a.name,{create:true});
   }
   const token='web-export-stream:'+crypto.randomUUID();
   const stream=await handle.createWritable({keepExistingData:false,mode:'exclusive'});
   state.streams.set(token,stream); return token;
 }
 if(action === 'write' || action === 'close' || action === 'abort') {
   const stream=state.streams.get(a.stream);
   if(!stream) { if(action === 'abort') return ''; throw new Error('Export stream is closed'); }
   if(action === 'write') await stream.write(Uint8Array.from(binary,c=>c.charCodeAt(0)));
   else { try { if(action === 'close') await stream.close(); else await stream.abort(); } finally { state.streams.delete(a.stream); } }
   return '';
 }
 if(action === 'delete') {
   const result=await target(a.token); await permission(result.handle);
   try {
     if(result.parent) {
       const current=result.directory ? await result.parent.getDirectoryHandle(result.name) : await result.parent.getFileHandle(result.name);
       if(!await current.isSameEntry(result.handle))return 'false';
       await result.parent.removeEntry(result.name,{recursive:result.directory});
     }
     else if(result.handle.remove) await result.handle.remove({recursive:result.directory});
     else return 'false';
   } catch(e) { if(e.name !== 'NotFoundError') throw e; }
   await records('delete',a.token); state.records.delete(a.token); return 'true';
 }
 throw new Error('Unknown export operation');
}""")
private external fun browserExport(action: String, arguments: String, binary: String): Promise<JsString>

/** Real File System Access API exports, with durable structured-clone handles for cleanup/retry.
 * [cacheNamespace] must match the BrowserCacheStorage used by the download repository.
 * Permission loss and browsers without filesystem destinations fail visibly; no download-link fallback.
 */
class BrowserComicExportIo(private val cacheNamespace: String) : ComicExportIo {
    private suspend fun operation(action: String, values: Map<String, JsonElement>, bytes: ByteArray? = null): String =
        browserExport(action, JsonObject(values).toString(), bytes?.let { data ->
            buildString(data.size) { data.forEach { append((it.toInt() and 255).toChar()) } }
        } ?: "").await().toString()
    private fun args(vararg pairs: Pair<String, String>) = pairs.associate { it.first to JsonPrimitive(it.second) }
    private fun sourceArgs(source: String) = args("namespace" to cacheNamespace, "source" to source)
    private fun binary(value: String) = ByteArray(value.length) { value[it].code.toByte() }
    override suspend fun inspect(source: String): ComicExportPage {
        val value = Json.parseToJsonElement(operation("inspect", sourceArgs(source))).jsonObject
        val size = value.getValue("size").jsonPrimitive.long
        if (size <= 0) throw PlatformIOException("本地图片缺失")
        return ComicExportPage(value.getValue("source").jsonPrimitive.content, size, imageExtension(binary(value.getValue("header").jsonPrimitive.content)))
    }
    private suspend fun copy(page: ComicExportPage, consume: suspend (ByteArray) -> Unit) {
        val opened = withContext(NonCancellable) {
            Json.parseToJsonElement(operation("open-source", sourceArgs(page.source))).jsonObject
        }
        val token = opened.getValue("token").jsonPrimitive.content
        try {
            currentCoroutineContext().ensureActive()
            if (opened.getValue("size").jsonPrimitive.long != page.size ||
                imageExtension(binary(opened.getValue("header").jsonPrimitive.content)) != page.extension)
                throw PlatformIOException("本地图片已变化，请重新选择章节")
            var copied = 0L
            while (copied < page.size) {
                currentCoroutineContext().ensureActive()
                val chunk = binary(operation("read", args("readToken" to token) + mapOf("size" to JsonPrimitive(page.size), "offset" to JsonPrimitive(copied))))
                if (chunk.isEmpty() || copied > page.size - chunk.size) throw PlatformIOException("图片读取不完整")
                consume(chunk); copied += chunk.size
            }
        } finally { withContext(NonCancellable) { operation("release-source", args("readToken" to token)) } }
    }
    private suspend fun output(token: String, name: String? = null, block: suspend (suspend (ByteArray) -> Unit) -> Unit) {
        val stream = withContext(NonCancellable) {
            operation(if (name == null) "open" else "member", args("token" to token) +
                if (name == null) emptyMap() else args("name" to name))
        }
        var complete = false
        try {
            currentCoroutineContext().ensureActive()
            block { bytes -> currentCoroutineContext().ensureActive(); operation("write", args("stream" to stream), bytes) }
            currentCoroutineContext().ensureActive()
            operation("close", args("stream" to stream)); complete = true
        } finally { if (!complete) withContext(NonCancellable) { operation("abort", args("stream" to stream)) } }
    }
    override suspend fun write(
        unit: ExportUnit, destination: String, directory: Boolean, format: ComicExportFormat,
        onCreated: suspend (String) -> Unit, onBytes: suspend (Long) -> Unit, onChapter: (String) -> Unit
    ): ComicExportOutput {
        val images = format == ComicExportFormat.IMAGES
        require(directory || !images) { "原图导出需要选择目录" }
        val chapter = if (images) unit.chapters.single() else null
        val values = args("destination" to destination, "name" to (chapter?.let(::chapterExportName) ?: unit.name),
            "comic" to comicExportName(unit.comic), "group" to (chapter?.let { exportGroupName(unit.comic, it.group) } ?: "")) +
            mapOf("directory" to JsonPrimitive(directory), "images" to JsonPrimitive(images))
        currentCoroutineContext().ensureActive()
        val result = withContext(NonCancellable) {
            Json.parseToJsonElement(operation("create", values)).jsonObject.also {
                // The created handle must be durable and tracked before cancellation can interrupt us.
                onCreated(it.getValue("token").jsonPrimitive.content)
            }
        }
        val token = result.getValue("token").jsonPrimitive.content
        currentCoroutineContext().ensureActive()
        if (images) {
            for (item in unit.chapters) {
                onChapter("${unit.comic.name}／${item.name}")
                for ((index, page) in item.pages.withIndex()) output(token, exportPageName(index, page)) { emit ->
                    copy(page) { bytes -> emit(bytes); onBytes(bytes.size.toLong()) }
                }
            }
            output(token, "manifest.json") { it(exportManifest(unit).encodeToByteArray()) }
        } else output(token) { writeComicArchive(unit, format, it, ::copy, onBytes, onChapter) }
        currentCoroutineContext().ensureActive()
        return ComicExportOutput(unit.id, token, result.getValue("name").jsonPrimitive.content, format.mimeType)
    }
    override suspend fun delete(uri: String): Boolean = try {
        operation("delete", args("token" to uri)) == "true"
    } catch (_: Exception) { false }
}
