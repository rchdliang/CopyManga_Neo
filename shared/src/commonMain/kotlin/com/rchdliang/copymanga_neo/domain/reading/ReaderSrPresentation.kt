package com.rchdliang.copymanga_neo.domain.reading

/** 输出就绪与屏幕实际绘制是两个阶段；缓存输出也必须重新经过解码/绘制确认。 */
data class SrFrame(val pageId: String, val revision: Int, val renderId: Long,
    val parameters: SrParameters, val status: SrStatus)
enum class SrRenderedImage { ORIGINAL, ENHANCED, DECODE_FAILED }
data class SrPresentationReceipt(val renderId: Long, val artifact: SrArtifact, val image: SrRenderedImage)
enum class SrPresentationPhase { ORIGINAL, MODEL_NOT_READY, WAITING_ORIGINAL, WAITING, PROCESSING, DECODING, ENHANCED, FAILED, UNAVAILABLE, SPREAD_ORIGINAL }
data class SrPresentation(val pageId: String?, val phase: SrPresentationPhase,
    val backend: SrExecutionMode? = null, val cpuFallback: Boolean = false, val reason: String? = null)

fun matchingSrFrame(frame: SrFrame?, pageId: String, revision: Int, settings: SrUserSettings): SrFrame? =
    frame?.takeIf { settings.enabled && it.pageId == pageId && it.revision == revision &&
        it.parameters.mode == settings.mode && it.parameters.cpuFallback == settings.cpuFallback }

fun readerSrPresentation(pageId: String?, singlePage: Boolean, settings: SrUserSettings,
    model: SrModelState, unavailableReason: String?, frame: SrFrame?, receipt: SrPresentationReceipt?,
    preparationFailed: Boolean = false, originalReady: Boolean = true): SrPresentation {
    fun state(phase: SrPresentationPhase, reason: String? = null) = SrPresentation(pageId, phase, reason = reason)
    if (pageId == null || !settings.enabled) return state(SrPresentationPhase.ORIGINAL)
    if (!singlePage) return state(SrPresentationPhase.SPREAD_ORIGINAL)
    if (unavailableReason != null) return state(SrPresentationPhase.UNAVAILABLE, unavailableReason)
    if (model != SrModelState.Ready) return state(SrPresentationPhase.MODEL_NOT_READY)
    if (preparationFailed) return state(SrPresentationPhase.FAILED)
    if (!originalReady) return state(SrPresentationPhase.WAITING_ORIGINAL)
    val current = frame?.takeIf { it.pageId == pageId && it.parameters.mode == settings.mode &&
        it.parameters.cpuFallback == settings.cpuFallback } ?: return state(SrPresentationPhase.WAITING)
    return when (val status = current.status) {
        SrStatus.Original -> state(SrPresentationPhase.WAITING)
        SrStatus.Processing -> state(SrPresentationPhase.PROCESSING)
        SrStatus.Failed -> state(SrPresentationPhase.FAILED)
        is SrStatus.Unavailable -> state(SrPresentationPhase.UNAVAILABLE, status.reason)
        is SrStatus.Ready -> {
            val shown = receipt?.takeIf { it.renderId == current.renderId && it.artifact === status.artifact }
            when (shown?.image) {
                SrRenderedImage.ENHANCED -> SrPresentation(pageId, SrPresentationPhase.ENHANCED,
                    status.artifact.backend, status.artifact.cpuFallback)
                SrRenderedImage.DECODE_FAILED -> state(SrPresentationPhase.FAILED)
                else -> state(SrPresentationPhase.DECODING)
            }
        }
    }
}
