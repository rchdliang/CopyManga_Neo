@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package com.rchdliang.copymanga_neo.data.remote.portable

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okio.Buffer
import kotlin.JsFun
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun platformLocalImageFetcherFactory(timeoutSeconds:()->Int):Fetcher.Factory<Uri>? =
    object:Fetcher.Factory<Uri> {
        override fun create(data:Uri,options:Options,imageLoader:ImageLoader):Fetcher? {
            if(!data.toString().startsWith("blob:"))return null
            return Fetcher {
                val payload=withTimeout(timeoutSeconds().coerceIn(5,60)*1000L) {
                    suspendCancellableCoroutine<Pair<ByteArray,String?>> { continuation ->
                        val token=readLocalBlob(data.toString(),MAXIMUM_BLOB_BYTES,
                            { binary,mime ->
                                if(continuation.isActive) {
                                    if(binary.length>MAXIMUM_BLOB_BYTES)continuation.resumeWithException(IllegalStateException("本地图片超过单张安全大小限制"))
                                    else continuation.resume(ByteArray(binary.length){binary[it].code.toByte()} to mime.takeIf{it.isNotBlank()})
                                }
                            },
                            { message -> if(continuation.isActive)continuation.resumeWithException(IllegalStateException(message)) })
                        continuation.invokeOnCancellation{cancelLocalBlob(token)}
                    }
                }
                SourceFetchResult(
                    source=ImageSource(Buffer().write(payload.first),options.fileSystem),
                    mimeType=payload.second,
                    dataSource=DataSource.MEMORY,
                )
            }
        }
    }

private const val MAXIMUM_BLOB_BYTES=64*1024*1024

@JsFun("""(address,limit,success,failure)=>{
 const operations=globalThis.__copyMangaBlobReads||(globalThis.__copyMangaBlobReads=new Map());
 const id=crypto.randomUUID(),controller=new AbortController();operations.set(id,controller);
 (async()=>{
   const url=new URL(address);
   if(url.protocol!=='blob:'||url.origin!==globalThis.location.origin)throw Error('图片地址不是本地缓存');
   const response=await fetch(address,{credentials:'omit',redirect:'error',mode:'same-origin',signal:controller.signal});
   if(!response.ok)throw Error('本地图片不存在，请重新下载');
   const declared=response.headers.get('content-length');
   if(declared!==null&&Number(declared)>limit){await response.body?.cancel();throw Error('本地图片超过单张安全大小限制');}
   const reader=response.body?.getReader();if(!reader)throw Error('此浏览器不支持有界图片读取');
   let total=0;const chunks=[];
   try{
     while(true){
       const next=await reader.read();if(next.done)break;
       total+=next.value.byteLength;if(total>limit)throw Error('本地图片超过单张安全大小限制');
       chunks.push(next.value);
     }
   }catch(error){await reader.cancel();throw error;}finally{reader.releaseLock();}
   let binary='';for(const chunk of chunks)for(let i=0;i<chunk.length;i+=8192)binary+=String.fromCharCode(...chunk.subarray(i,i+8192));
   if(!controller.signal.aborted)success(binary,response.headers.get('content-type')||'');
 })().catch(error=>{if(!controller.signal.aborted)failure(error?.message||String(error));}).finally(()=>operations.delete(id));
 return id;
}""")
private external fun readLocalBlob(address:String,limit:Int,success:(String,String)->Unit,failure:(String)->Unit):String

@JsFun("""id=>{const controller=globalThis.__copyMangaBlobReads?.get(id);if(controller)controller.abort();}""")
private external fun cancelLocalBlob(token:String)
