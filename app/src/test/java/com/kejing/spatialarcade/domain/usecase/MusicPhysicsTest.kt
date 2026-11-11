package com.kejing.spatialarcade.domain.usecase

import com.kejing.spatialarcade.data.repository.*
import com.kejing.spatialarcade.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.*

class MusicPhysicsTest {
    private fun game()=LocalGameRepository().let{GameRulesUseCase(GameKind.C,it.contacts(),it.score())}
    @Test fun variableRenderFramesDoNotStretchTheMidiTimeline(){
        val fine=game();val uneven=game();fine.demo();uneven.demo()
        repeat(1200){fine.step(1f/120)}
        repeat(50){uneven.step(.20f)}
        assertEquals(fine.noteIndex,uneven.noteIndex)
        assertEquals(fine.musicPhysics.time,uneven.musicPhysics.time,.012f)
        assertTrue(MusicCourse.distance(fine.ball,uneven.ball)<.015f)
    }
    @Test fun theVisiblePhraseNeverCrowdsMoreThanFiveRealNotePlates(){
        for(t in ArcadeCatalog.tracks){
            val s=MusicSimulation(MusicCourses.get(t.id));val view=MusicPresentation();s.place()
            repeat(((s.course.runSeconds+3)*60).toInt()){
                s.step(1f/60);view.update(1f/60,s)
                val count=s.course.impacts.withIndex().count{(i,q)->view.visibleNote(i)&&view.visible(q.contact.y,q.contact.y,s.scroll)}
                assertTrue("${t.title}: displayed plates $count",count<=5)
                if(!s.finishing)assertTrue("Upcoming contact must remain in the phrase",view.visibleNote(s.noteIndex+1))
            }
        }
    }
    @Test fun allTwentyPhysicalCoursesPlayEveryOnsetThenFinishTheirResonance(){
        val rows=mutableListOf("track,notes,length_m,springs,max_onset_error_s,max_step_m,complete_seconds")
        var worstError=0f
        for(track in ArcadeCatalog.tracks){
            val e=game();e.selectTrack(track.id);e.demo();val heard=mutableListOf<Int>();var maxStep=0f;var returnStep=0f;var error=0f;var first=-1f
            val course=e.musicPhysics.course
            for(frame in 0..((course.runSeconds+8)*120).toInt()){
                val before=e.ball;e.step(1f/120)
                maxStep=max(maxStep,MusicCourse.distance(before,e.ball))
                if(e.musicPhysics.finishing)returnStep=max(returnStep,MusicCourse.distance(before,e.ball))
                assertFalse("Unexpected collision ${track.id} after ${e.noteIndex} at ${e.musicPhysics.physicalBall}",e.musicPhysics.missed)
                assertTrue("In complete physical course: ${track.id} ${e.ball}",e.ball.y in .06f.. .95f)
                for(n in e.consumeMusicNotes()){
                    heard+=n;if(first<0)first=e.musicPhysics.time
                    error=max(error,abs((e.musicPhysics.time-first)-track.notes[n].time))
                }
            }
            assertEquals("${track.title}: every contact exactly once",track.notes.indices.toList(),heard)
            assertEquals("${track.id}/${track.title}: completed t=${e.musicPhysics.time} planned=${course.runSeconds} segment=${e.musicPhysics.segment}/${course.points.size} speed=${e.musicPhysics.speed}",Phase.READY,e.snapshot.phase)
            assertEquals(0f,e.musicPhysics.ballVisibility,0f)
            assertFalse(e.musicPhysics.returning)
            assertTrue(e.musicPhysics.time-e.musicPhysics.impactTime>=MusicSimulation.resonanceSeconds)
            assertTrue("No tail teleport: $returnStep",returnStep<.028f)
            worstError=max(worstError,error)
            rows+="${track.id},${heard.size},${course.distances.last()},${e.musicPhysics.springCount},$error,$maxStep,${e.musicPhysics.time}"
        }
        val cwd=File(System.getProperty("user.dir"));val root=if(cwd.name=="app")cwd.parentFile else cwd
        File(root,"build/reports/simulation/music-physics-v027.csv").apply{requireNotNull(parentFile).mkdirs()}.writeText(rows.joinToString("\n"))
        assertTrue("Physical score timing: $worstError",worstError<.015f)
    }
    @Test fun impulseChangesTrajectoryInsteadOfBeingCorrectedToAPlaybackClock(){
        val a=MusicSimulation(MusicCourses.get(0));val b=MusicSimulation(MusicCourses.get(0));a.place();b.place();b.impulse(.35f)
        var ta=0f;var tb=0f
        repeat(2400){a.step(1f/480);b.step(1f/480);if(a.noteIndex>=0&&ta==0f)ta=a.time;if(b.noteIndex>=0&&tb==0f)tb=b.time}
        assertTrue(ta>0);assertTrue("A perturbed ball must miss or arrive at a different time",tb==0f||abs(ta-tb)>.05f)
    }
    @Test fun songsHaveUniqueShapesAndNotesUseDifferentPhysicalSpacing(){
        val signatures=ArcadeCatalog.tracks.map{MusicCourses.get(it.id).points.hashCode()}
        assertEquals(20,signatures.distinct().size)
        for(t in ArcadeCatalog.tracks){val c=MusicCourses.get(t.id)
            assertEquals(t.notes.size,c.marks.size);assertTrue(c.marks.toList().zipWithNext().all{it.second>it.first})
            val intervals=c.marks.toList().zipWithNext{a,b->b-a};assertTrue(intervals.max()-intervals.min()>.01f)
            assertTrue(c.impacts.map{it.restitution}.distinct().size>=3)
        }
    }
    @Test fun changingSongAndResetClearMomentumAndPauseFreezesPhysicalState(){
        val e=game();e.selectTrack(2);e.demo();repeat(500){e.step(.1f)}
        e.selectTrack(5);assertTrue(e.consumeMusicNotes().isEmpty());assertEquals(-1,e.noteIndex);assertEquals(0f,e.musicPhysics.speed,0f)
        e.demo();repeat(300){e.step(.1f)};val p=e.ball;val t=e.musicPhysics.time;e.pause();repeat(100){e.step(.1f)}
        assertEquals(p,e.ball);assertEquals(t,e.musicPhysics.time,0f);e.reset();assertEquals(e.initialBall(),e.ball)
    }
    @Test fun startDropsFromAVisibleShortApexWithoutAPropelledLaunch(){
        for(track in ArcadeCatalog.tracks){
            val p=MusicSimulation(MusicCourses.get(track.id));p.place()
            val start=p.physicalBall;val velocity=p.velocity;val first=p.course.impacts.first().contact
            assertTrue("short drop height",start.y-first.y in .015f.. .16f)
            assertTrue("already falling",velocity.y<=.00001f)
            p.step(.01f)
            assertEquals(start.y+velocity.y*.01f-.5f*9.81f*.01f*.01f,p.physicalBall.y,.000001f)
            assertFalse(p.launching);assertEquals(-1,p.noteIndex)
            repeat(120){p.step(1f/480)}
            assertTrue(p.noteIndex>=0);assertFalse(p.failedLaunch)
        }
    }
    @Test fun everyNoteIsADescendingPlateCollisionWithPassiveRebound(){
        for(track in ArcadeCatalog.tracks){
            val sim=MusicSimulation(MusicCourses.get(track.id));sim.place();var contacts=0
            repeat(((sim.course.runSeconds+2)*480).toInt()){
                sim.step(1f/480)
                while(sim.events.isNotEmpty()){
                    val i=sim.events.removeFirst();val plate=sim.course.impacts[i]
                    val delta=sim.impactPosition+plate.contact*(-1f)
                    val planeError=abs(delta.x*plate.normal.x+delta.y*plate.normal.y+delta.z*plate.normal.z)
                    assertTrue("sphere touches plate ${track.id}/$i",planeError<.00002f)
                    assertTrue("inside finite plate ${track.id}/$i: $delta",MusicCourse.distance(sim.impactPosition,plate.contact)<.002f)
                    assertTrue(sim.incomingNormalSpeed<0);assertTrue(sim.outgoingNormalSpeed>0);assertTrue(sim.impactIncoming.y<0)
                    assertEquals(-sim.incomingNormalSpeed*plate.restitution,sim.outgoingNormalSpeed,.00001f)
                    assertTrue("no energy created at plate",plate.restitution in 0f..1f)
                    contacts++
                }
            }
            assertEquals(track.notes.size,contacts)
            assertTrue("No sideways capture teleport ${track.id}: ${sim.maxCatchCorrection}",sim.maxCatchCorrection<.0002f)
            assertTrue("No abrupt direction change at the rail mouth",sim.maxCaptureAngle<.001f)
        }
    }
    @Test fun notePlatesAreSeparateFiniteSurfacesAndDelaysAreRealRails(){
        for(track in ArcadeCatalog.tracks){val c=MusicCourses.get(track.id)
            assertEquals(track.notes.size,c.impacts.size)
            for((i,m) in c.impacts.withIndex()){
                assertTrue(m.width>=.008f);assertTrue(m.depth>=.006f)
                if(i>0)assertTrue(MusicCourse.distance(c.impacts[i-1].contact,m.contact)>.005f)
                assertEquals(1.0,m.n.length(),1e-8)
            }
        }
    }
    @Test fun unsupportedFlightKeepsHorizontalMomentumAndGravitationalAcceleration(){
        val sim=MusicSimulation(MusicCourses.get(0));sim.place()
        while(!sim.flying)sim.step(1f/480)
        val before=sim.velocity;val p=sim.physicalBall;val dt=1f/1000;sim.step(dt)
        assertEquals(before.x,sim.velocity.x,.000001f);assertEquals(before.z,sim.velocity.z,.000001f)
        assertEquals(before.y-9.81f*dt,sim.velocity.y,.000001f)
        assertEquals(p.x+before.x*dt,sim.physicalBall.x,.000001f)
        sim.sidewaysImpulse(2.0,0.0);repeat(700){sim.step(1f/480)}
        assertEquals("No note is played for missing the finite plate",-1,sim.noteIndex)
    }
}
