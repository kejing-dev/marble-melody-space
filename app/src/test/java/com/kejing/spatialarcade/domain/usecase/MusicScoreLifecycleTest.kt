package com.kejing.spatialarcade.domain.usecase

import com.kejing.spatialarcade.domain.model.*
import com.kejing.spatialarcade.ui.feature.arcade.*
import org.junit.Assert.*
import org.junit.Test

class MusicScoreLifecycleTest {
    @Test fun bookPausesWithoutMovingTheBallAndDismissDoesNotResume(){
        val vm=ArcadeViewModel(GameKind.C);vm.ready()
        vm.onEvent(ArcadeEvent.MusicPlayback)
        repeat(20){vm.engine.step(.01f)}
        val clock=vm.engine.musicPhysics.time;val ball=vm.engine.ball
        assertTrue(vm.selectFromPhysicalBook())
        repeat(50){vm.engine.step(.01f)}
        assertEquals(clock,vm.engine.musicPhysics.time,0f);assertEquals(ball,vm.engine.ball)
        vm.onEvent(ArcadeEvent.MusicBookOpened);assertTrue(vm.state.value.library)
        vm.onEvent(ArcadeEvent.CloseLibrary);vm.engine.step(1f)
        assertEquals(Phase.PAUSED,vm.engine.snapshot.phase);assertEquals(clock,vm.engine.musicPhysics.time,0f)
        vm.onEvent(ArcadeEvent.Reset)
        assertEquals(Phase.READY,vm.engine.snapshot.phase);assertEquals(-1,vm.engine.noteIndex)
    }
    @Test fun trackChangesResetNotationToTheSelectedCourse(){
        val vm=ArcadeViewModel(GameKind.C);vm.ready()
        for(track in 0..19){
            vm.onEvent(ArcadeEvent.Track(track))
            assertEquals(track,vm.engine.musicPhysics.course.id)
            assertEquals(-1,vm.engine.noteIndex);assertEquals(0,MusicScoreDisplay.pageStart(vm.engine.noteIndex))
            vm.onEvent(ArcadeEvent.MusicPlayback);vm.engine.step(.4f)
            assertEquals(Phase.PLAYING,vm.engine.snapshot.phase)
        }
    }
    @Test fun heldBookPressSelectsOnceButStaleMenuCanReopenWithoutRestartingSong(){
        val vm=ArcadeViewModel(GameKind.C);val latch=PressLatch()
        assertFalse(vm.selectFromPhysicalBook());vm.ready();vm.onEvent(ArcadeEvent.MusicPlayback);vm.engine.step(.6f)
        val clock=vm.engine.musicPhysics.time;var selections=0
        repeat(100){if(latch.press(true)&&vm.selectFromPhysicalBook())selections++}
        assertEquals(1,selections);vm.onEvent(ArcadeEvent.MusicBookOpened);assertTrue(vm.state.value.library)
        latch.press(false);assertTrue(latch.press(true));assertTrue(vm.selectFromPhysicalBook())
        assertFalse(vm.state.value.library);assertTrue(vm.state.value.musicLibraryPending)
        vm.onEvent(ArcadeEvent.MusicBookOpened);assertTrue(vm.state.value.library)
        assertEquals(clock,vm.engine.musicPhysics.time,0f);assertEquals(Phase.PAUSED,vm.engine.snapshot.phase)
    }
    @Test fun completeSongKeepsItsLastNotationUntilResonanceEnds(){
        val vm=ArcadeViewModel(GameKind.C);vm.ready();vm.onEvent(ArcadeEvent.MusicPlayback)
        while(!vm.engine.musicPhysics.finishing){vm.engine.step(1f/120);vm.engine.consumeMusicNotes()}
        repeat(120){vm.engine.step(1f/120)}
        assertFalse(vm.engine.musicPhysics.completed);assertEquals(vm.engine.notes.lastIndex,vm.engine.noteIndex)
        repeat(250){vm.engine.step(1f/120)}
        assertTrue(vm.engine.musicPhysics.completed);assertEquals(vm.engine.notes.lastIndex,vm.engine.noteIndex)
    }
    @Test fun raisedScoreCullingKeepsCurrentCarrierAndMeasuresNextNoteVisibility(){
        var carried=0;var nextVisible=0;var nextTotal=0
        for(track in 0..19){
            val sim=MusicSimulation(MusicCourses.get(track));val display=MusicPresentation();display.bind(track);sim.place()
            repeat(((sim.course.runSeconds+4)*480).toInt()){
                sim.step(1f/480)
                display.update(1f/480,sim)
                if(sim.started&&!sim.flying&&!sim.finishing&&!sim.completed&&sim.noteIndex>=0){
                    val rail=sim.course.rails[sim.course.railAfter.getValue(sim.noteIndex)]
                    // The real segment under the ball must remain within a visible
                    // render group. It has the same ±15 mm bounds as ECS groups.
                    val p=rail.path[sim.segment];val q=rail.path[sim.segment+1]
                    assertTrue("Carrier clipped: track=$track t=${sim.time}",display.visible(minOf(p.y,q.y).toFloat()-.015f,maxOf(p.y,q.y).toFloat()+.015f,sim.scroll))
                    assertTrue("Current real carrier survives phrase culling",display.visibleRail(sim.noteIndex))
                    carried++
                }
                if(!sim.finishing&&sim.noteIndex+1 in sim.course.impacts.indices){
                    val y=sim.course.impacts[sim.noteIndex+1].contact.y
                    if(display.visibleNote(sim.noteIndex+1)&&display.visible(y-.025f,y+.020f,sim.scroll))nextVisible++
                    nextTotal++
                }
            }
            assertTrue(sim.completed)
        }
        assertTrue(carried>100000)
        val cwd=java.io.File(System.getProperty("user.dir"));val root=if(cwd.name=="app")cwd.parentFile else cwd
        java.io.File(root,"build/reports/simulation/v027-carrier-visibility.json").apply{requireNotNull(parentFile).mkdirs()}.writeText("""{"tracks":20,"carrierSamples":$carried,"carrierHidden":0,"nextNoteVisible":$nextVisible,"nextNoteSamples":$nextTotal,"bottom":${MusicPresentation.bottom},"top":${MusicPresentation.top},"scope":"480 Hz actual carrier segment and phrase culling; next note counted before contact."}""")
    }
}
