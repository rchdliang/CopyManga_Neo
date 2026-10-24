package com.rchdliang.copymanga_neo.domain.reading

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSrPresentationTest {
    private fun output(backend: SrExecutionMode = SrExecutionMode.GPU, fallback: Boolean = false): SrArtifact {
        val bytes = ByteArray(33)
        byteArrayOf(-119,80,78,71,13,10,26,10,0,0,0,13,73,72,68,82).copyInto(bytes)
        bytes[19]=32; bytes[23]=32
        return SrArtifact(bytes,32,32,backend,fallback)
    }
    @Test fun readyRequiresMatchingDrawAndDecodeFailureNeverClaimsApplied() {
        val artifact=output(); val settings=SrUserSettings(enabled=true)
        val frame=SrFrame("A",0,7,SrParameters(),SrStatus.Ready(artifact))
        fun shown(receipt: SrPresentationReceipt?=null, f: SrFrame?=frame)=readerSrPresentation("A",true,settings,SrModelState.Ready,null,f,receipt)
        assertEquals(SrPresentationPhase.DECODING,shown().phase)
        assertEquals(SrPresentationPhase.DECODING,shown(SrPresentationReceipt(6,artifact,SrRenderedImage.ENHANCED)).phase)
        assertEquals(SrPresentationPhase.DECODING,shown(SrPresentationReceipt(7,output(),SrRenderedImage.ENHANCED)).phase)
        assertEquals(SrPresentationPhase.FAILED,shown(SrPresentationReceipt(7,artifact,SrRenderedImage.DECODE_FAILED)).phase)
        val applied=shown(SrPresentationReceipt(7,artifact,SrRenderedImage.ENHANCED))
        assertEquals(SrPresentationPhase.ENHANCED,applied.phase);assertEquals(SrExecutionMode.GPU,applied.backend)
        assertEquals(SrPresentationPhase.WAITING,shown(f=frame.copy(pageId="B")).phase)
        assertNull(matchingSrFrame(frame,"A",1,settings))
        assertNull(matchingSrFrame(frame,"A",0,settings.copy(mode=SrExecutionMode.CPU)))
    }
    @Test fun spreadDisabledAndUnavailableDoNotUseOldSuccess() {
        val artifact=output(SrExecutionMode.CPU,true)
        val frame=SrFrame("A",0,1,SrParameters(),SrStatus.Ready(artifact))
        val receipt=SrPresentationReceipt(1,artifact,SrRenderedImage.ENHANCED)
        val on=SrUserSettings(enabled=true)
        fun state(single:Boolean=true,s:SrUserSettings=on,model:SrModelState=SrModelState.Ready)=
            readerSrPresentation("A",single,s,model,null,frame,receipt)
        assertEquals(SrPresentationPhase.SPREAD_ORIGINAL,state(false).phase)
        assertEquals(SrPresentationPhase.ORIGINAL,state(s=on.copy(enabled=false)).phase)
        assertEquals(SrPresentationPhase.MODEL_NOT_READY,state(model=SrModelState.Deleting).phase)
        assertEquals(SrPresentationPhase.MODEL_NOT_READY,state(model=SrModelState.Downloading(1,20)).phase)
        assertEquals(SrExecutionMode.CPU,state().backend);assertTrue(state().cpuFallback)
    }
    @Test fun lateDrawRefreshModeChangeAndOldDisposalCannotOverwriteCurrentPage()=runTest {
        var calls=0
        val platform=object:ReaderSrPlatform {
            override val budgetBytes=96L*1024*1024;override val workingBytes=0L
            override val unavailableReason:String?=null;override val detail=MutableStateFlow("fixture")
            override suspend fun source(page:SrPage)=SrSource(page.id,"a".repeat(64),16,16)
            override suspend fun upscale(request:SrRequest):SrArtifact {calls++;return output()}
        }
        val session=ReaderSrSession(backgroundScope,platform)
        session.settings.value=SrUserSettings(enabled=true)
        session.select(SrPage("A","A"));session.originalReady("A");runCurrent()
        val a=assertNotNull(session.frame.value);val art=(a.status as SrStatus.Ready).artifact
        val old=Any();session.imageDrawn(old,a,art,SrRenderedImage.ENHANCED)
        assertNotNull(session.presentationReceipt.value)
        session.select(SrPage("B","B"));session.imageDrawn(old,a,art,SrRenderedImage.ENHANCED)
        assertNull(session.presentationReceipt.value)
        session.originalReady("B");runCurrent()
        session.select(SrPage("A","A"));runCurrent()
        val cached=assertNotNull(session.frame.value)
        assertNotEquals(a.renderId,cached.renderId);assertEquals(2,calls)
        assertNull(session.presentationReceipt.value)
        session.imageDrawn(old,a,art,SrRenderedImage.ENHANCED);assertNull(session.presentationReceipt.value)
        val owner=Any();session.imageDrawn(owner,cached,(cached.status as SrStatus.Ready).artifact,SrRenderedImage.ENHANCED)
        session.imageDetached(old);assertNotNull(session.presentationReceipt.value)
        session.originalReady("A",1);session.imageDrawn(owner,cached,art,SrRenderedImage.ENHANCED)
        assertNull(session.presentationReceipt.value);runCurrent()
        val refreshed=assertNotNull(session.frame.value)
        session.settings.value=session.settings.value.copy(mode=SrExecutionMode.CPU)
        session.imageDrawn(owner,refreshed,(refreshed.status as SrStatus.Ready).artifact,SrRenderedImage.ENHANCED)
        assertNull(session.presentationReceipt.value);runCurrent()
        val cpu=assertNotNull(session.frame.value)
        session.settings.value=session.settings.value.copy(enabled=false)
        session.imageDrawn(owner,cpu,(cpu.status as SrStatus.Ready).artifact,SrRenderedImage.ENHANCED)
        assertNull(session.presentationReceipt.value)
        session.close()
    }
}
