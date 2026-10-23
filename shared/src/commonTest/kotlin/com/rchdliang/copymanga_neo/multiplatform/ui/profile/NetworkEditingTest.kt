package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import androidx.lifecycle.ViewModelStore
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.NetworkSettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkEditingTest {
    private val stores = mutableListOf<ViewModelStore>()
    @BeforeTest fun before() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun after() { stores.forEach { it.clear() }; Dispatchers.resetMain() }
    private fun model(repo: FakeNetwork) = NetworkSettingsViewModel(repo).also { vm ->
        stores += ViewModelStore().also { it.put("network", vm) }
    }

    @Test fun fieldErrorsExplainInvalidInputWithoutEchoingPrivateValues() {
        assertNull(networkFieldError("登录域名（留空跟随 API）", ""))
        assertNotNull(networkFieldError("API 域名", ""))
        assertEquals("版本号须为大于 0 的整数", networkFieldError("版本号", "0"))
        assertNull(networkFieldError("SDK 标识", "abc 123"))
        assertFalse(networkFieldError("API 域名", "https://example.com/private?token=secret")!!.contains("secret"))
    }
    @Test fun unchangedAndNormalizedEquivalentHostsAreClean() {
        val saved = NetworkSettings()
        assertFalse(NetworkSettingsUiState(saved, saved).dirty)
        assertFalse(NetworkSettingsUiState(saved, saved.copy(apiHost = " https://${saved.apiHost}/ ")).dirty)
        assertTrue(NetworkSettingsUiState(saved, saved.copy(apiHost = "")).dirty)
        assertTrue(NetworkSettingsUiState(saved, saved.copy(apiHost = "https://${saved.apiHost}/unexpected")).dirty)
    }
    @Test fun editingBackToBaselineClearsDirtyAndDiscardRestoresIt() {
        val vm = model(FakeNetwork())
        val saved = vm.uiState.value.saved
        vm.edit { it.copy(proxyMode = ProxyMode.DIRECT) }
        assertTrue(vm.uiState.value.dirty)
        vm.edit { saved }
        assertFalse(vm.uiState.value.dirty)
        vm.edit { it.copy(apiHost = "") }; vm.discard()
        assertEquals(saved, vm.uiState.value.draft)
    }
    @Test fun previousAndDefaultsOnlyModifyDraftUntilSaved() {
        val repo = FakeNetwork(); val vm = model(repo)
        repo.previous = NetworkSettings(proxyMode = ProxyMode.DIRECT)
        vm.rollback()
        assertEquals(repo.previous, vm.uiState.value.draft)
        assertEquals(0, repo.saves)
        assertEquals(ProxyMode.SYSTEM, repo.settings.value.proxyMode)
        vm.save()
        assertEquals(1, repo.saves)
        vm.reset()
        assertEquals(ProxyMode.DIRECT, repo.settings.value.proxyMode)
        assertEquals(ProxyMode.SYSTEM, vm.uiState.value.draft.proxyMode)
    }
    @Test fun failedSaveKeepsDraftAndDoesNotNavigateOrExposeRawError() {
        val repo = FakeNetwork(); repo.fail = true; val vm = model(repo)
        vm.edit { it.copy(proxyMode = ProxyMode.DIRECT) }
        var navigated = false; vm.save { navigated = true }
        assertFalse(navigated); assertTrue(vm.uiState.value.dirty)
        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.error!!.contains("secret"))
        assertFalse(vm.uiState.value.busy)
        repo.fail = false; vm.save { navigated = true }
        assertTrue(navigated); assertFalse(vm.uiState.value.dirty)
        assertNull(vm.uiState.value.error)
    }
    @Test fun invalidInputNeverReachesPersistenceAndCanBeCorrected() {
        val repo = FakeNetwork(); val vm = model(repo)
        vm.edit { it.copy(apiHost = "https://example.com/private?token=secret") }
        vm.save()
        assertEquals(0, repo.saves); assertNotNull(vm.uiState.value.error)
        vm.edit { it.copy(apiHost = NetworkDefaults.API_HOST) }
        assertNull(vm.uiState.value.error); assertFalse(vm.uiState.value.dirty)
    }
    @Test fun leavingRouteDuringSaveDoesNotCancelTransactionOrAllowDuplicateSave() = runTest {
        val repo = FakeNetwork(); repo.gate = CompletableDeferred(); val vm = model(repo)
        vm.edit { it.copy(proxyMode = ProxyMode.DIRECT) }
        var calls = 0; vm.save { calls++ }
        assertTrue(vm.uiState.value.saving)
        vm.cancelWork(); vm.save { calls++ }
        vm.edit { it.copy(apiHost = "bad") }
        assertEquals(NetworkDefaults.API_HOST, vm.uiState.value.draft.apiHost)
        repo.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(1, calls); assertEquals(1, repo.saves)
        assertFalse(vm.uiState.value.saving)
    }
}

internal class FakeNetwork : NetworkSettingsRepository {
    override val settings = MutableStateFlow(NetworkSettings())
    override val officialAppDefaults = OfficialAppSettings()
    var previous = NetworkSettings()
    var saves = 0
    var fail = false
    var gate: CompletableDeferred<Unit>? = null
    override suspend fun previousSettings() = Result.success(previous)
    override suspend fun save(settings: NetworkSettings): Result<Unit> {
        saves++; gate?.await()
        return if (fail) Result.failure(IllegalStateException("https://private?token=secret"))
        else Result.success(Unit).also { this.settings.value = settings }
    }
    override fun generatePseudoId() = "id"
    override fun generateUmString() = "id"
    override suspend fun diagnoseOfficialApp() = Result.success("report")
    override suspend fun reset(): Result<Unit> = error("UI must not immediately reset persistence")
    override suspend fun rollback(): Result<Unit> = error("UI must not immediately roll back persistence")
    override suspend fun discover() = Result.success(Unit)
    override suspend fun test(settings: NetworkSettings) = Result.success(emptyList<NetworkProbe>())
    override suspend fun refreshIfNeeded() {}
}
