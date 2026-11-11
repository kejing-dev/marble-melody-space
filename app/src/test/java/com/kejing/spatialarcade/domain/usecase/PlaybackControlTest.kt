package com.kejing.spatialarcade.domain.usecase

import com.kejing.spatialarcade.data.repository.*
import com.kejing.spatialarcade.domain.model.*
import com.kejing.spatialarcade.ui.feature.arcade.*
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.*

class PlaybackControlTest {
    @Test fun physicalPlayFromCapturedPlayingFrameRestartsAtTheFirstDrop(){
        val vm=ArcadeViewModel(GameKind.C);vm.ready();vm.onEvent(ArcadeEvent.Track(5));vm.onEvent(ArcadeEvent.MusicPlayback)
        repeat(1800){vm.engine.step(1f/480);vm.engine.consumeMusicNotes()}
        assertTrue(vm.engine.musicPhysics.time>3f)
        vm.playFromPhysicalButton(capturedFrame=true)
        assertEquals(5,vm.engine.snapshot.track);assertEquals(Phase.PLAYING,vm.engine.snapshot.phase)
        assertEquals(0f,vm.engine.musicPhysics.time,0f);assertEquals(-1,vm.engine.noteIndex)
        assertFalse(vm.state.value.musicBookOpen)
        repeat(120){vm.engine.step(1f/480)}
        assertTrue(vm.engine.noteIndex>=0);assertEquals(listOf(0),vm.engine.consumeMusicNotes())
    }
    @Test fun ordinaryPhysicalPressStillPausesAndResumesAtTheSameTime(){
        val vm=ArcadeViewModel(GameKind.C);vm.ready();vm.playFromPhysicalButton(capturedFrame=false)
        repeat(1200){vm.engine.step(1f/480);vm.engine.consumeMusicNotes()}
        val time=vm.engine.musicPhysics.time;val point=vm.engine.ball
        vm.playFromPhysicalButton(capturedFrame=false)
        assertEquals(Phase.PAUSED,vm.engine.snapshot.phase)
        repeat(120){vm.engine.step(1f/480)}
        assertEquals(time,vm.engine.musicPhysics.time,0f);assertEquals(point,vm.engine.ball)
        vm.playFromPhysicalButton(capturedFrame=false)
        assertEquals(Phase.PLAYING,vm.engine.snapshot.phase);assertEquals(time,vm.engine.musicPhysics.time,0f)
        vm.engine.step(.05f);assertTrue(vm.engine.musicPhysics.time>time)
    }
    private fun music()=LocalGameRepository().let{GameRulesUseCase(GameKind.C,it.contacts(),it.score())}
    @Test fun heldCapAndAdditionalPointerDoNotToggleTwice(){
        val latch=PressLatch();assertTrue(latch.press(true));repeat(100){assertFalse(latch.press(true))}
        assertFalse(latch.press(false));assertTrue(latch.press(true));assertFalse(latch.press(false))
    }
    @Test fun pauseResumeContinuesSameTrajectoryAndContactSequence(){
        val a=music();val b=music();a.toggleMusicPlayback();b.toggleMusicPlayback()
        repeat(360){a.step(1f/120);b.step(1f/120)}
        val prefix=a.consumeMusicNotes();assertEquals(prefix,b.consumeMusicNotes())
        a.toggleMusicPlayback();val ball=a.ball;val clock=a.musicPhysics.time
        repeat(800){a.step(.1f)}
        assertEquals(Phase.PAUSED,a.snapshot.phase);assertEquals(ball,a.ball);assertEquals(clock,a.musicPhysics.time,0f);assertTrue(a.consumeMusicNotes().isEmpty())
        a.toggleMusicPlayback();repeat(360){a.step(1f/120);b.step(1f/120)}
        assertEquals(b.ball,a.ball);assertEquals(b.musicPhysics.time,a.musicPhysics.time,0f);assertEquals(b.consumeMusicNotes(),a.consumeMusicNotes())
    }
    @Test fun capDropCompletesAllTwentyScoresAndFadesOnlyAfterTheLastNote(){
        for(track in ArcadeCatalog.tracks){
            val e=music();e.selectTrack(track.id);e.toggleMusicPlayback();val heard=mutableListOf<Int>()
            val view=MusicPresentation()
            repeat(((e.musicPhysics.course.runSeconds+10)*60).toInt()){
                e.step(1f/60);view.update(1f/60,e.musicPhysics);heard.addAll(e.consumeMusicNotes())
            }
            assertEquals("track ${track.id}",track.notes.indices.toList(),heard)
            assertEquals(Phase.READY,e.snapshot.phase);assertEquals(0f,e.musicPhysics.ballVisibility,0f)
            assertTrue(view.scale in 1f..MusicPresentation.phraseScale);assertFalse(e.musicPhysics.returning)
            e.toggleMusicPlayback();assertEquals(Phase.PLAYING,e.snapshot.phase);assertEquals(0,e.snapshot.count)
        }
    }
    @Test fun displayMagnificationKeepsBallAndMeshInTheSameCoordinateSystem(){
        val e=music();e.selectTrack(5);e.demo();val view=MusicPresentation()
        repeat(480){e.step(1f/60);view.update(1f/60,e.musicPhysics)}
        assertTrue(view.scale in 1f..MusicPresentation.phraseScale)
        val ball=view.map(e.musicPhysics.physicalBall+Point(0f,e.musicScroll,0f))
        val fromMeshTransform=e.musicPhysics.physicalBall*view.scale+view.routeOffset(e.musicScroll)
        assertTrue(MusicCourse.distance(ball,fromMeshTransform)<.000003f)
        val before=e.musicPhysics.course.impacts.count{it.contact.y+e.musicScroll in .085f.. .77f}
        val after=e.musicPhysics.course.impacts.count{view.visible(it.contact.y,it.contact.y,e.musicScroll)}
        assertTrue("Fewer notes per screen: $before -> $after",after<before)
    }
    @Test fun playButtonClosesBookAndCatalogBeforePlayback(){
        val vm=ArcadeViewModel(GameKind.C);vm.ready();vm.onEvent(ArcadeEvent.OpenLibrary)
        vm.onEvent(ArcadeEvent.MusicPlayback)
        assertEquals(Phase.PLAYING,vm.engine.snapshot.phase)
        assertFalse(vm.state.value.library);assertFalse(vm.state.value.musicBookOpen)
        vm.onEvent(ArcadeEvent.MusicPlayback)
        assertEquals(Phase.PAUSED,vm.engine.snapshot.phase);assertFalse(vm.state.value.musicBookOpen)
    }
    @Test fun changingLanguagePreservesContactPowerAndShot(){
        val vm=ArcadeViewModel(GameKind.A);vm.onEvent(ArcadeEvent.Select(7));vm.engine.grab("boot");vm.engine.drag(0f,0f,.035f)
        val before=vm.engine.snapshot;vm.onEvent(ArcadeEvent.ToggleLanguage);assertTrue(vm.state.value.english);assertEquals(before,vm.engine.snapshot)
    }
    @Test fun grabbingPaddleAfterGameOverStartsNewRound(){
        val e=LocalGameRepository().let{GameRulesUseCase(GameKind.D,it.contacts(),it.score())}
        repeat(3){e.grab("paddle");e.drag(-1f,0f,0f);repeat(5000){if(e.snapshot.phase==Phase.PLAYING)e.step(1f/120)}}
        assertEquals(Phase.RESULT,e.snapshot.phase);e.grab("paddle");assertEquals(Phase.PLAYING,e.snapshot.phase);assertEquals(3,e.snapshot.lives);assertEquals(0,e.snapshot.score)
    }
}
