package com.rchdliang.copymanga_neo.ui.history

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rchdliang.copymanga_neo.domain.model.BrowseHistoryItem
import com.rchdliang.copymanga_neo.domain.model.BrowseHistoryPage
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class HistoryViewModelTest {
    private val item = BrowseHistoryItem(
        1,
        "示例",
        "example",
        null,
        emptyList(),
        "read",
        "第1话",
        "latest",
        "第2话"
    )
    private val page = BrowseHistoryPage(listOf(item), null)
    private val repository = FakeMangaRepository(page)
    private val user = FakeUserRepository()

    @Test
    fun onlyLoadsAfterLoginAndResetsOnLogout() = runBlocking {
        withViewModel { vm ->
            vm.uiState.first { !it.isCheckingLogin }
            assertEquals(0, repository.loadCalls)
            user.loggedIn.value = true
            vm.uiState.first { it.items.isNotEmpty() }
            assertEquals(1, repository.loadCalls)
            user.loggedIn.value = false
            vm.uiState.first { !it.isLoggedIn }
            assertTrue(vm.uiState.value.items.isEmpty())
        }
    }

    @Test
    fun failedClearKeepsRecordsAndUnconfirmedClearSendsNothing() = runBlocking {
        user.loggedIn.value = true
        withViewModel { vm ->
            vm.uiState.first { it.items.isNotEmpty() }
            withContext(Dispatchers.Main) { vm.confirmClear() }
            assertEquals(0, repository.clearCalls)
            repository.clearResult = Result.failure(Exception("清空失败"))
            withContext(Dispatchers.Main) { vm.requestClear(); vm.confirmClear() }
            vm.uiState.first { it.toastMessage == "清空失败" }
            assertEquals(listOf(item), vm.uiState.value.items)
            assertFalse(vm.uiState.value.clearCompleted)
            assertEquals(1, repository.clearCalls)
        }
    }

    @Test
    fun inFlightRefreshCannotRestoreHistoryAfterClear() = runBlocking {
        user.loggedIn.value = true
        withViewModel { vm ->
            vm.uiState.first { it.items.isNotEmpty() }
            val pending = CompletableDeferred<BrowseHistoryPage>()
            repository.pendingPage = pending
            withContext(Dispatchers.Main) { vm.refresh() }
            vm.uiState.first { it.isRefreshing }
            withContext(Dispatchers.Main) { vm.requestClear(); vm.confirmClear() }
            vm.uiState.first { it.clearCompleted }
            withContext(Dispatchers.Main) { pending.complete(page) }
            withContext(Dispatchers.Main) { assertTrue(vm.uiState.value.items.isEmpty()) }
            assertEquals(1, repository.clearCalls)
        }
    }

    @Test
    fun switchingAccountReloadsAndClearsOldItems() = runBlocking {
        user.loggedIn.value = true
        withViewModel { vm ->
            vm.uiState.first { it.items.isNotEmpty() }
            val pending = CompletableDeferred<BrowseHistoryPage>()
            repository.pendingPage = pending
            user.usernameState.value = "second-account"
            vm.uiState.first { it.isRefreshing && it.items.isEmpty() }
            pending.complete(BrowseHistoryPage(emptyList(), null))
            vm.uiState.first { !it.isRefreshing }
            assertTrue(vm.uiState.value.items.isEmpty())
            assertEquals(2, repository.loadCalls)
        }
    }

    private suspend fun withViewModel(block: suspend (HistoryViewModel) -> Unit) =
        withTimeout(10.seconds) {
            val store = ViewModelStore()
            val vm = withContext(Dispatchers.Main) {
                HistoryViewModel(
                    repository, user,
                    com.rchdliang.copymanga_neo.testing.InMemoryReadingProgressRepository(repository)
                ).also { store.put("history", it) }
            }
            try {
                block(vm)
            } finally {
                withContext(Dispatchers.Main + NonCancellable) { store.clear() }
            }
        }

    private class FakeMangaRepository(private val page: BrowseHistoryPage) :
        MangaRepository by unsupported() {
        var loadCalls = 0
        var clearCalls = 0
        var clearResult = Result.success(Unit)
        var pendingPage: CompletableDeferred<BrowseHistoryPage>? = null
        override suspend fun getBrowseHistory(offset: Int): Result<BrowseHistoryPage> {
            loadCalls++
            // 模拟不响应取消的迟到请求，验证 ViewModel 的结果保护。
            val pending = pendingPage
            return Result.success(if (pending == null) page else withContext(NonCancellable) { pending.await() })
        }

        override suspend fun clearBrowseHistory(): Result<Unit> {
            clearCalls++
            return clearResult
        }
    }

    private class FakeUserRepository : UserRepository by unsupported() {
        val loggedIn = MutableStateFlow(false)
        val usernameState = MutableStateFlow<String?>("first-account")
        override fun isLoggedIn() = loggedIn
        override fun getUsername() = usernameState
        override fun isAuthCacheReady() = flowOf(true)
    }

    companion object {
        private inline fun <reified T> unsupported(): T = Proxy.newProxyInstance(
            T::class.java.classLoader, arrayOf(T::class.java)
        ) { _, method, _ -> error("Unexpected call: ${method.name}") } as T
    }
}
