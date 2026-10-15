package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.domain.model.DownloadTaskStatus

/** Pure close policy: paused work never forces a background session. */
fun desktopHasActiveTransfers(statuses: Iterable<DownloadTaskStatus>, exporting: Boolean): Boolean =
    exporting || statuses.any { it in setOf(DownloadTaskStatus.WAITING, DownloadTaskStatus.WAITING_FOR_WIFI,
        DownloadTaskStatus.REQUESTING_PAGE, DownloadTaskStatus.DOWNLOADING) }

enum class DesktopWindowPhase {
    OPEN, CONFIRM_CLOSE, FLUSH_BACKGROUND, BACKGROUND, FLUSH_QUIT;
    val showApplication: Boolean get() = this == OPEN || this == CONFIRM_CLOSE
    val visible: Boolean get() = this != BACKGROUND
    val flushing: Boolean get() = this == FLUSH_BACKGROUND || this == FLUSH_QUIT
}

fun desktopCloseRequest(activeTransfers: Boolean): DesktopWindowPhase =
    if (activeTransfers) DesktopWindowPhase.CONFIRM_CLOSE else DesktopWindowPhase.FLUSH_QUIT

/** Availability must be checked again after the asynchronous flush, not only on button click. */
fun desktopBackgroundAfterFlush(tray: DesktopNotificationStatus): DesktopWindowPhase =
    if (tray == DesktopNotificationStatus.Available) DesktopWindowPhase.BACKGROUND else DesktopWindowPhase.OPEN

fun desktopRecoverTrayLoss(phase: DesktopWindowPhase, tray: DesktopNotificationStatus): DesktopWindowPhase =
    if (phase == DesktopWindowPhase.BACKGROUND && tray != DesktopNotificationStatus.Available) DesktopWindowPhase.OPEN else phase

/** Only the failed-Quit dialog can select a recovery; background failures retain the safe default. */
enum class DesktopQuitRecovery { SAVE, RETRY_READ_AND_SAVE, QUIT_WITHOUT_SAVING }

/** Invoked by a post-disposal Compose effect, never by the close event itself. */
suspend fun desktopFlushWindowTransition(
    target: DesktopWindowPhase,
    flushReading: suspend () -> Result<Unit>,
    trayStatus: () -> DesktopNotificationStatus,
    quit: suspend () -> Unit,
    recovery: DesktopQuitRecovery = DesktopQuitRecovery.SAVE,
    retryRead: suspend () -> Result<Unit> = { Result.success(Unit) }
): Result<DesktopWindowPhase> {
    require(target.flushing)
    require(target == DesktopWindowPhase.FLUSH_QUIT || recovery == DesktopQuitRecovery.SAVE)
    // Discard means leave the existing library untouched, not clear or reset it. This helper has
    // deliberately no deletion callback. Graph shutdown still drains already-started writes.
    val saved = try {
        when (recovery) {
            DesktopQuitRecovery.SAVE -> flushReading()
            DesktopQuitRecovery.RETRY_READ_AND_SAVE -> {
                val loaded = retryRead()
                if (loaded.isFailure) loaded else flushReading()
            }
            DesktopQuitRecovery.QUIT_WITHOUT_SAVING -> Result.success(Unit)
        }
    }
    catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
    catch (failure: Exception) { Result.failure(failure) }
    if (saved.isFailure) return Result.failure(saved.exceptionOrNull()!!)
    return if (target == DesktopWindowPhase.FLUSH_BACKGROUND) Result.success(desktopBackgroundAfterFlush(trayStatus()))
    else { quit(); Result.success(DesktopWindowPhase.FLUSH_QUIT) }
}
