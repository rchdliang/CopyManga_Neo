package com.rchdliang.copymanga_neo.data.novel.portable

import com.rchdliang.copymanga_neo.domain.model.*

/** Cache operations publish only fully validated resources, atomically, under book + volume; mode/owner describe the request source only. */
interface PortableNovelCache {
    val generation: Long
    suspend fun readMetadata(key:String):String?
    suspend fun writeMetadata(key:String,value:String,expectedGeneration:Long)
    suspend fun read(mode:RequestMode,owner:String,path:String,id:String):NovelReadingVolume?
    suspend fun publish(mode:RequestMode,owner:String,path:String,id:String,metadataJson:String,textBytes:ByteArray):NovelReadingVolume
    suspend fun writeImage(mode:RequestMode,owner:String,path:String,id:String,url:String,bytes:ByteArray):NovelReadingVolume
    /** Persist explicit user intent independently of text/image completeness. */
    suspend fun markDownloadRequested(mode:RequestMode,owner:String,path:String,id:String)
    suspend fun list():List<NovelCachedVolume>
    suspend fun removeVolume(mode:RequestMode,owner:String,path:String,id:String)
    suspend fun removeBook(mode:RequestMode,owner:String,path:String)
    suspend fun clear()
    suspend fun usage():NovelCacheUsage
}
