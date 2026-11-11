package com.kejing.spatialarcade.domain.usecase

import com.kejing.spatialarcade.domain.model.*
import com.kejing.spatialarcade.ui.feature.arcade.*
import org.junit.Test
import org.junit.Assert.*

class MusicBookMotionTest {
    @Test fun selectingBookPausesThenWaitsForOpenBeforeShowingCatalog(){
        val vm=ArcadeViewModel(GameKind.C);vm.ready();vm.onEvent(ArcadeEvent.MusicPlayback)
        vm.engine.step(.4f);val p=vm.engine.ball;val clock=vm.engine.musicPhysics.time
        vm.onEvent(ArcadeEvent.SelectMusicBook)
        assertEquals(Phase.PAUSED,vm.engine.snapshot.phase)
        assertTrue(vm.state.value.musicBookOpen);assertTrue(vm.state.value.musicLibraryPending);assertFalse(vm.state.value.library)
        val motion=MusicBookMotion();motion.update(.48f,false)
        repeat(20){motion.update(.016f,true);vm.engine.step(.016f);assertFalse(motion.fullyOpen);assertFalse(vm.state.value.library)}
        motion.update(.2f,true);assertTrue(motion.fullyOpen)
        vm.onEvent(ArcadeEvent.MusicBookOpened);assertTrue(vm.state.value.library)
        assertEquals(p,vm.engine.ball);assertEquals(clock,vm.engine.musicPhysics.time,0f)
    }
    @Test fun playOrDismissDuringOpeningCancelsLateMenuCompletion(){
        val vm=ArcadeViewModel(GameKind.C);vm.ready()
        vm.onEvent(ArcadeEvent.SelectMusicBook);vm.onEvent(ArcadeEvent.MusicPlayback)
        vm.onEvent(ArcadeEvent.MusicBookOpened)
        assertFalse(vm.state.value.library);assertFalse(vm.state.value.musicBookOpen);assertEquals(Phase.PLAYING,vm.engine.snapshot.phase)
        vm.onEvent(ArcadeEvent.SelectMusicBook);vm.onEvent(ArcadeEvent.CloseLibrary);vm.onEvent(ArcadeEvent.MusicBookOpened)
        assertFalse(vm.state.value.library)
    }
    @Test fun hingeCanReverseWithoutJumpingAndStopsAtBothEndpoints(){
        val m=MusicBookMotion();m.update(.24f,false);assertEquals(.5f,m.openFraction,.00001f)
        val before=m.closedAmount;m.update(.01f,true)
        assertTrue(m.closedAmount<before);assertTrue(m.closedAmount>before-.05f)
        m.update(2f,true);assertEquals(1f,m.openFraction,0f)
        m.update(2f,false);assertEquals(0f,m.openFraction,0f)
        m.update(Float.NaN,true);assertEquals(0f,m.openFraction,0f)
    }
}
