package com.rchdliang.copymanga_neo.multiplatform

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import android.text.format.Formatter
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.rchdliang.copymanga_neo.domain.model.ComicExportLocation
import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage
import com.rchdliang.copymanga_neo.domain.repository.NetworkLogRepository
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackDispatcher
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.downloads.DownloadNotificationPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.export.ComicExportPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.profile.ProfilePlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.ReaderPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.ScreenOrientationSetting
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.text.DateFormat
import java.util.Date
import kotlin.coroutines.resume

/**
 * Real Android boundary for the shared UI. Retain this model in the Activity's ViewModelStore,
 * then attach each replacement Activity before STARTED. Native result keys remain stable, so
 * rotation does not cancel an in-flight chooser or retain the destroyed Activity.
 */
class AndroidPlatformHost(context: Context, private val savedState: SavedStateHandle) : ViewModel(), UiPlatform, ReaderPlatform,
    ProfilePlatform, ComicExportPlatform, DownloadNotificationPlatform {
    private val app = context.applicationContext
    private var activity by mutableStateOf<ComponentActivity?>(null)
    override val backDispatcher = UiBackDispatcher()
    override val notificationSettingsUnavailableReason: String? = null
    private var readerPreviousOrientation: Int? = savedState["reader_original_orientation"]
    private val launchers = mutableMapOf<String, ActivityResultLauncher<Intent>>()
    private val waiting = mutableMapOf<String, CancellableContinuation<ActivityResult>?>()
    private var notificationLauncher: ActivityResultLauncher<String>? = null

    fun attach(activity: ComponentActivity) {
        this.activity = activity
        listOf(IMAGE, EXPORT, OPEN, CREATE, AVATAR).forEach { key ->
            launchers[key] = activity.activityResultRegistry.register(
                "copymanga:shared:$key", activity, ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val wasWaiting = waiting.containsKey(key)
                val continuation = waiting.remove(key)
                if (continuation?.isActive == true) {
                    clearPicker(key)
                    continuation.resume(result)
                } else if (wasWaiting) {
                    clearPicker(key) // The caller canceled; do not give its result to a later operation.
                } else if (key == AVATAR) {
                    // Profile preview bytes/URI are deliberately not restored after process death.
                    clearPicker(key)
                } else if (savedState.get<String>("picker:$key") != null) {
                    // Android can deliver before the restored route constructs its model.
                    savedState["picker-result:$key"] = result
                }
            }
        }
        notificationLauncher = activity.activityResultRegistry.register(
            "copymanga:shared:notifications", activity, ActivityResultContracts.RequestPermission()
        ) { }
    }

    fun detach(activity: ComponentActivity) {
        if (this.activity !== activity) return
        this.activity = null
        launchers.clear()
        notificationLauncher = null
        // Do not cancel continuations: the replacement Activity re-registers these same keys.
    }

    private fun requireActivity() = checkNotNull(activity) { "当前没有可交互的 Android 窗口" }

    private fun clearPicker(key: String) {
        savedState.remove<String>("picker:$key")
        savedState.remove<ActivityResult>("picker-result:$key")
    }

    private suspend fun choose(key: String, intent: Intent): ActivityResult = withContext(Dispatchers.Main.immediate) {
        check(!waiting.containsKey(key)) { "系统选择器仍在处理，请先完成或取消当前操作" }
        val launcher = checkNotNull(launchers[key]) { "系统选择器尚未就绪，请稍后重试" }
        val signature = listOf(intent.action, intent.type, intent.getStringExtra(Intent.EXTRA_TITLE),
            intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.joinToString()).joinToString("|")
        val previous = savedState.get<String>("picker:$key")
        val restoredResult = savedState.get<ActivityResult>("picker-result:$key")
        if (previous == signature && restoredResult != null) {
            clearPicker(key)
            return@withContext restoredResult
        }
        check(previous == null || previous == signature || restoredResult != null) {
            "先前的系统选择器尚未返回，请先完成或取消当前操作"
        }
        val resumeNativePicker = previous == signature
        if (!resumeNativePicker) clearPicker(key)
        suspendCancellableCoroutine { continuation ->
            waiting[key] = continuation
            continuation.invokeOnCancellation {
                // Reserve the key until the old result arrives, so it cannot complete a newer request.
                if (waiting[key] === continuation) waiting[key] = null
            }
            if (!resumeNativePicker) {
                savedState["picker:$key"] = signature
                try { launcher.launch(intent) }
                catch (error: Exception) {
                    waiting.remove(key)
                    clearPicker(key)
                    continuation.resumeWith(Result.failure(error))
                }
            }
        }
    }

    private fun selectedUri(result: ActivityResult, persist: Boolean = false): String? {
        if (result.resultCode != Activity.RESULT_OK) return null
        val uri = result.data?.data ?: return null
        if (persist) {
            val flags = result.data?.flags ?: 0
            val permissions = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0 && permissions != 0) {
                try { app.contentResolver.takePersistableUriPermission(uri, permissions) }
                catch (error: SecurityException) {
                    android.util.Log.w("SharedPicker", "本次授权可用，但无法保存重启后的文档访问权限", error)
                }
            }
        }
        return uri.toString()
    }

    override suspend fun chooseImageDestination(image: ReaderExportImage): Result<String?> = nativeResult {
        selectedUri(choose(IMAGE, createIntent(image.displayName, image.mimeType)), persist = true)
    }

    override suspend fun pickDestination(location: ComicExportLocation): Result<String?> = nativeResult {
        val intent = if (location.directory) Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(DOCUMENT_FLAGS)
        } else createIntent(location.name, location.mimeType)
        selectedUri(choose(EXPORT, intent), persist = true)
    }

    override suspend fun openDocument(mimeTypes: List<String>): Result<String?> = nativeResult {
        require(mimeTypes.isNotEmpty())
        selectedUri(choose(OPEN, Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeTypes.singleOrNull() ?: "*/*"
            if (mimeTypes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }), persist = true)
    }

    override suspend fun createDocument(name: String, mimeType: String): Result<String?> = nativeResult {
        selectedUri(choose(CREATE, createIntent(name, mimeType)), persist = true)
    }

    override suspend fun readDocument(reference: String, maxBytes: Int): Result<ByteArray> = nativeResult {
        require(maxBytes >= 0)
        withContext(Dispatchers.IO) {
            app.contentResolver.openInputStream(Uri.parse(reference))?.use { input ->
                ByteArrayOutputStream(minOf(maxBytes, 8192)).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(count <= maxBytes - output.size()) { "文件超过允许大小" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            } ?: error("无法打开所选文件")
        }
    }

    override suspend fun writeDocument(reference: String, bytes: ByteArray): Result<Unit> = nativeResult {
        withContext(Dispatchers.IO) {
            app.contentResolver.openOutputStream(Uri.parse(reference), "wt")?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: error("无法写入所选文件")
        }
    }

    override suspend fun exportNetworkLog(reference: String, repository: NetworkLogRepository): Result<Unit> = nativeResult {
        withContext(Dispatchers.IO) {
            app.contentResolver.openOutputStream(Uri.parse(reference), "wt")?.use { output ->
                repository.export(output).getOrThrow()
            } ?: error("无法写入网络记录")
        }
    }

    override suspend fun pickAvatarJpeg(): Result<ByteArray?> = nativeResult {
        val reference = selectedUri(choose(AVATAR, Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
        })) ?: return@nativeResult null
        val source = readDocument(reference, 10 * 1024 * 1024).getOrThrow()
        withContext(Dispatchers.IO) {
            // ImageDecoder applies EXIF orientation before we re-encode the bounded bitmap.
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(source))) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > 1024) decoder.setTargetSize(
                    (info.size.width * 1024L / longest).toInt().coerceAtLeast(1),
                    (info.size.height * 1024L / longest).toInt().coerceAtLeast(1)
                )
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            try {
                ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) { "图片转换失败" }
                    output.toByteArray()
                }
            } finally { bitmap.recycle() }
        }
    }

    override suspend fun shareImage(image: ReaderExportImage): Result<Unit> = nativeResult {
        withContext(Dispatchers.Main.immediate) {
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.reader.images", File(image.filePath))
            val target = Intent(Intent.ACTION_SEND).apply {
                type = image.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, image.displayName)
                clipData = ClipData.newRawUri(image.displayName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            requireActivity().startActivity(Intent.createChooser(target, "分享漫画图片").apply {
                clipData = target.clipData
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
    }

    override suspend fun shareText(title: String, text: String): Result<Unit> = nativeResult {
        withContext(Dispatchers.Main.immediate) {
            requireActivity().startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_TITLE, title)
            }, title))
        }
    }

    override suspend fun readClipboardText(): Result<String?> = nativeResult {
        withContext(Dispatchers.Main.immediate) {
            app.getSystemService(ClipboardManager::class.java).primaryClip?.let { clip ->
                if (clip.itemCount > 0) clip.getItemAt(0).coerceToText(app)?.toString() else null
            }
        }
    }

    override suspend fun copySensitiveText(label: String, text: String): Result<Unit> = nativeResult {
        withContext(Dispatchers.Main.immediate) {
            val clip = ClipData.newPlainText(label, text).apply {
                description.extras = PersistableBundle().apply {
                    putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE
                        else "android.content.extra.IS_SENSITIVE", true)
                }
            }
            app.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        }
    }

    override suspend fun openNotificationSettings(): Result<Unit> = nativeResult {
        withContext(Dispatchers.Main.immediate) {
            val current = requireActivity()
            val candidates = listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
            )
            var last: Exception? = null
            for (intent in candidates) {
                try { current.startActivity(intent); return@withContext }
                catch (error: android.content.ActivityNotFoundException) { last = error }
                catch (error: SecurityException) { last = error }
            }
            throw last ?: IllegalStateException("无法打开系统通知设置")
        }
    }

    override suspend fun requestDownloadNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        withContext(Dispatchers.Main.immediate) {
            if (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return@withContext
            val preferences = app.getSharedPreferences("download_notifications", Context.MODE_PRIVATE)
            if (!preferences.getBoolean("permission_requested", false)) {
                val launcher = notificationLauncher ?: return@withContext
                preferences.edit { putBoolean("permission_requested", true) }
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun formatDate(epochMilliseconds: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMilliseconds))
    override fun formatFileSize(bytes: Long): String = Formatter.formatFileSize(app, bytes)
    override fun edgeToEdgeDialogProperties(dismissOnBackPress: Boolean, dismissOnClickOutside: Boolean) = DialogProperties(
        dismissOnBackPress = dismissOnBackPress, dismissOnClickOutside = dismissOnClickOutside,
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false
    )
    @Composable
    override fun dynamicColorScheme(darkTheme: Boolean): ColorScheme =
        if (darkTheme) dynamicDarkColorScheme(app) else dynamicLightColorScheme(app)

    @Composable
    override fun ImmersiveEffect(orientation: ScreenOrientationSetting?) {
        val current = activity
        DisposableEffect(current) {
            val window = current?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            if (readerPreviousOrientation == null) {
                readerPreviousOrientation = current?.requestedOrientation
                savedState["reader_original_orientation"] = readerPreviousOrientation
            }
            val previousBehavior = controller?.systemBarsBehavior
            val statusVisible = window?.decorView?.let(ViewCompat::getRootWindowInsets)
                ?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
            controller?.hide(WindowInsetsCompat.Type.statusBars())
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            onDispose {
                if (statusVisible) controller?.show(WindowInsetsCompat.Type.statusBars())
                else controller?.hide(WindowInsetsCompat.Type.statusBars())
                previousBehavior?.let { controller.systemBarsBehavior = it }
                if (current?.isChangingConfigurations == false) {
                    readerPreviousOrientation?.let { current.requestedOrientation = it }
                    readerPreviousOrientation = null
                    savedState.remove<Int>("reader_original_orientation")
                }
            }
        }
        LaunchedEffect(current, orientation) {
            if (orientation != null) current?.requestedOrientation = when (orientation) {
                ScreenOrientationSetting.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                ScreenOrientationSetting.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                ScreenOrientationSetting.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                ScreenOrientationSetting.SYSTEM_DEFAULT -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    @Composable
    override fun KeepScreenOnEffect(enabled: Boolean) {
        val current = activity
        DisposableEffect(current, enabled) {
            val window = current?.window
            val previous = ((window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
            if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                if (previous) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    override fun onCleared() {
        waiting.values.filterNotNull().forEach { it.cancel() }
        waiting.clear()
        launchers.clear()
        activity = null
    }

    private companion object {
        const val IMAGE = "reader-image"
        const val EXPORT = "comic-export"
        const val OPEN = "open-document"
        const val CREATE = "create-document"
        const val AVATAR = "avatar"
        const val DOCUMENT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        fun createIntent(name: String, mime: String) = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = mime
            putExtra(Intent.EXTRA_TITLE, name); addFlags(DOCUMENT_FLAGS)
        }
    }
}

private suspend inline fun <T> nativeResult(crossinline action: suspend () -> T): Result<T> =
    try { Result.success(action()) }
    catch (error: CancellationException) { throw error }
    catch (error: Exception) { Result.failure(error) }
