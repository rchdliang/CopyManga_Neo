@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package com.rchdliang.copymanga_neo.multiplatform.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import com.rchdliang.copymanga_neo.domain.model.ComicExportLocation
import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage
import com.rchdliang.copymanga_neo.domain.repository.NetworkLogRepository
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackDispatcher
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.downloads.DownloadNotificationPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.export.ComicExportPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.novel.NovelReaderPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.profile.ProfilePlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.ReaderPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.ScreenOrientationSetting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import com.rchdliang.copymanga_neo.domain.model.asUserActionResult
import kotlin.JsFun
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64

/** Browser capabilities are permission-checked at the call site; unsupported saves fail visibly. */
class BrowserPlatformHost : UiPlatform, ReaderPlatform, NovelReaderPlatform, ComicExportPlatform,
    ProfilePlatform, DownloadNotificationPlatform {
    override val notificationSettingsUnavailableReason: String?
        get() = if (browserNotificationsSupported()) null else "此浏览器不支持下载通知"
    override val backDispatcher = UiBackDispatcher()
    @Composable override fun ExternalUiThemeEffect(colors: ColorScheme) {
        val palette = listOf(colors.surfaceContainerHigh, colors.onSurface, colors.primary, colors.onPrimary, colors.outlineVariant)
            .joinToString(",") { "#" + it.toArgb().toUInt().toString(16).padStart(8, '0').takeLast(6) }
        SideEffect { setBrowserActionPalette(palette) }
    }
    private val warnings = MutableStateFlow<List<String>>(emptyList())
    val capabilityWarnings: StateFlow<List<String>> = warnings
    private fun warn(message: String) { if (message !in warnings.value) warnings.value += message }

    @Composable override fun ImmersiveEffect(orientation: ScreenOrientationSetting?) {
        DisposableEffect(orientation != null) {
            val token = if (orientation != null) beginBrowserEffect("fullscreen", ::warn) else null
            if (orientation != null && orientation != ScreenOrientationSetting.AUTO && orientation != ScreenOrientationSetting.SYSTEM_DEFAULT)
                warn("浏览器无法保证恢复用户原来的屏幕方向锁定；请使用设备方向设置")
            onDispose { token?.let(::endBrowserEffect) }
        }
    }
    @Composable override fun KeepScreenOnEffect(enabled: Boolean) {
        DisposableEffect(enabled) {
            val token = if (enabled) beginBrowserEffect("awake", ::warn) else null
            onDispose { token?.let(::endBrowserEffect) }
        }
    }
    @Composable override fun ReaderWindow(keepScreenOn: Boolean, controlsVisible: Boolean, lightBars: Boolean) {
        DisposableEffect(controlsVisible) {
            val token = if (!controlsVisible) beginBrowserEffect("fullscreen", ::warn) else null
            onDispose { token?.let(::endBrowserEffect) }
        }
        KeepScreenOnEffect(keepScreenOn)
    }
    override fun formatDate(epochMilliseconds: Long) = browserDate(epochMilliseconds.toDouble())
    override fun plainTextFromHtml(html: String) = browserPlainText(html)
    override fun formatLineHeight(value: Float) = browserDecimal(value.toDouble()) + " 倍"
    override fun formatFileSize(bytes: Long): String {
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var n = bytes.coerceAtLeast(0).toDouble(); var index = 0
        while (n >= 1000 && index < units.lastIndex) { n /= 1000; index++ }
        return if (index == 0) "${n.toLong()} B" else browserDecimal(n) + " " + units[index]
    }
    override suspend fun shareText(title: String, text: String) = operation("shareText", "title" to title, "text" to text).asUserActionResult()
    override suspend fun readClipboardText() = operation("readClipboard")
    override suspend fun copySensitiveText(label: String, text: String): Result<Unit> {
        warn("浏览器无法控制系统剪贴板预览或历史记录，请使用后及时清除敏感内容")
        return operation("copy", "text" to text).asUserActionResult()
    }
    override suspend fun openDocument(mimeTypes: List<String>) = operation("open", "accept" to mimeTypes.joinToString(","))
    override suspend fun createDocument(name: String, mimeType: String) = operation("save", "name" to name, "mime" to mimeType)
    override suspend fun chooseImageDestination(image: ReaderExportImage) = createDocument(image.displayName, image.mimeType)
    override suspend fun shareImage(image: ReaderExportImage) = operation("shareImage", "reference" to image.filePath,
        "name" to image.displayName, "mime" to image.mimeType).asUserActionResult()
    override suspend fun pickDestination(location: ComicExportLocation) = if (location.directory) operation("directory")
        else createDocument(location.name, location.mimeType)
    override suspend fun readDocument(reference: String, maxBytes: Int): Result<ByteArray> {
        if (maxBytes < 0) return Result.failure(IllegalArgumentException("读取大小不能为负数"))
        return operation("read", "reference" to reference, "maxBytes" to maxBytes.toString()).mapCatching {
            Base64.decode(requireNotNull(it))
        }
    }
    override suspend fun writeDocument(reference: String, bytes: ByteArray) = operation("write",
        "reference" to reference, "base64" to Base64.encode(bytes)).map { Unit }
    override suspend fun pickAvatarJpeg(): Result<ByteArray?> = operation("avatar").mapCatching { it?.let(Base64::decode) }
    override suspend fun exportNetworkLog(reference: String, repository: NetworkLogRepository): Result<Unit> {
        var stream: String? = null
        return try {
            stream = requireNotNull(operation("streamOpen", "reference" to reference).getOrThrow())
            repository.exportChunks { bytes ->
                var offset = 0
                while (offset < bytes.size) {
                    currentCoroutineContext().ensureActive()
                    val end = minOf(bytes.size, offset + 65536)
                    operation("streamWrite", "reference" to requireNotNull(stream), "base64" to Base64.encode(bytes.copyOfRange(offset, end))).getOrThrow()
                    offset = end
                }
            }.getOrThrow()
            operation("streamClose", "reference" to requireNotNull(stream)).getOrThrow()
            stream = null
            Result.success(Unit)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
        finally { stream?.let { token -> withContext(NonCancellable) { operation("streamAbort", "reference" to token) } } }
    }
    override suspend fun openNotificationSettings(): Result<Unit> = operation("notificationSettings").map {}
    override suspend fun requestDownloadNotificationPermissionOnce() { operation("notifications").onFailure { warn(it.message ?: "无法请求通知权限") } }

    private suspend fun operation(name: String, vararg arguments: Pair<String, String>): Result<String?> {
        val json = buildJsonObject { arguments.forEach { put(it.first, it.second) } }.toString()
        return suspendCancellableCoroutine { continuation ->
            try {
                val token = browserOperation(name, json,
                    { value -> if (continuation.isActive) continuation.resume(Result.success(value)) },
                    { error -> if (continuation.isActive) continuation.resume(Result.failure(IllegalStateException(error))) })
                continuation.invokeOnCancellation { cancelBrowserOperation(token) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (continuation.isActive) continuation.resume(Result.failure(error)) }
        }
    }
}

@JsFun("palette => { globalThis.__copyMangaActionPalette = palette.split(','); }")
private external fun setBrowserActionPalette(palette: String)

@JsFun("(ms) => { const d=new Date(ms); return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0'); }")
private external fun browserDate(milliseconds: Double): String
@JsFun("(n) => n.toLocaleString(undefined,{minimumFractionDigits:2,maximumFractionDigits:2,useGrouping:false})")
private external fun browserDecimal(number: Double): String
@JsFun("""(html) => { const text=html.replace(/<(script|style)[^>]*>[\s\S]*?<\/\1\s*>/gi,'').replace(/<br\s*\/?>/gi,'\n').replace(/<\/(p|div|h[1-6]|blockquote)\s*>/gi,'\n\n').replace(/<[^>]*>/g,''); const field=document.createElement('textarea'); field.innerHTML=text.replace(/</g,'&lt;').replace(/>/g,'&gt;'); return field.value; }""")
private external fun browserPlainText(html: String): String

@JsFun("""(operation, json, success, failure) => {
 const args=JSON.parse(json);
 const state=globalThis.__copyMangaPlatform || (globalThis.__copyMangaPlatform={files:new Map(),effects:new Map(),counter:0});
 const operations=state.operations||(state.operations=new Map()),id='operation:'+crypto.randomUUID(),pending={cancelled:false,abort:null};operations.set(id,pending);
 const check=()=>{if(pending.cancelled)throw Error('操作已取消');};
 const streams=state.streams||(state.streams=new Map());
 const database=()=>new Promise((resolve,reject)=>{const request=indexedDB.open('copymanga-platform-handles',1);request.onupgradeneeded=()=>request.result.createObjectStore('handles');request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);request.onblocked=()=>reject(Error('文件句柄数据库被另一标签页阻止'));});
 const put=async value=>{const id='web-file:'+crypto.randomUUID();state.files.set(id,value);
   if(value.kind==='file'||value.kind==='directory'){const db=await database();try{await new Promise((resolve,reject)=>{const tx=db.transaction('handles','readwrite');tx.objectStore('handles').put(value,id);tx.oncomplete=()=>resolve();tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(tx.error||Error('保存文件句柄失败'));});}finally{db.close();}}return id;};
 const handle=async ref=>{if(state.files.has(ref))return state.files.get(ref);if(!ref.startsWith('web-file:'))return null;const db=await database();try{return await new Promise((resolve,reject)=>{const request=db.transaction('handles').objectStore('handles').get(ref);request.onsuccess=()=>{if(request.result)state.files.set(ref,request.result);resolve(request.result||null);};request.onerror=()=>reject(request.error);});}finally{db.close();}};
 const resolve=async ref=>{const value=await handle(ref);if(!value)throw Error('文件句柄已失效，请重新选择文件');return value.getFile?await value.getFile():value;};
 const base64=async blob=>{const bytes=new Uint8Array(await blob.arrayBuffer());let s='';for(let i=0;i<bytes.length;i+=8192)s+=String.fromCharCode(...bytes.subarray(i,i+8192));return btoa(s);};
 // Prepared payloads stay in this closure until the user confirms or cancels.
 // Invoke action synchronously in the real DOM click, before any Promise/await.
 const activate=(label,action)=>new Promise((resolve,reject)=>{
   check();state.activationGate?.();const previous=document.activeElement;
   const palette=globalThis.__copyMangaActionPalette||['#f3edf7','#1d1b20','#6650a4','#ffffff','#cac4d0'];
   const overlay=document.createElement('div');overlay.style.cssText='position:fixed;inset:0;z-index:2147483647;background:#0008;display:grid;place-items:center;padding:24px';
   const panel=document.createElement('section');panel.setAttribute('role','dialog');panel.setAttribute('aria-modal','true');panel.setAttribute('aria-label',label);
   panel.style.cssText='width:min(100%,420px);box-sizing:border-box;padding:24px;border-radius:28px;background:'+palette[0]+';color:'+palette[1]+';font:16px/1.5 system-ui;box-shadow:0 8px 40px #0006';
   const title=document.createElement('h2');title.textContent=label;title.style.cssText='font-size:24px;font-weight:500;margin:0 0 12px';
   const message=document.createElement('p');message.textContent='已准备好。请点击继续打开浏览器系统界面。';message.setAttribute('role','status');
   const proceed=document.createElement('button');proceed.textContent='继续';
   const cancel=document.createElement('button');cancel.textContent='取消';
   for(const button of [proceed,cancel]){button.type='button';button.style.cssText='font:inherit;font-weight:600;min-height:48px;padding:10px 24px;margin:8px 8px 0 0;border-radius:24px;cursor:pointer;border:1px solid '+palette[4]+';background:transparent;color:'+palette[1];}
   proceed.style.cssText+=';background:'+palette[2]+';color:'+palette[3]+';border-color:'+palette[2];
   panel.append(title,message,proceed,cancel);overlay.append(panel);document.body.append(overlay);
   const siblings=Array.from(document.body.children).filter(element=>element!==overlay).map(element=>[element,element.inert]);siblings.forEach(([element])=>element.inert=true);
   let done=false,busy=false;
   const finish=(value,error)=>{if(done)return;done=true;overlay.remove();siblings.forEach(([element,inert])=>element.inert=inert);if(state.activationGate===abort)delete state.activationGate;if(previous?.isConnected)previous.focus();if(error)reject(error);else resolve(value);};
   const abort=()=>finish(null,new DOMException('操作已取消','AbortError'));
   pending.abort=abort;state.activationGate=abort;
   cancel.onclick=abort;
   overlay.onkeydown=event=>{
     if(event.key==='Escape'){event.preventDefault();event.stopPropagation();if(!busy)abort();}
     if(event.key==='Tab'){event.preventDefault();event.stopPropagation();(document.activeElement===proceed?cancel:proceed).focus();}
   };
   proceed.onclick=()=>{
     if(done||busy||pending.cancelled)return;busy=true;proceed.disabled=true;cancel.disabled=true;
     let result;try{result=action();}catch(error){failed(error);return;}
     Promise.resolve(result).then(value=>finish(value),failed);
   };
   function failed(error){
     if(done)return;
     if(error?.name==='AbortError'){finish(null,error);return;}
     busy=false;proceed.disabled=false;cancel.disabled=false;
     message.textContent=operation==='copy'||operation==='readClipboard'?'剪贴板操作未完成，请重试或取消':error?.message||String(error);proceed.textContent='重试';proceed.focus();
   }
   proceed.focus();
 });
 const choose=accept=>new Promise(resolve=>{
   const input=document.createElement('input');input.type='file';input.accept=accept;input.style.display='none';document.body.appendChild(input);
   let done=false;const finish=file=>{if(done)return;done=true;input.remove();resolve(file);};
   pending.abort=()=>finish(null);input.addEventListener('change',()=>finish(input.files[0]||null),{once:true});
   input.addEventListener('cancel',()=>finish(null),{once:true});input.click();
 });
 (async()=>{
   switch(operation){
    case 'shareText': if(!navigator.share)throw Error('此浏览器不支持系统分享，请复制链接');await activate('分享链接',()=>navigator.share({title:args.title,text:args.text}));return '';
    case 'readClipboard': if(!navigator.clipboard?.readText)throw Error('浏览器不允许读取剪贴板');return await activate('读取剪贴板',()=>navigator.clipboard.readText());
    case 'copy': if(!navigator.clipboard?.writeText)throw Error('浏览器不允许写入剪贴板');await activate('复制到剪贴板',()=>navigator.clipboard.writeText(args.text));return '';
    case 'fullscreen': {
      const token=state.effects.get(args.lease);if(!token||token.released)return '';
      await activate('进入阅读全屏',()=>{
        if(token.released||state.fullscreenOwner!==token)throw new DOMException('操作已取消','AbortError');
        return token.element.requestFullscreen().then(()=>{
          const owner=state.fullscreenOwner;if(!owner||owner.element!==token.element)return;
          if(owner.released){if(document.fullscreenElement===owner.element)document.exitFullscreen().catch(()=>{});state.fullscreenOwner=null;}
          else owner.owned=true;
        });
      });return '';
    }
    case 'open': {const file=await choose(args.accept);return file?put(file):null;}
    case 'save': if(!globalThis.showSaveFilePicker)throw Error('此浏览器不支持可确认的文件保存，请使用支持File System Access的浏览器或桌面版');{const selected=await activate('选择保存位置',()=>showSaveFilePicker({suggestedName:args.name}));check();return put(selected);}
    case 'directory': if(!globalThis.showDirectoryPicker)throw Error('此浏览器不支持选择可写文件夹');{const selected=await activate('选择导出文件夹',()=>showDirectoryPicker({mode:'readwrite'}));check();return put(selected);}
    case 'read': return await base64((await resolve(args.reference)).slice(0,Number(args.maxBytes)));
    case 'write': {
      const target=await handle(args.reference);if(!target?.createWritable)throw Error('所选文件没有写入权限');
      const raw=atob(args.base64), bytes=Uint8Array.from(raw,c=>c.charCodeAt(0));const stream=await target.createWritable();
      pending.abort=()=>stream.abort().catch(()=>{});try{check();for(let offset=0;offset<bytes.length;offset+=65536){check();await stream.write(bytes.subarray(offset,offset+65536));}check();await stream.close();}catch(error){try{await stream.abort();}catch(_){}throw error;}return '';
    }
    case 'streamOpen': {
      const target=await handle(args.reference);if(!target?.createWritable)throw Error('所选文件没有写入权限');
      const stream=await target.createWritable();pending.abort=()=>stream.abort().catch(()=>{});
      try{check();const reference='web-stream:'+crypto.randomUUID();streams.set(reference,stream);return reference;}catch(error){await stream.abort();throw error;}
    }
    case 'streamWrite': {
      const stream=streams.get(args.reference);if(!stream)throw Error('日志写入流已关闭');pending.abort=()=>stream.abort().catch(()=>{});
      check();const bytes=Uint8Array.from(atob(args.base64),c=>c.charCodeAt(0));await stream.write(bytes);check();return '';
    }
    case 'streamClose': {const stream=streams.get(args.reference);if(!stream)throw Error('日志写入流已关闭');pending.abort=()=>stream.abort().catch(()=>{});check();await stream.close();streams.delete(args.reference);return '';}
    case 'streamAbort': {const stream=streams.get(args.reference);streams.delete(args.reference);if(stream)await stream.abort();return '';}
    case 'avatar': {
      const file=await choose('image/*');if(!file)return null;if(file.size>10*1024*1024)throw Error('头像原图不能超过10MiB');
      const image=await createImageBitmap(file,{imageOrientation:'from-image'});
      try{const ratio=Math.min(1,1024/Math.max(image.width,image.height));const canvas=document.createElement('canvas');canvas.width=Math.max(1,Math.round(image.width*ratio));canvas.height=Math.max(1,Math.round(image.height*ratio));
      const context=canvas.getContext('2d');if(!context)throw Error('无法建立头像画布');context.fillStyle='#fff';context.fillRect(0,0,canvas.width,canvas.height);context.drawImage(image,0,0,canvas.width,canvas.height);
      const blob=await new Promise((resolve,reject)=>canvas.toBlob(x=>x?resolve(x):reject(Error('JPEG编码失败')),'image/jpeg',0.9));return await base64(blob);}finally{image.close();}
    }
    case 'shareImage': {
      let blob;if(await handle(args.reference))blob=await resolve(args.reference);else {const response=await fetch(args.reference);if(!response.ok)throw Error('读取分享图片失败');blob=await response.blob();}
      const file=new File([blob],args.name,{type:args.mime});if(!navigator.share||!navigator.canShare?.({files:[file]}))throw Error('此浏览器不支持图片分享，请保存图片后分享');await activate('分享图片',()=>navigator.share({files:[file]}));return '';
    }
    case 'notificationSettings':
    case 'notifications': {
      if(!globalThis.Notification)throw Error('此浏览器不支持下载通知');const key='copymanga-notification-requested';
      if(Notification.permission==='granted'||Notification.permission==='denied'){
        if(operation==='notificationSettings')throw Error(Notification.permission==='granted'
          ? '通知权限已开启。如需关闭，请打开浏览器地址栏的网站权限设置'
          : '通知权限已被阻止。请打开浏览器地址栏的网站权限设置，允许此站点发送通知');
        return '';
      }
      // Old versions persisted an attempt before a gesture-blocked prompt. Heal that state.
      localStorage.removeItem(key);
      const result=await activate('开启下载通知',()=>Notification.requestPermission().then(value=>{
        if(value==='default')throw Error('尚未授予通知权限，请点击重试或取消');return value;
      }));
      if(result==='granted'||result==='denied')localStorage.setItem(key,result);
      if(result!=='granted')throw Error('未获通知权限，下载状态仍显示在应用内');return '';
    }
    default: throw Error('未知浏览器操作');
   }
 })().then(value=>{operations.delete(id);if(!pending.cancelled)success(value);},error=>{operations.delete(id);if(pending.cancelled)return;if(error?.name==='AbortError'&&['open','save','directory','avatar','readClipboard','copy','shareText','shareImage'].includes(operation))success(null);else if(error?.name==='AbortError'&&['notifications','notificationSettings','fullscreen'].includes(operation))success('');else failure(error?.message||String(error));});
 return id;
}""")
private external fun browserOperation(operation: String, arguments: String, success: (String?) -> Unit, failure: (String) -> Unit): String

@JsFun("""id=>{const pending=globalThis.__copyMangaPlatform?.operations?.get(id);if(!pending)return;pending.cancelled=true;if(pending.abort)pending.abort();}""")
private external fun cancelBrowserOperation(token: String)

private fun beginBrowserEffect(kind: String, failure: (String) -> Unit): String =
    beginBrowserEffectNative(kind, failure, { lease ->
        browserOperation("fullscreen", buildJsonObject { put("lease", lease) }.toString(), {}, failure)
    }, ::cancelBrowserOperation)

@JsFun("""(kind, failure, requestFullscreen, cancelOperation) => {
 const state=globalThis.__copyMangaPlatform||(globalThis.__copyMangaPlatform={files:new Map(),effects:new Map(),counter:0});
 const id='effect:'+String(++state.counter), token={kind,released:false,owned:false,sentinel:null};state.effects.set(id,token);
 if(kind==='awake'){
   const request=()=>{if(token.released||document.visibilityState!=='visible'||token.sentinel)return;if(!navigator.wakeLock){failure('此浏览器不支持屏幕常亮');return;}
     navigator.wakeLock.request('screen').then(lock=>{if(token.released){lock.release();return;}token.sentinel=lock;lock.addEventListener('release',()=>{token.sentinel=null;});}).catch(e=>failure(e.message));};
   token.listener=request;document.addEventListener('visibilitychange',request);request();
 }else {
   const previous=state.fullscreenOwner;
   token.element=document.documentElement;
   token.fullscreenListener=()=>{if(document.fullscreenElement!==token.element)token.owned=false;};
   document.addEventListener('fullscreenchange',token.fullscreenListener);
   if(previous?.operation)cancelOperation(previous.operation);
   // A newer lease owns our existing or in-flight fullscreen; old cleanup cannot exit it.
   if(previous){token.owned=previous.owned;state.fullscreenOwner=token;}
   if(!document.fullscreenElement){
     if(!token.element.requestFullscreen)failure('此浏览器不支持全屏');
     else {state.fullscreenOwner=token;token.operation=requestFullscreen(id);token.cancelOperation=cancelOperation;}
   }
 }

 return id;
}""")
private external fun beginBrowserEffectNative(kind: String, failure: (String) -> Unit, requestFullscreen: (String) -> String, cancelOperation: (String) -> Unit): String
@JsFun("""(id) => {const state=globalThis.__copyMangaPlatform,token=state?.effects.get(id);if(!token)return;token.released=true;if(token.fullscreenListener)document.removeEventListener('fullscreenchange',token.fullscreenListener);if(token.listener)document.removeEventListener('visibilitychange',token.listener);if(token.sentinel)token.sentinel.release().catch(()=>{});if(token.operation)token.cancelOperation(token.operation);if(state.fullscreenOwner===token&&token.owned){if(document.fullscreenElement===token.element)document.exitFullscreen().catch(()=>{});state.fullscreenOwner=null;}state.effects.delete(id);}""")
private external fun endBrowserEffect(token: String)

@JsFun("() => !!globalThis.Notification")
private external fun browserNotificationsSupported(): Boolean
