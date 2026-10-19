package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.data.remote.portable.api.*
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import kotlin.io.encoding.Base64
import kotlin.random.Random

/** Session verification precedes publication. Password persistence requires a secure host backend. */
class PortableUserRepository(
    private val client:CopyMangaClient,
    private val storage:SecureCredentialStorage = MemorySecureCredentialStorage(),
    private val persistImportedConfiguration:(suspend (NetworkSettings,Long)->Unit)? = null,
) : UserRepository {
    private val api=MangaApiService(client)
    private val registration=RegistrationApiService(client)
    private val writes=MemberWriteApiService(client)
    private val credentials=MutableStateFlow<Map<RequestMode,StoredCredential>>(emptyMap())
    private val ready=MutableStateFlow(!storage.persistent)
    private val mutex=Mutex()
    init { client.setSessionInvalidationListener { mode,generation -> clearModeSecureSession(mode,generation) } }
    /** Call before showing authenticated content when injecting a persistent keychain backend. */
    suspend fun restoreSecureSessions()=mutex.withLock {
        if(ready.value)return@withLock
        try {
            val previous=storage.readAll()
            val loaded=previous.mapValues { (mode,stored) ->
                if(mode==RequestMode.OFFICIAL_APP&&stored.session.token.isNotBlank()&&stored.appIdentity==null)
                    stored.copy(session=PortableSession(),autoLogin=false) else stored
            }
            if(loaded!=previous)storage.writeAll(loaded)
            credentials.value=loaded
            client.state.update { snapshot ->
                val app=loaded[RequestMode.OFFICIAL_APP]
                val settings=if(app?.session?.token?.isNotBlank()==true&&app.appIdentity!=null)snapshot.settings.copy(officialApp=app.appIdentity.applyTo(snapshot.settings.officialApp)) else snapshot.settings
                snapshot.copy(settings=settings,webSession=loaded[RequestMode.WEB]?.session?:PortableSession(),appSession=app?.session?:PortableSession())
            }
        }finally{ready.value=true}
    }
    /** Invoke before applying a changed official identity; does not mutate network settings. */
    suspend fun clearModeSecureSession(mode:RequestMode,expectedGeneration:Long) {
        // Settings owns a different mutex while calling this hook. Never wait in reverse order.
        check(mutex.tryLock()){ "登录凭据操作正在进行，请稍后重试设置修改" }
        try {
            client.state.requireCurrent(expectedGeneration)
            val previous=storage.readAll()
            val cleared=(previous[mode]?:stored(mode)).copy(session=PortableSession(),autoLogin=false)
            storage.writeAll(previous+(mode to cleared))
            if(active().generation!=expectedGeneration) {
                withContext(NonCancellable){storage.writeAll(previous)}
                error("清理凭据期间会话已改变，请重试设置修改")
            }
            credentials.update{it+(mode to cleared)}
        } finally { mutex.unlock() }
    }
    private suspend fun awaitReady(){if(!ready.value)restoreSecureSessions()}
    private fun active()=client.state.snapshot
    private fun stored(mode:RequestMode=active().settings.requestMode)=credentials.value[mode]?:StoredCredential(active().session(mode))
    private fun requirePersistence(remember:Boolean,automatic:Boolean) {
        require(!automatic||remember){"自动登录需要记住密码"}
        require((!remember&&!automatic)||storage.persistent){"此平台没有安全凭据存储，无法记住密码或自动登录；请取消这两个选项"}
    }
    override suspend fun login(username:String,rawPassword:String,expectedGeneration:Long?):Result<LoginInfo> {
        awaitReady();val remembered=getSavedUsernameSync()==username&&getRememberPasswordSync()
        return loginWithOptions(username,rawPassword,remembered,remembered&&getAutoLoginSync(),expectedGeneration?:sessionVersionSync())
    }
    override suspend fun loginWithOptions(username:String,rawPassword:String,rememberPassword:Boolean,autoLogin:Boolean,expectedGeneration:Long):Result<LoginInfo> = result {
        awaitReady();requirePersistence(rememberPassword,autoLogin)
        require(username.isNotBlank()&&rawPassword.isNotEmpty()){"请输入账号和密码"}
        val snapshot=active();check(snapshot.generation==expectedGeneration){"登录期间会话或网络设置已改变"}
        val mode=snapshot.settings.requestMode;val salt=Random.nextInt(1000,10000).toString()
        val response=api.login(username=username,password=Base64.Default.encode("$rawPassword-$salt".encodeToByteArray()),salt=salt,requestMode=mode)
        client.state.requireCurrent(snapshot.generation)
        check(response.isSuccessful){"登录请求失败（HTTP ${response.code()}）"}
        val info=response.body()?.results?:error("登录响应为空")
        val token=info.token?.takeIf{it.isNotBlank()}?:response.headers.entries.firstOrNull{it.key.equals("set-cookie",true)}?.value?.firstNotNullOfOrNull { cookie->cookie.substringBefore(';').takeIf{it.startsWith("token=")}?.substringAfter('=')?.takeIf{it.isNotBlank()} }?:error("未能获取登录 Token")
        val verified=api.getMemberInfoWithCredential("Token $token",requestMode=mode)
        client.state.requireCurrent(snapshot.generation)
        check(verified.isSuccessful){"登录接口已返回，但会话验证失败，请检查登录与 API 域名"}
        val member=verified.body()?.results?:error("登录会话验证未返回用户信息")
        val name=member.username?.takeIf{it.isNotBlank()}?:info.username?.takeIf{it.isNotBlank()}?:username
        val session=PortableSession(token,requireNotNull(readingProgressOwner(true,name)),name,member.avatar?:info.avatar)
        val value=StoredCredential(session,username.takeIf{rememberPassword},rawPassword.takeIf{rememberPassword},rememberPassword,autoLogin,if(mode==RequestMode.OFFICIAL_APP)client.credentialIdentity() else null)
        commit(snapshot,mode,value,replaceSameAccount=true,accountAliases=setOf(username,name))
        info.toLoginInfo().copy(token=token,username=name,avatar=session.avatar)
    }
    private suspend fun commit(
        snapshot:NetworkSnapshot,mode:RequestMode,value:StoredCredential,settings:NetworkSettings=snapshot.settings,
        replaceSameAccount:Boolean=false,importedLogin:SavedLoginCredential?=null,accountAliases:Set<String> = emptySet()
    )=mutex.withLock {
        client.state.requireCurrent(snapshot.generation)
        val changes=mutableMapOf(mode to value)
        if(replaceSameAccount) {
            val names=(setOfNotNull(value.session.username,value.savedUsername)+accountAliases).filter{it.isNotBlank()}.toSet()
            RequestMode.entries.filter{it!=mode}.forEach { otherMode ->
                val old=stored(otherMode)
                val same=old.session.username in names||(old.session.username==null&&old.savedUsername in names)
                var next=if(same)old.copy(session=PortableSession(),autoLogin=false) else old
                if(importedLogin!=null&&(next.session.username==null||next.session.username in names)&&
                    (next.savedUsername==null||next.savedUsername in names)) {
                    next=next.copy(savedUsername=importedLogin.username,savedPassword=importedLogin.password,
                        rememberPassword=true,autoLogin=false)
                }
                if(next!=old)changes[otherMode]=next
            }
        }
        val previous=storage.readAll()
        val configurationChanged=settings!=snapshot.settings
        require(!configurationChanged||!storage.persistent||persistImportedConfiguration!=null){"安全凭据存储尚未连接配置事务，无法持久化导入"}
        var configurationWritten=false
        var credentialAttempted=false
        try {
            if(configurationChanged&&persistImportedConfiguration!=null) {
                // Mark first because a host write can fail after publishing its replacement file.
                configurationWritten=true
                persistImportedConfiguration.invoke(settings,snapshot.generation)
            }
            client.state.requireCurrent(snapshot.generation)
            credentialAttempted=true
            storage.writeAll(previous+changes)
            client.state.requireCurrent(snapshot.generation)
            val committed=client.state.updateIfCurrent(snapshot.generation){it.copy(settings=settings,
                appSession=changes[RequestMode.OFFICIAL_APP]?.session?:it.appSession,
                webSession=changes[RequestMode.WEB]?.session?:it.webSession)}
            check(committed){"保存期间会话或网络设置已改变"}
            credentials.update{it+changes}
        }catch(error:Exception) {
            withContext(NonCancellable) {
                if(credentialAttempted) {
                    try { storage.writeAll(previous) }
                    catch(rollback:Exception) { error.addSuppressed(rollback) }
                }
                if(configurationWritten&&active().generation==snapshot.generation) {
                    try { persistImportedConfiguration?.invoke(snapshot.settings,snapshot.generation) }
                    catch(rollback:Exception) { error.addSuppressed(rollback) }
                }
            }
            throw error
        }
    }
    override suspend fun register(username:String,rawPassword:String):Result<RegistrationInfo> = result {
        awaitReady();RegistrationPolicy.validationError(username,rawPassword)?.let{error(it)}
        check(!isLoggedInSync()){"请先退出当前账号后注册"};val snapshot=active()
        val response=if(snapshot.settings.requestMode==RequestMode.WEB)registration.registerWeb(username,rawPassword,version=snapshot.settings.version) else registration.register(username,rawPassword)
        check(response.isSuccessful&&response.body()?.code==200){"注册请求未成功（HTTP ${response.code()}）"}
        check(response.body()?.results!=null){"注册响应缺少账号信息，请先尝试登录，勿重复注册"}
        RegistrationInfo(username,active().generation==snapshot.generation)
    }
    override suspend fun getMemberInfo():Result<MemberInfo> = result {
        awaitReady();val snapshot=active();require(snapshot.session().token.isNotBlank()){"当前未登录"}
        val response=api.getMemberInfo();check(response.isSuccessful){if(response.code()==401)"会话已过期，请重新登录" else "获取用户信息失败（HTTP ${response.code()}）"}
        val member=response.body()?.results?:error("用户信息为空")
        client.state.requireCurrent(snapshot.generation)
        val name=member.username?.takeIf{it.isNotBlank()}?:snapshot.session().username
        updateMetadata(snapshot,name,member.avatar)
        member.toMemberInfo()
    }
    private suspend fun updateMetadata(snapshot:NetworkSnapshot,name:String?,avatar:String?)=mutex.withLock {
        client.state.requireCurrent(snapshot.generation)
        val mode=snapshot.settings.requestMode
        val previous=storage.readAll()
        val value=stored(mode).copy(session=snapshot.session(mode).withMemberMetadata(name,avatar))
        try {
            storage.writeAll(previous+(mode to value))
            client.state.requireCurrent(snapshot.generation)
            client.state.updateMetadata(snapshot.generation,name,avatar)
            credentials.update{it+(mode to value)}
        } catch(error:Exception) {
            withContext(NonCancellable) {
                try { storage.writeAll(previous) }
                catch(rollback:Exception) { error.addSuppressed(rollback) }
            }
            throw error
        }
    }
    override suspend fun getEditableProfile():Result<EditableProfile> = result {
        awaitReady();val snapshot=active();val token=snapshot.session().token;require(token.isNotBlank()){"请先登录"}
        val value=editable(token);client.state.requireCurrent(snapshot.generation);value
    }
    private suspend fun editable(token:String):EditableProfile {
        val result=client.results(ApiRequest("/api/v3/member/update/info",headers=CopyMangaNetworkConfig.noAuthApiHeaders()+mapOf("Authorization" to "Token $token")))
        val info=result["info"] as? JsonObject?:error("账号资料为空")
        fun text(key:String)=(info[key] as? JsonPrimitive)?.contentOrNull
        val gender=info["gender"]?.takeUnless{it==JsonNull}?.let { value->(if(value is JsonObject)value["value"] else value)?.jsonPrimitive?.content?.toIntOrNull()?:error("账号性别数据格式异常，请重新加载资料") }?:0
        return EditableProfile(text("nickname").orEmpty(),text("avatar"),text("avatar_rp"),gender)
    }
    override suspend fun updateProfile(nickname:String,avatarJpeg:ByteArray?,gender:Int?):Result<MemberInfo> = result {
        require(gender == null || ProfileGender.entries.any { it.value == gender }) { "请选择有效的性别" }
        awaitReady();require(nickname.isNotBlank()){"请输入昵称"};val snapshot=active();val token=snapshot.session().token;require(token.isNotBlank()){"请先登录"}
        val current=editable(token);client.state.requireCurrent(snapshot.generation)
        val headers=CopyMangaNetworkConfig.noAuthApiHeaders()+mapOf("Authorization" to "Token $token")
        val avatar=if(avatarJpeg!=null) {
            require(avatarJpeg.isNotEmpty()&&avatarJpeg.size<=5*1024*1024){"头像文件为空或过大"}
            val response=client.call(ApiRequest("/api/v3/upload/image/1",method="POST",headers=headers,avatarJpeg=avatarJpeg),serializer<JsonObject>())
            client.state.requireCurrent(snapshot.generation)
            check(response.isSuccessful && response.body()?.get("code")?.jsonPrimitive?.intOrNull == 200) {
                (response.body()?.get("message") as? JsonPrimitive)?.contentOrNull ?: "头像上传失败"
            }
            (response.body()?.get("results") as? JsonPrimitive)?.contentOrNull?.takeIf{it.isNotBlank()}?:error("头像上传未返回路径")
        } else current.avatarPath?:current.avatar.orEmpty()
        client.state.requireCurrent(snapshot.generation)
        val saved=client.call(ApiRequest("/api/v3/member/update/info",method="POST",form=listOf("nickname" to nickname.trim(),"avatar" to avatar,"gender" to (gender ?: current.gender).toString()),headers=headers),serializer<JsonObject>())
        check(saved.isSuccessful&&saved.body()?.get("code")?.jsonPrimitive?.int==200) {
            (saved.body()?.get("message") as? JsonPrimitive)?.contentOrNull ?: "资料保存未确认，请刷新检查"
        }
        try { client.state.requireCurrent(snapshot.generation);getMemberInfo().getOrThrow() }catch(e:CancellationException){throw e}catch(e:Exception){throw ProfileSavedRefreshException("资料已保存，但刷新失败，请关闭后刷新账号页",e)}
    }
    override suspend fun exportLoginCredential(includePassword:Boolean):Result<String> = result {
        awaitReady();val snapshot=active();val mode=snapshot.settings.requestMode;val session=snapshot.session();require(session.token.isNotBlank()){"请先登录"}
        val response=api.getMemberInfoWithCredential("Token ${session.token}",requestMode=mode)
        require(response.isSuccessful&&response.body()?.results!=null){"当前模式会话验证失败，请重新登录后导出"};client.state.requireCurrent(snapshot.generation)
        val saved=stored(mode);val login=if(includePassword) {
            val user=saved.savedUsername?.takeIf{it.isNotBlank()}?:error("没有保存的登录账号")
            val password=saved.savedPassword?.takeIf{it.isNotBlank()}?:error("没有保存的登录密码")
            SavedLoginCredential(user,password)
        }else null
        PortableCredentialCodec.encode(LoginCredentialBundle(mode,listOf(LoginCredential(mode,session.token,requireNotNull(session.username))),if(mode==RequestMode.OFFICIAL_APP)client.credentialIdentity() else null,login))
    }
    override suspend fun importLoginCredential(serializedCredential:String):Result<CredentialImportResult> = result {
        awaitReady();val snapshot=active();val bundle=PortableCredentialCodec.decode(serializedCredential)
        if(bundle.savedLogin!=null)requirePersistence(true,false)
        val credential=bundle.sessions.single{it.mode==bundle.activeMode};val mode=credential.mode
        val response=api.getMemberInfoWithCredential("Token ${credential.token}",requestMode=mode,appIdentity=bundle.appIdentity.takeIf{mode==RequestMode.OFFICIAL_APP})
        require(response.isSuccessful){"服务端拒绝此登录凭据，请在原设备重新登录后导出"}
        val member=response.body()?.results?:error("登录凭据未返回用户信息");client.state.requireCurrent(snapshot.generation)
        val name=member.username?.takeIf{it.isNotBlank()}?:credential.username
        val session=PortableSession(credential.token,requireNotNull(readingProgressOwner(true,name)),name,member.avatar)
        val settings=snapshot.settings.copy(requestMode=mode,officialApp=bundle.appIdentity?.takeIf{mode==RequestMode.OFFICIAL_APP}?.applyTo(snapshot.settings.officialApp)?:snapshot.settings.officialApp)
        val capturedIdentity=if(mode==RequestMode.OFFICIAL_APP)bundle.appIdentity?:client.credentialIdentity() else null
        val value=StoredCredential(session,bundle.savedLogin?.username,bundle.savedLogin?.password,bundle.savedLogin!=null,false,capturedIdentity)
        commit(snapshot,mode,value,settings,replaceSameAccount=true,importedLogin=bundle.savedLogin)
        CredentialImportResult(member.toMemberInfo(),mode,active().generation,bundle.sessions.map{it.mode}.filter{it!=mode}.toSet())
    }
    override suspend fun logout():Result<Unit> = result {
        awaitReady();val snapshot=active();val mode=snapshot.settings.requestMode;val token=snapshot.session().token
        commit(snapshot,mode,stored(mode).copy(session=PortableSession(),autoLogin=false))
        if(mode==RequestMode.OFFICIAL_APP&&token.isNotBlank())try { writes.logout("Token $token",requestMode=mode) }catch(e:CancellationException){throw e}catch(_:Exception){ /* Local logout remains valid if remote confirmation is unavailable. */ }
    }
    override fun sessionChanges():Flow<Long> = client.state.snapshots.map{it.generation}.distinctUntilChanged()
    override fun sessionVersionSync()=active().generation
    override fun isLoggedIn():Flow<Boolean> = client.state.snapshots.map{it.session().token.isNotBlank()}.distinctUntilChanged()
    override fun isAuthCacheReady():Flow<Boolean> = ready.asStateFlow()
    override fun isLoggedInSync()=active().session().token.isNotBlank()
    override fun getUsername():Flow<String?> = client.state.snapshots.map{it.session().username}.distinctUntilChanged()
    override fun getAvatar():Flow<String?> = client.state.snapshots.map{it.session().avatar}.distinctUntilChanged()
    override suspend fun saveCredentials(username:String,password:String,rememberPassword:Boolean,autoLogin:Boolean,expectedGeneration:Long) {
        awaitReady();requirePersistence(rememberPassword,autoLogin)
        mutex.withLock {
            client.state.requireCurrent(expectedGeneration);val mode=active().settings.requestMode
            if(active().session(mode).token.isBlank())return@withLock
            val previous=storage.readAll()
            val value=stored(mode).copy(savedUsername=username.takeIf{rememberPassword},savedPassword=password.takeIf{rememberPassword},rememberPassword=rememberPassword,autoLogin=autoLogin)
            try {
                storage.writeAll(previous+(mode to value))
                client.state.requireCurrent(expectedGeneration)
                credentials.update{it+(mode to value)}
            } catch(error:Exception) {
                withContext(NonCancellable){storage.writeAll(previous)}
                throw error
            }
        }
    }
    private fun <T> preference(selector:(StoredCredential)->T):Flow<T> = combine(client.state.snapshots,credentials){snapshot,values->selector(values[snapshot.settings.requestMode]?:StoredCredential(snapshot.session()))}.distinctUntilChanged()
    override fun getRememberPassword()=preference{it.rememberPassword}
    override fun getAutoLogin()=preference{it.autoLogin}
    override fun getSavedUsername()=preference{it.savedUsername}
    override fun getSavedPassword()=preference{it.savedPassword}
    override fun getAutoLoginSync()=stored().autoLogin
    override fun getRememberPasswordSync()=stored().rememberPassword
    override fun getSavedUsernameSync()=stored().savedUsername
    override fun getSavedPasswordSync()=stored().savedPassword
    private suspend fun <T> result(block:suspend()->T):Result<T> = try{Result.success(block())}catch(e:CancellationException){throw e}catch(e:Exception){Result.failure(e)}
}
