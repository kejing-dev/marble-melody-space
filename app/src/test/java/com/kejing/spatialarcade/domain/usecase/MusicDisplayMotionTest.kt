package com.kejing.spatialarcade.domain.usecase

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import java.io.File

class MusicDisplayMotionTest {
    @Test fun cameraDoesNotFollowAnImpulseAndInitialDropShowsGravity(){
        val a=MusicSimulation(MusicCourses.get(0));val b=MusicSimulation(MusicCourses.get(0))
        a.place();b.place()
        val y=a.ball.y;val vy=a.velocity.y;val scroll=a.scroll
        a.step(.01f)
        assertEquals(scroll,a.scroll,0f)
        assertEquals(y+vy*.01f-.5f*9.81f*.01f*.01f,a.ball.y,.000001f)
        a.reset();a.place()
        while(a.noteIndex<0){a.step(1f/480);b.step(1f/480)}
        b.impulse(.4f)
        repeat(5){a.step(1f/1000);b.step(1f/1000)}
        assertEquals("Same authored reading path despite different ball momentum",a.scroll,b.scroll,0f)
        assertTrue(MusicCourse.distance(a.ball,b.ball)>.001f)
        assertFalse(a.missed);assertFalse(b.missed)
    }

    @Test fun visibleFallsRetainDownwardMotionInsteadOfLockingAtTheFocusHeight(){
        var freeSamples=0;var newDescending=0;var oldDescending=0
        var gravitySamples=0;var newGravity=0;var oldGravity=0
        val rows=mutableListOf("track,free_fall_samples,new_descending,old_descending,ballistic_samples,new_gravity,old_gravity")
        for(id in 0..19){
            val s=MusicSimulation(MusicCourses.get(id));s.place()
            var oldScroll=.50f-s.physicalBall.y
            var previousNew=s.ball.y;var previousOld=s.physicalBall.y+oldScroll
            var newVelocity=0f;var oldVelocity=0f;var previousFlying=false;var previousNote=-2
            var previousSafe=false
            var count=0;var newDown=0;var oldDown=0;var ballistic=0;var gNew=0;var gOld=0
            val dt=1f/120
            repeat(((s.course.runSeconds+4)*120).toInt()){
                s.step(dt)
                if(!s.finishing)oldScroll=max(oldScroll,min(s.course.returnOffset.toFloat(),.40f-s.physicalBall.y))
                val currentNew=s.ball.y;val currentOld=s.physicalBall.y+oldScroll
                val vn=(currentNew-previousNew)/dt;val vo=(currentOld-previousOld)/dt
                val safe=s.flying&&previousFlying&&s.noteIndex==previousNote
                if(safe&&s.velocity.y<-.12f){count++;if(vn<-.04f)newDown++;if(vo<-.04f)oldDown++}
                if(safe&&previousSafe){
                    val an=(vn-newVelocity)/dt;val ao=(vo-oldVelocity)/dt
                    ballistic++;if(an<-4.9f)gNew++;if(ao<-4.9f)gOld++
                }
                previousNew=currentNew;previousOld=currentOld;newVelocity=vn;oldVelocity=vo
                previousFlying=s.flying;previousNote=s.noteIndex;previousSafe=safe
            }
            freeSamples+=count;newDescending+=newDown;oldDescending+=oldDown
            gravitySamples+=ballistic;newGravity+=gNew;oldGravity+=gOld
            rows+="$id,$count,$newDown,$oldDown,$ballistic,$gNew,$gOld"
        }
        val cwd=File(requireNotNull(System.getProperty("user.dir")));val root=if(cwd.name=="app")cwd.parentFile else cwd
        File(root,"build/reports/simulation/v027-visible-motion.csv").apply{requireNotNull(parentFile).mkdirs()}.writeText(rows.joinToString("\n"))
        File(root,"build/reports/simulation/v027-visible-motion.json").apply{requireNotNull(parentFile).mkdirs()}.writeText("""{"freeFallSamples":$freeSamples,"newVisibleDescending":$newDescending,"oldVisibleDescending":$oldDescending,"ballisticSamples":$gravitySamples,"newGravityRetained":$newGravity,"oldGravityRetained":$oldGravity,"scope":"120 Hz samples, same physical flight/contact; visible descent > 0.04 m/s and downward acceleration > half gravity; not native frame or hand-input evidence"}""")
        assertTrue("Visible falls restored: $newDescending/$freeSamples",newDescending>freeSamples*.80)
        assertTrue("At least 25 percentage points fewer locked descending samples",newDescending-oldDescending>freeSamples*.25)
        assertTrue("Visible ballistic curvature retained: $newGravity/$gravitySamples",newGravity>gravitySamples*.85)
    }

    @Test fun readingPathChangesWithSongAndIsSmoothAtEveryNote(){
        val s=MusicSimulation(MusicCourses.get(0))
        for(id in 0..19){
            s.course=MusicCourses.get(id);s.reset()
            assertEquals(.44f,s.course.impacts.first().contact.y+s.scroll,.000001f)
            val camera=MusicReadingScroll(s.course)
            val times=com.kejing.spatialarcade.data.repository.MusicLibrary.tracks[id].notes.map{it.time.toDouble()}
            for((i,t) in times.withIndex()){
                assertEquals(MusicReadingScroll.contactHeight-s.course.impacts[i].center.y,camera.at(t),1e-9)
                if(i>0&&i<times.lastIndex){
                    val h=.00001
                    val before=(camera.at(t)-camera.at(t-h))/h
                    val after=(camera.at(t+h)-camera.at(t))/h
                    assertEquals("No camera-velocity step at note $id/$i",before,after,.002)
                }
            }
        }
    }

}
