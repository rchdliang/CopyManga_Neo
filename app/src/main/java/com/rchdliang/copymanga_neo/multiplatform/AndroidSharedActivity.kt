package com.rchdliang.copymanga_neo.multiplatform

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rchdliang.copymanga_neo.BuildConfig
import com.rchdliang.copymanga_neo.data.download.DownloadNotifications
import com.rchdliang.copymanga_neo.data.export.ComicExportService
import com.rchdliang.copymanga_neo.domain.model.AppThemeMode
import com.rchdliang.copymanga_neo.domain.model.AppSettings
import kotlinx.coroutines.flow.catch
import com.rchdliang.copymanga_neo.multiplatform.app.AppPlatforms
import com.rchdliang.copymanga_neo.multiplatform.app.CopyMangaApp
import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiBackEvent
import com.rchdliang.copymanga_neo.multiplatform.ui.novel.AndroidNovelReaderPlatform
import com.rchdliang.copymanga_neo.multiplatform.ui.profile.AppBuildInfo
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Shared feature UI hosted by the original Android repository, storage and service graph. */
@AndroidEntryPoint
class AndroidSharedActivity : ComponentActivity() {
    @Inject lateinit var dependencies: AndroidSharedDependencies
    @Inject lateinit var imageLoaders: AndroidSharedImageLoaders
    private lateinit var host: AndroidPlatformHost
    private var openDownloads by mutableStateOf(false)
    private var retryExportId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = ViewModelProvider.create(this, viewModelFactory {
            initializer { AndroidPlatformHost(applicationContext, createSavedStateHandle()) }
        })[AndroidPlatformHost::class]
        host.attach(this)
        dependencies.repositories.risk.resetForAppLaunch(isFreshActivity = savedInstanceState == null)
        openDownloads = savedInstanceState?.getBoolean("openDownloads")
            ?: (intent.action == DownloadNotifications.OPEN_DOWNLOADS)
        retryExportId = if (savedInstanceState != null) savedInstanceState.getString("retryExportId")
            else intent.takeIf { it.action == ComicExportService.RETRY }?.getStringExtra(ComicExportService.TASK_ID)
        enableEdgeToEdge()
        val backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                host.backDispatcher.startGesture(backEvent.toUiEvent())
            }
            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                host.backDispatcher.progressGesture(backEvent.toUiEvent())
            }
            override fun handleOnBackCancelled() {
                host.backDispatcher.cancelGesture()
            }
            override fun handleOnBackPressed() {
                val handled = if (host.backDispatcher.isGestureInProgress)
                    host.backDispatcher.commitGesture() else host.backDispatcher.dispatchBack()
                if (!handled) {
                    isEnabled = false
                    try { onBackPressedDispatcher.onBackPressed() }
                    finally { isEnabled = true }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
        val platforms = AppPlatforms(
            ui = host, reader = host, novel = AndroidNovelReaderPlatform(this),
            export = host, profile = host, imageLoader = imageLoaders::get, notifications = host,
            buildInfo = AppBuildInfo("CopyManga Neo", BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE, BuildConfig.APPLICATION_ID, BuildConfig.DEBUG)
        )
        setContent {
            // Let Android own back-to-home when no shared screen can consume back.
            val handlesBack = host.backDispatcher.hasEnabledHandlers || host.backDispatcher.isGestureInProgress
            SideEffect { backCallback.isEnabled = handlesBack }
            val settingsFlow = remember(dependencies) {
                dependencies.repositories.appSettings.settings.catch { error ->
                    android.util.Log.e("SharedActivity", "应用设置读取失败", error)
                    emit(AppSettings())
                }
            }
            val settings by settingsFlow.collectAsStateWithLifecycle(null)
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings?.themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                else -> systemDark
            }
            SideEffect {
                val style = if (dark) SystemBarStyle.dark(Color.Transparent.toArgb())
                    else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            CopyMangaApp(
                dependencies.repositories, platforms, viewModelStoreOwner = this,
                openDownloads = openDownloads, onDownloadsOpened = { openDownloads = false },
                retryExportId = retryExportId, onExportRetryConsumed = { retryExportId = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.action) {
            DownloadNotifications.OPEN_DOWNLOADS -> openDownloads = true
            ComicExportService.RETRY -> retryExportId = intent.getStringExtra(ComicExportService.TASK_ID)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("openDownloads", openDownloads)
        outState.putString("retryExportId", retryExportId)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        host.backDispatcher.cancelGesture()
        super.onStop()
    }

    override fun onDestroy() {
        if (::host.isInitialized) host.detach(this)
        super.onDestroy()
    }
}

private fun BackEventCompat.toUiEvent() = UiBackEvent(
    progress = progress.coerceIn(0f, 1f),
    fromLeft = swipeEdge == BackEventCompat.EDGE_LEFT
)
