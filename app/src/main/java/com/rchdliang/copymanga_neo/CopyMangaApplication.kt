package com.rchdliang.copymanga_neo

import android.app.Application
import com.rchdliang.copymanga_neo.multiplatform.AndroidAppContainer

/** 进程级共享数据图；Android 服务和共享界面访问同一实例。 */
class CopyMangaApplication : Application() {
    lateinit var container: AndroidAppContainer
        private set
    override fun onCreate() {
        super.onCreate()
        // The disposable SR worker must not initialize databases, downloads or UI services.
        if (getProcessName().endsWith(":reader_sr")) return
        container = AndroidAppContainer(this)
    }
}
