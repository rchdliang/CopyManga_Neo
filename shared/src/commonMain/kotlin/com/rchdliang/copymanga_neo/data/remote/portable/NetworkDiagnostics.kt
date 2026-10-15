package com.rchdliang.copymanga_neo.data.remote.portable

import com.rchdliang.copymanga_neo.domain.model.RequestMode

/** Deliberately excludes raw URLs, query values, forms, headers, server messages and credentials. */
data class NetworkDiagnostic(
    val phase:String,
    val method:String,
    val host:String,
    val endpoint:String,
    val mode:RequestMode,
    val generation:Long,
    val status:Int? = null,
    val businessCode:Int? = null,
    val elapsedMillis:Long? = null,
    val bytes:Int? = null,
    val failureKind:String? = null,
    val outcomeUnknown:Boolean = false,
)
internal fun redactedEndpoint(path:String):String {
    val known=setOf("api","v3","h5","homeIndex","homeIndex2","comic","comic2","comics","book","books","search","ranks","update","newest","topics","topic","contents","theme","count","filter","tags","group","chapters","chapter2","chapter2download","comicdetail","chapter","member","collect","browse","info","login","logout","register","query","recs","bookcomments","bookcomment","comment","comments","roast","roasts","config","report","upload","image","system","network21","volumes","volume")
    return path.split('/').joinToString("/"){if(it.isEmpty()||it in known)it else "{id}"}
}
