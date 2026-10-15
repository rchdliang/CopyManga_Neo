package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.domain.model.*
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64

internal data class LoginCredential(val mode:RequestMode,val token:String,val username:String)
internal data class SavedLoginCredential(val username:String,val password:String)
internal data class LoginCredentialBundle(val activeMode:RequestMode,val sessions:List<LoginCredential>,val appIdentity:AppCredentialIdentity?=null,val savedLogin:SavedLoginCredential?=null)
internal object PortableCredentialCodec {
    private const val prefix="copymanga-neo:v3:"
    private const val legacy="copymanga-neo:v2:"
    private val base64=Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
    fun encode(bundle:LoginCredentialBundle):String {
        validate(bundle);require(bundle.sessions.size==1){"只能导出当前模式的登录凭据"}
        require(bundle.activeMode!=RequestMode.OFFICIAL_APP||bundle.appIdentity!=null){"App 凭据缺少请求身份"}
        val root=buildJsonObject {
            put("active_mode",bundle.activeMode.name)
            bundle.savedLogin?.let { login -> put("saved_login",buildJsonObject{put("username",login.username);put("password",login.password)}) }
            bundle.appIdentity?.let { i->put("app_identity",buildJsonObject{put("version",i.version);put("version_code",i.versionCode);put("pseudo_id",i.pseudoId);put("um_string",i.umString);put("device",i.device);put("device_info",i.deviceInfo);put("build_time",i.buildTime)}) }
            put("sessions",JsonArray(bundle.sessions.map { s->buildJsonObject{put("mode",s.mode.name);put("token",s.token);put("username",s.username)} }))
        }
        return (prefix+base64.encode(root.toString().encodeToByteArray())).also{require(it.length<=32768){"登录凭据过长"}}
    }
    fun decode(serialized:String):LoginCredentialBundle {
        val text=serialized.trim();require(text.isNotEmpty()&&text.length<=32768){"登录凭据为空或过长"}
        val start=listOf(prefix,legacy).firstOrNull{text.startsWith(it)}?:error("凭据格式不支持，请使用新版重新导出")
        val root=Json.parseToJsonElement(base64.decode(text.removePrefix(start)).decodeToString(throwOnInvalidSequence=true)).jsonObject
        fun str(o:JsonObject,key:String):String {val value=requireNotNull(o[key] as? JsonPrimitive);require(value.isString);return value.content}
        fun mode(o:JsonObject,key:String)=RequestMode.valueOf(str(o,key))
        val sessions=requireNotNull(root["sessions"] as? JsonArray).map{val item=it.jsonObject;LoginCredential(mode(item,"mode"),str(item,"token"),str(item,"username"))}
        val identity=if(start==prefix)(root["app_identity"] as? JsonObject)?.let { i->AppCredentialIdentity(str(i,"version"),str(i,"version_code"),str(i,"pseudo_id"),str(i,"um_string"),str(i,"device"),str(i,"device_info"),str(i,"build_time")) } else null
        val saved=if(start==prefix)(root["saved_login"] as? JsonObject)?.let{SavedLoginCredential(str(it,"username"),str(it,"password"))} else null
        return LoginCredentialBundle(mode(root,"active_mode"),sessions,identity,saved).also{validate(it);if(start==prefix&&sessions.any{it.mode==RequestMode.OFFICIAL_APP})require(identity!=null){"App 凭据缺少请求身份"}}
    }
    private fun validate(b:LoginCredentialBundle) {
        require(b.sessions.size in 1..RequestMode.entries.size&&b.sessions.map{it.mode}.distinct().size==b.sessions.size&&b.sessions.any{it.mode==b.activeMode}){"登录凭据模式无效"}
        b.sessions.forEach{require(it.token.isNotBlank()&&it.token.length<=8192&&it.username.isNotBlank()&&it.username.length<=256){"登录凭据内容无效"}}
        b.savedLogin?.let{require(b.sessions.size==1&&it.username.isNotBlank()&&it.username.length<=256&&it.password.isNotBlank()&&it.password.length<=4096){"保存的登录信息无效"}}
        b.appIdentity?.let { i->
            require(b.sessions.any{it.mode==RequestMode.OFFICIAL_APP}&&i.version.matches(Regex("[A-Za-z0-9._-]{1,64}"))&&i.versionCode.toIntOrNull()?.let{it>0}==true){"App 请求身份无效"}
            listOf(i.pseudoId,i.umString,i.device,i.deviceInfo,i.buildTime).forEach{require(it.isNotBlank()&&it.length<=256&&it.all{c->c.code in 32..126}){"App 请求身份格式无效"}}
        }
    }
}
