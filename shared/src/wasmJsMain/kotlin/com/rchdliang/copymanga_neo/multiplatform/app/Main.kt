@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.window.ComposeViewport
import com.rchdliang.copymanga_neo.multiplatform.platform.BrowserPlatformHost
import kotlinx.coroutines.launch
import kotlin.JsFun

@JsFun("""callback => {
 const id='copymanga-lifecycle-'+crypto.randomUUID();
 const listener=()=>{ if(document.visibilityState==='hidden') callback(); };
 const hide=()=>callback();
 document.addEventListener('visibilitychange',listener); window.addEventListener('pagehide',hide);
 (globalThis.__copyMangaLifecycle || (globalThis.__copyMangaLifecycle=new Map())).set(id,()=>{
   document.removeEventListener('visibilitychange',listener); window.removeEventListener('pagehide',hide);
 });
 return id;
}""")
private external fun observeBrowserBackground(callback: () -> Unit): String
@JsFun("""id => { const map=globalThis.__copyMangaLifecycle; map?.get(id)?.(); map?.delete(id); }""")
private external fun stopObservingBrowserBackground(id: String)

@JsFun("""(back, exit) => {
 const id='copymanga-back-'+crypto.randomUUID();
 const previousGuard=history.state?.copyMangaBack;
 const hadPrevious=previousGuard ? history.state.copyMangaHadPrevious === true : (globalThis.navigation?.currentEntry ? navigation.currentEntry.index > 0 : history.length > 1);
 let armed=false, released=false, waiting=false;
 const arm=()=>{ if(!armed&&!released){ history.pushState({copyMangaBack:id,copyMangaHadPrevious:hadPrevious},'',location.href); armed=true; } };
 const pop=event=>{
   if(released)return;
   if(event.state?.copyMangaBack===id){armed=true;return;}
   if(!armed)return;
   armed=false;
   if(waiting){arm();return;}
   if(globalThis.__copyMangaPlatform?.activationGate){globalThis.__copyMangaPlatform.activationGate();arm();return;}
   if(back())arm(); else {waiting=true;arm();exit();}
 };
 // Repeats can arrive after a popup consumed the first Escape; never pop again.
 const key=event=>{if(event.key==='Escape'&&!event.defaultPrevented&&!event.repeat&&!event.altKey&&!event.ctrlKey&&!event.metaKey&&!event.shiftKey&&back())event.preventDefault();};
 const listen=()=>{window.addEventListener('popstate',pop);window.addEventListener('keydown',key);};
 const restore=event=>{if(event.persisted&&released){released=false;waiting=false;armed=history.state?.copyMangaBack===id;listen();arm();}};
 listen();window.addEventListener('pageshow',restore);
 const dispose=(permanent=true)=>{released=true;window.removeEventListener('popstate',pop);window.removeEventListener('keydown',key);if(permanent)window.removeEventListener('pageshow',restore);};
 if(previousGuard){history.replaceState({copyMangaBack:id,copyMangaHadPrevious:hadPrevious},'',location.href);armed=true;}else arm();
 (globalThis.__copyMangaBack || (globalThis.__copyMangaBack=new Map())).set(id,{
   resume:()=>{waiting=false;arm();}, dispose:()=>dispose(true),
   leave:()=>{waiting=false;if(hadPrevious){const steps=armed ? -2 : -1;dispose(false);history.go(steps);}else arm();}
 });
 return id;
}""")
private external fun installBrowserBack(back: () -> Boolean, exit: () -> Unit): String
@JsFun("""(id, action) => { const map=globalThis.__copyMangaBack; const value=map?.get(id); value?.[action]?.(); if(action==='dispose')map?.delete(id); }""")
private external fun browserBackAction(id: String, action: String)

/** Browser runtime configuration is public to page scripts and visitors; it is not secret storage. */
@JsFun("""() => {
 const value = globalThis.copyMangaNeoConfig?.tapString;
 return typeof value === 'string' ? value : '';
}""")
private external fun browserTapString(): String

/** HTTPS/localhost is required for secure file pickers and persistent browser storage. */
fun main() {
    val host = BrowserPlatformHost()
    ComposeViewport {
        PortableAppEntry(
            create = { BrowserGraph.create(it, host, tapString = browserTapString()).core },
            platforms = { AppPlatforms(host, host, host, host, host, it.imageLoader, host) },
            capabilityWarnings = host.capabilityWarnings,
            lifecycle = { graph, scope, report ->
                val latestGraph = rememberUpdatedState(graph)
                DisposableEffect(Unit) {
                    val token = observeBrowserBackground {
                        scope.launch { latestGraph.value?.repositories?.localReading?.flush()?.onFailure { report(it.message ?: "阅读记录保存失败") } }
                    }
                    var backToken = ""
                    backToken = installBrowserBack(host.backDispatcher::dispatchBack) {
                        scope.launch {
                            val saved = latestGraph.value?.repositories?.localReading?.flush() ?: Result.success(Unit)
                            saved.onSuccess { browserBackAction(backToken, "leave") }.onFailure {
                                browserBackAction(backToken, "resume")
                                report(it.message ?: "阅读记录尚未保存，未离开应用")
                            }
                        }
                    }
                    onDispose {
                        stopObservingBrowserBackground(token)
                        browserBackAction(backToken, "dispose")
                    }
                }
            }
        )
    }
}
