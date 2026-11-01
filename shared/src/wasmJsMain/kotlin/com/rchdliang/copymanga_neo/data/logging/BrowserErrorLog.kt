@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package com.rchdliang.copymanga_neo.data.logging

import kotlin.JsFun

// 同步 localStorage 只用于有界诊断；不能在页面异常时依赖异步数据库提交。
@JsFun("(key) => localStorage.getItem('copymanga-diagnostics-' + key)")
private external fun readLog(key: String): String?
@JsFun("(key, text) => { localStorage.setItem('copymanga-diagnostics-' + key, text); }")
private external fun writeLog(key: String, text: String)
@JsFun("(key) => { localStorage.removeItem('copymanga-diagnostics-' + key); }")
private external fun removeLog(key: String)

@JsFun("""callback => {
    if (globalThis.__copyMangaErrorLogInstalled) return;
    globalThis.__copyMangaErrorLogInstalled = true;
    window.addEventListener('error', event => {
        callback('BrowserError', String(event.error?.stack || event.message || 'Unknown browser error'));
    });
    window.addEventListener('unhandledrejection', event => {
        callback('UnhandledRejection', String(event.reason?.stack || event.reason || 'Unknown rejection'));
    });
}""")
private external fun installBrowserErrorHooks(callback: (String, String) -> Unit)

fun installBrowserErrorLog() {
    AppErrorLog.install(ErrorLogStore(object : ErrorLogFiles {
        override fun <T> locked(block: () -> T): T = block()
        override fun read(name: String): String? = readLog(name)
        override fun write(name: String, text: String, durable: Boolean) = writeLog(name, text)
        override fun remove(name: String) = removeLog(name)
    }), "Web/WASM")
    installBrowserErrorHooks { tag, detail ->
        AppErrorLog.record("E", tag, "浏览器未处理异常", RuntimeException(detail), crash = true)
    }
}
