package com.kejing.spatialarcade.domain.usecase

import com.kejing.spatialarcade.domain.model.*
import com.kejing.spatialarcade.ui.feature.arcade.*
import org.junit.Assert.*
import org.junit.Test

class MusicMoonlightTest {
    @Test fun reportCompletePhysicalBallBoundsForOfflineDisplayLayout(){
        val rows=mutableListOf("track,min_x,max_x,min_z,max_z")
        for(id in 0..19){
            val sim=MusicSimulation(MusicCourses.get(id));sim.place()
            var minX=sim.physicalBall.x;var maxX=minX;var minZ=sim.physicalBall.z;var maxZ=minZ
            repeat(((sim.course.runSeconds+4)*480).toInt()){
                sim.step(1f/480)
                if(sim.ballVisibility>0f){val p=sim.physicalBall;minX=minOf(minX,p.x);maxX=maxOf(maxX,p.x);minZ=minOf(minZ,p.z);maxZ=maxOf(maxZ,p.z)}
            }
            assertTrue(sim.completed)
            rows+="$id,$minX,$maxX,$minZ,$maxZ"
        }
        val cwd=java.io.File(System.getProperty("user.dir"));val root=if(cwd.name=="app")cwd.parentFile else cwd
        java.io.File(root,"build/reports/simulation/v027-ball-bounds.csv").apply{requireNotNull(parentFile).mkdirs()}.writeText(rows.joinToString("\n"))
    }
    @Test fun everyPhysicalControlIsEdgeTriggeredAndIndependent(){
        val deck=MusicControlDeck();val vm=ArcadeViewModel(GameKind.C);vm.ready()
        fun press(c:MusicControl,down:Boolean){if(deck.press(c,down))vm.onEvent(ArcadeEvent.MusicControlPress(c))}
        repeat(100){press(MusicControl.SpeedUp,true)}
        assertEquals(1.25f,vm.state.value.musicSpeed,0f)
        repeat(100){press(MusicControl.VolumeDown,true)}
        assertEquals(.15f,vm.state.value.musicVolume,.00001f)
        repeat(100){press(MusicControl.Mute,true)}
        assertTrue(vm.state.value.muted)
        deck.releaseAll();press(MusicControl.Mute,true);assertFalse(vm.state.value.muted)
        repeat(10){press(MusicControl.SpeedDown,false);press(MusicControl.SpeedDown,true)}
        assertEquals(.75f,vm.state.value.musicSpeed,0f)
        repeat(10){press(MusicControl.VolumeUp,false);press(MusicControl.VolumeUp,true)}
        assertEquals(1f,vm.state.value.musicVolume,0f)
    }
    @Test fun lastImpactWaitsForResonanceAndPauseFreezesTheWholeTail(){
        val vm=ArcadeViewModel(GameKind.C);vm.ready();vm.onEvent(ArcadeEvent.MusicPlayback)
        val e=vm.engine
        while(!e.musicPhysics.finishing){e.step(1f/480);e.consumeMusicNotes()}
        assertEquals(e.notes.lastIndex,e.noteIndex);assertFalse(e.musicPhysics.completed)
        val last=e.musicPhysics.impactTime;val t=e.musicPhysics.time
        vm.onEvent(ArcadeEvent.MusicPlayback);repeat(500){e.step(.1f)}
        assertEquals(t,e.musicPhysics.time,0f);assertFalse(e.musicPhysics.completed)
        vm.onEvent(ArcadeEvent.MusicPlayback)
        repeat(120){e.step(1f/120)}
        assertEquals(0f,e.musicPhysics.ballVisibility,0f);assertFalse(e.musicPhysics.completed)
        repeat(200){e.step(1f/120)}
        assertTrue(e.musicPhysics.completed);assertTrue(e.musicPhysics.time-last>=2.4f)
        assertFalse(e.musicPhysics.returning);assertEquals(Phase.READY,e.snapshot.phase)
    }
    @Test fun entranceAndFirstPlateShareTheDisplayFrameWithoutTopClipping(){
        for(id in 0..19){
            val sim=MusicSimulation(MusicCourses.get(id));val display=MusicPresentation()
            display.bind(id)
            val ball=display.map(sim.ball)
            val plate=display.map(sim.course.impacts.first().contact+Point(0f,sim.scroll,0f))
            // The opening drop starts above the short plate viewport, inside
            // the measured Volume. The first target itself is already visible.
            assertTrue("opening ball contained $id: $ball",ball.y in MusicPresentation.bottom.. .81f)
            assertTrue("first plate visible $id: $plate",plate.y in MusicPresentation.bottom..MusicPresentation.top)
            assertTrue(ball.y>plate.y+.015f)
        }
    }
    @Test fun everyTrackKeepsAllMeshExtentsAndTheWholeBallFlightInsideThePerformanceArea(){
        val density=mutableListOf("track,previousScale,newScale,previousVisibleSamples,newVisibleSamples")
        for(id in 0..19){
            val sim=MusicSimulation(MusicCourses.get(id));val display=MusicPresentation();display.bind(id)
            val b=com.kejing.spatialarcade.data.repository.MusicDisplayLayouts.bounds[id]
            val previousScale=minOf(1.98f,.816f/(b[1]-b[0]),.648f/(b[3]-b[2]))
            assertTrue("Measured course uses the available performance region $id",display.scale>1f&&display.scale<=MusicPresentation.phraseScale)
            for(x in listOf(b[0],b[1]))for(z in listOf(b[2],b[3])){
                val p=display.map(Point(x,.4f,z))
                assertTrue("mesh x $id: $p",p.x>=MusicPresentation.left-.00001f&&p.x<=MusicPresentation.right+.00001f)
                assertTrue("mesh z $id: $p",p.z>=MusicPresentation.back-.00001f&&p.z<=MusicPresentation.front+.00001f)
            }
            sim.place();val fixedOffset=display.routeOffset(0f);val fixedScale=display.scale
            var previousVisible=0;var currentVisible=0
            repeat(((sim.course.runSeconds+4)*480).toInt()){
                sim.step(1f/480);display.update(1f/480,sim)
                assertEquals(fixedOffset,display.routeOffset(0f));assertEquals(fixedScale,display.scale,0f)
                if(sim.ballVisibility>0f){
                    val p=display.map(sim.ball);val r=MusicSimulation.radius*display.scale*sim.ballVisibility
                    assertTrue("ball x $id at ${sim.time}: $p",p.x-r>=MusicPresentation.left-.00001f&&p.x+r<=MusicPresentation.right+.00001f)
                    assertTrue("ball z $id at ${sim.time}: $p",p.z-r>=MusicPresentation.back-.00001f&&p.z+r<=MusicPresentation.front+.00001f)
                    assertTrue("ball y $id at ${sim.time}: $p",p.y-r>=.065f&&p.y+r<=.81f)
                }
                if(it%48==0&&sim.noteIndex>=0&&!sim.finishing){
                    previousVisible+=sim.course.impacts.count{impact->.4f+(impact.contact.y+sim.scroll-.4f)*previousScale in .12f.. .75f}
                    currentVisible+=sim.course.impacts.withIndex().count{(i,impact)->display.visibleNote(i)&&display.visible(impact.contact.y,impact.contact.y,sim.scroll)}
                }
            }
            assertTrue(sim.completed)
            assertTrue("Fewer visible pads in track $id: $previousVisible -> $currentVisible",currentVisible<previousVisible)
            density+="$id,$previousScale,${display.scale},$previousVisible,$currentVisible"
        }
        val cwd=java.io.File(System.getProperty("user.dir"));val root=if(cwd.name=="app")cwd.parentFile else cwd
        java.io.File(root,"build/reports/simulation/v027-display-density.csv").apply{requireNotNull(parentFile).mkdirs()}.writeText(density.joinToString("\n"))
    }
}
