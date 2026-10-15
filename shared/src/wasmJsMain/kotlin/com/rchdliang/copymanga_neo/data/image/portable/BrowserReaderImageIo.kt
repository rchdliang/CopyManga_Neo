@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package com.rchdliang.copymanga_neo.data.image.portable

import com.rchdliang.copymanga_neo.domain.model.UserActionCancelled
import coil3.disk.DiskCache
import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage
import kotlinx.coroutines.await
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.JsFun
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.JsString
import kotlin.js.Promise

/** Persistent prepared bytes in IndexedDB; user output uses real File System Access handles.
 * The picker callback must be the same host instance that owns __copyMangaPlatform.files. */
class BrowserReaderImageIo(
    namespace: String,
    private val chooseDestination: suspend (ReaderExportImage) -> Result<String?>,
    private val diskCache: DiskCache? = null,
) : ReaderImageIo {
    private val namespace = namespace.also { require(it.matches(Regex("[A-Za-z0-9_.-]{1,80}"))) }
    private suspend fun operation(action: String, reference: String = "", name: String = "", payload: String = "", limit: Long = 0): String? =
        browserImageStore(namespace, action, reference, name, payload, limit.toDouble()).await()?.toString()

    override suspend fun prunePrepared(beforeMillis: Long) { operation("prune", limit = beforeMillis) }
    override suspend fun readCached(source: String, maximumBytes: Int): ByteArray? = readCoilReaderImageCache(diskCache, source, maximumBytes)
    override suspend fun readLocal(source: String, maximumBytes: Int): ByteArray = binary(
        requireNotNull(operation("local", source, limit = maximumBytes.toLong())), maximumBytes
    )
    override suspend fun inspect(bytes: ByteArray): ReaderImageMetadata = readerImageHeader(bytes)
    override suspend fun publish(displayName: String, bytes: ByteArray): String {
        currentCoroutineContext().ensureActive()
        return requireNotNull(operation("publish", name = displayName, payload = encoded(bytes)))
    }
    override suspend fun readPrepared(image: ReaderExportImage, maximumBytes: Int): ByteArray = binary(
        requireNotNull(operation("prepared", image.filePath, image.displayName, limit = maximumBytes.toLong())), maximumBytes
    )
    override suspend fun saveToGallery(image: ReaderExportImage, bytes: ByteArray) {
        // An anchor download cannot acknowledge that a file reached disk, so require a confirmed writer.
        val destination = chooseDestination(image).getOrThrow() ?: throw UserActionCancelled()
        write(destination, bytes)
    }
    override suspend fun saveToDocument(image: ReaderExportImage, destination: String, readValidatedBytes: suspend () -> ByteArray) {
        write(destination, readValidatedBytes())
    }
    private suspend fun write(destination: String, bytes: ByteArray) {
        currentCoroutineContext().ensureActive()
        suspendCancellableCoroutine<Unit> { continuation ->
            val token = browserImageWrite(destination, encoded(bytes),
                { if (continuation.isActive) continuation.resume(Unit) },
                { message -> if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message)) })
            continuation.invokeOnCancellation { cancelBrowserImageWrite(token) }
        }
    }
    private fun encoded(bytes: ByteArray): String = buildString(bytes.size) { bytes.forEach { append((it.toInt() and 255).toChar()) } }
    private fun binary(value: String, maximumBytes: Int): ByteArray {
        require(value.length <= maximumBytes) { "图片超过单张安全大小限制" }
        return ByteArray(value.length) { value[it].code.toByte() }
    }
}

@JsFun("""(namespace, action, reference, name, payload, limit) => {
 const state=globalThis.__copyMangaPlatform||(globalThis.__copyMangaPlatform={files:new Map(),effects:new Map(),counter:0});
 const handle=async ref=>{
   if(state.files.has(ref))return state.files.get(ref);
   if(!ref.startsWith('web-file:'))return null;
   const db=await new Promise((resolve,reject)=>{const request=indexedDB.open('copymanga-platform-handles',1);request.onupgradeneeded=()=>request.result.createObjectStore('handles');request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);request.onblocked=()=>reject(Error('文件句柄数据库被另一标签页阻止'));});
   try{return await new Promise((resolve,reject)=>{const request=db.transaction('handles').objectStore('handles').get(ref);request.onsuccess=()=>{if(request.result)state.files.set(ref,request.result);resolve(request.result||null);};request.onerror=()=>reject(request.error);});}finally{db.close();}
 };
 const prefix='web-image:'+namespace+':';
 const mime=name=>({'jpg':'image/jpeg','png':'image/png','gif':'image/gif','webp':'image/webp','avif':'image/avif','heic':'image/heic'}[name.split('.').pop()]||'application/octet-stream');
 const binary=async blob=>{if(blob.size>limit)throw Error('图片超过单张安全大小限制');const bytes=new Uint8Array(await blob.arrayBuffer());if(bytes.length>limit)throw Error('图片超过单张安全大小限制');let out='';for(let i=0;i<bytes.length;i+=8192)out+=String.fromCharCode(...bytes.subarray(i,i+8192));return out;};
 if(action==='local'&&!reference.startsWith(prefix))return (async()=>{
   const item=await handle(reference);if(item)return await binary(item.getFile?await item.getFile():item);
   if(!reference.startsWith('blob:'))throw Error('浏览器图片地址不是本地缓存');
   const response=await fetch(reference,{credentials:'omit'});if(!response.ok)throw Error('本地图片不存在，请重新下载');
   const expected=Number(response.headers.get('content-length'));if(Number.isFinite(expected)&&expected>limit){await response.body?.cancel();throw Error('图片超过单张安全大小限制');}
   const reader=response.body?.getReader();if(!reader)throw Error('此浏览器不支持有界图片读取');
   let total=0;const chunks=[];try{while(true){const next=await reader.read();if(next.done)break;total+=next.value.length;if(total>limit)throw Error('图片超过单张安全大小限制');chunks.push(next.value);}}catch(error){await reader.cancel();throw error;}finally{reader.releaseLock();}
   return await binary(new Blob(chunks));
 })();
 return new Promise((resolve,reject)=>{
   const request=indexedDB.open('copymanga-reader-images:'+namespace,1);let blocked=false;
   request.onupgradeneeded=()=>request.result.createObjectStore('exports');
   request.onerror=()=>reject(request.error);
   request.onblocked=()=>{blocked=true;reject(Error('图片缓存升级被另一标签页阻止'));};
   request.onsuccess=()=>{
     const db=request.result;if(blocked){db.close();return;}
     const tx=db.transaction('exports',['publish','prune'].includes(action)?'readwrite':'readonly');const store=tx.objectStore('exports');
     let result=null,completion=()=>{};
     const fail=error=>{try{tx.abort();}catch(_){}reject(error);};
     tx.onerror=()=>reject(tx.error);tx.onabort=()=>{db.close();reject(tx.error||Error('图片缓存事务已中止'));};
     tx.oncomplete=()=>{db.close();try{completion();resolve(result);}catch(error){reject(error);}};
     if(action==='publish'){
       const ref=prefix+name;const bytes=Uint8Array.from(payload,c=>c.charCodeAt(0));
       store.add({name,bytes:bytes.buffer,created:Date.now()},ref);result=ref;
       completion=()=>state.files.set(ref,new Blob([bytes],{type:mime(name)}));
     }else if(action==='prune'){
       const removed=[];const cursor=store.openCursor();cursor.onsuccess=()=>{const item=cursor.result;if(!item)return;
         if(item.value.created<limit){removed.push(item.key);item.delete();}item.continue();};
       completion=()=>removed.forEach(ref=>state.files.delete(ref));
     }else{
       if(!reference.startsWith(prefix)){fail(Error('图片地址不属于临时导出缓存'));return;}
       const read=store.get(reference);read.onsuccess=()=>{
         const entry=read.result;if(!entry){fail(Error('临时图片已过期，请重新选择图片'));return;}
         if(action==='prepared'&&entry.name!==name){fail(Error('图片文件名与缓存不符'));return;}
         if(entry.bytes.byteLength>limit){fail(Error('图片超过单张安全大小限制'));return;}
         const bytes=new Uint8Array(entry.bytes);let text='';for(let i=0;i<bytes.length;i+=8192)text+=String.fromCharCode(...bytes.subarray(i,i+8192));result=text;
         completion=()=>state.files.set(reference,new Blob([bytes],{type:mime(entry.name)}));
       };
     }
   };
 });
}""")
private external fun browserImageStore(namespace: String, action: String, reference: String, name: String, payload: String, limit: Double): Promise<JsString?>

@JsFun("""(reference,payload,success,failure)=>{
 const state=globalThis.__copyMangaPlatform||(globalThis.__copyMangaPlatform={files:new Map(),effects:new Map(),counter:0});
 const handle=async ref=>{
   if(state.files.has(ref))return state.files.get(ref);
   if(!ref.startsWith('web-file:'))return null;
   const db=await new Promise((resolve,reject)=>{const request=indexedDB.open('copymanga-platform-handles',1);request.onupgradeneeded=()=>request.result.createObjectStore('handles');request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);request.onblocked=()=>reject(Error('文件句柄数据库被另一标签页阻止'));});
   try{return await new Promise((resolve,reject)=>{const request=db.transaction('handles').objectStore('handles').get(ref);request.onsuccess=()=>{if(request.result)state.files.set(ref,request.result);resolve(request.result||null);};request.onerror=()=>reject(request.error);});}finally{db.close();}
 };
 const writes=globalThis.__copyMangaImageWrites||(globalThis.__copyMangaImageWrites=new Map());
 const id='write:'+crypto.randomUUID(),operation={cancelled:false,stream:null};writes.set(id,operation);
 (async()=>{
   const target=await handle(reference);if(!target?.createWritable)throw Error('所选文件没有写入权限，请重新选择');
   const stream=await target.createWritable();operation.stream=stream;
   try{
     if(operation.cancelled)throw Error('已取消保存图片');
     const bytes=Uint8Array.from(payload,c=>c.charCodeAt(0));
     for(let offset=0;offset<bytes.length;offset+=65536){if(operation.cancelled)throw Error('已取消保存图片');await stream.write(bytes.subarray(offset,offset+65536));}
     if(operation.cancelled)throw Error('已取消保存图片');await stream.close();
   }catch(error){try{await stream.abort();}catch(_){}throw error;}
 })().then(()=>{writes.delete(id);if(!operation.cancelled)success();},error=>{writes.delete(id);if(!operation.cancelled)failure(error?.message||String(error));});
 return id;
}""")
private external fun browserImageWrite(reference: String, payload: String, success: () -> Unit, failure: (String) -> Unit): String

@JsFun("""id=>{const operation=globalThis.__copyMangaImageWrites?.get(id);if(!operation)return;operation.cancelled=true;if(operation.stream)operation.stream.abort().catch(()=>{});}""")
private external fun cancelBrowserImageWrite(token: String)
