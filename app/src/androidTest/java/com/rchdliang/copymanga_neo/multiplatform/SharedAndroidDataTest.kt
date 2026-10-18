package com.rchdliang.copymanga_neo.multiplatform

import android.content.Context
import android.content.ContextWrapper
import com.rchdliang.copymanga_neo.data.local.portable.platformDocumentStorage
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.rchdliang.copymanga_neo.data.image.portable.AndroidReaderImageIo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.rchdliang.copymanga_neo.data.repository.portable.*
import com.rchdliang.copymanga_neo.data.remote.portable.PortableSession
import com.rchdliang.copymanga_neo.data.storage.FileCacheStorage
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID

/** 仅使用隔离目录及合成凭据，不接触已有账号，也不调用线上写接口。 */
@RunWith(AndroidJUnit4::class)
class SharedAndroidDataTest {
    @Test fun documentStoreUsesAndroidCompatibleIoAndStrictUtf8() = runBlocking {
        val fixture = context()
        try {
            val storage = platformDocumentStorage(fixture.noBackupFilesDir.absolutePath)
            assertNull(storage.read("settings.json"))
            storage.write("settings.json", "中文😀")
            assertEquals("中文😀", storage.read("settings.json"))
            storage.write("settings.json", "替换")
            assertEquals("替换", storage.read("settings.json"))
            assertTrue(runCatching { storage.write("../escape", "invalid") }.isFailure)
            File(fixture.noBackupFilesDir, "settings.json").writeBytes(byteArrayOf(0xc3.toByte(), 0x28))
            assertTrue(runCatching { storage.read("settings.json") }.isFailure)
            storage.remove("settings.json")
            assertNull(storage.read("settings.json"))
        } finally { fixture.noBackupFilesDir.deleteRecursively() }
    }
    @Test fun androidImageShareUsesDeclaredFileProviderRoot() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = byteArrayOf(1, 2, 3)
        val path = AndroidReaderImageIo(target).publish("share-test-${UUID.randomUUID()}.jpg", bytes)
        try {
            val uri = FileProvider.getUriForFile(target, target.packageName + ".reader.images", File(path))
            assertArrayEquals(bytes, target.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
        } finally { File(path).delete() }
    }
    @Test fun realForegroundServiceAcknowledgesWhileActivityIsVisible() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val resumed = CompletableDeferred<AndroidSharedActivity>()
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        var launchedActivity: AndroidSharedActivity? = null
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is AndroidSharedActivity) {
                launchedActivity = activity
                if (stage == Stage.RESUMED) resumed.complete(activity)
            }
        }
        instrumentation.runOnMainSync { monitor.addLifecycleCallback(callback) }
        try {
            // 部分厂商系统禁止测试进程从后台弹出 Activity；以 shell 模拟外部启动，不放宽应用权限。
            val component = requireNotNull(target.packageManager.getLaunchIntentForPackage(target.packageName)?.component)
            val output = ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("am start -W -n ${component.flattenToString()}")
            ).bufferedReader().use { it.readText() }
            assertTrue(output, output.contains("Status: ok"))
            withTimeout(15_000) { resumed.await() }
            val graph = withTimeout(30_000) { target.sharedContainer.awaitGraph() }
            withTimeout(15_000) { target.sharedContainer.downloads.awaitReady() }
            assertSame(graph, target.sharedContainer.current)
        } finally {
            instrumentation.runOnMainSync {
                monitor.removeLifecycleCallback(callback)
                launchedActivity?.finish()
            }
        }
    }
    private fun context(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        return object : ContextWrapper(base) {
            private val directory = File(base.cacheDir, "credential-test-$id").apply { mkdirs() }
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File = directory
            override fun getPackageName(): String = base.packageName + ".test." + id
        }
    }
    @Test fun keystoreRoundTripAndTamperNeverReturnPlaintextCredentials() = runBlocking {
        val fixture = context()
        try {
            val storage = AndroidSecureCredentialStorage(fixture)
            val record = StoredCredential(PortableSession("synthetic-token", "user:test", "test"))
            storage.writeAll(mapOf(RequestMode.WEB to record))
            assertEquals(record, AndroidSecureCredentialStorage(fixture).readAll()[RequestMode.WEB])
            val file = File(fixture.noBackupFilesDir, "shared-credentials.bin")
            val encrypted = file.readBytes()
            assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("synthetic-token"))
            encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
            file.writeBytes(encrypted)
            assertTrue(runCatching { AndroidSecureCredentialStorage(fixture).readAll() }.isFailure)
            assertArrayEquals(encrypted, file.readBytes())
        } finally {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(fixture.packageName + ".shared.credentials.v1")
            fixture.noBackupFilesDir.deleteRecursively()
        }
    }
    @Test fun androidAtomicStreamRetainsCompleteImageWhenTransferFails() = runBlocking {
        val fixture = context()
        try {
            val storage = FileCacheStorage(fixture.noBackupFilesDir.absolutePath)
            storage.write("downloads/page", byteArrayOf(9))
            val result = runCatching {
                storage.writeStream("downloads/page") { emit -> emit(byteArrayOf(1)); error("truncated") }
            }
            assertTrue(result.isFailure)
            assertArrayEquals(byteArrayOf(9), storage.read("downloads/page", 10))
            assertEquals(listOf("downloads/page"), storage.list())
        } finally { fixture.noBackupFilesDir.deleteRecursively() }
    }
    @Test fun applicationOwnsOnePortableGraph() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = withTimeout(30_000) { context.sharedContainer.awaitGraph() }
        assertSame(first, context.sharedContainer.awaitGraph())
        assertTrue(first.repositories.users is PortableUserRepository)
        assertTrue(first.repositories.manga is PortableMangaRepository)
        assertSame(first, context.sharedContainer.current)
    }
}
