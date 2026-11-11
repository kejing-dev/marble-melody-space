package com.kejing.spatialarcade.domain.usecase

import com.kejing.spatialarcade.data.repository.ArcadeCatalog
import com.kejing.spatialarcade.data.repository.LocalGameRepository
import com.kejing.spatialarcade.domain.model.GameKind
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlin.math.*

class MusicLowArcTest {
    private val root=File(requireNotNull(System.getProperty("user.dir"))).let{if(it.name=="app")it.parentFile else it}
    @Test fun allLowArcsKeepEveryPhysicalContactAndMidiInterval(){
        val directory=File(root,"build/reports/simulation/v027-runtime-paths").apply{requireNotNull(parentFile).mkdirs()}.apply{mkdirs()}
        val contacts=File(directory,"contacts.csv").bufferedWriter()
        contacts.write("track,note,time,x,y,z,in_x,in_y,in_z,out_x,out_y,out_z,width,depth,corner_radius\n")
        val display=GZIPOutputStream(File(root,"build/reports/simulation/v027-display-trace.csv.gz").apply{requireNotNull(parentFile).mkdirs()}.outputStream()).bufferedWriter()
        display.write("track,time,x,y,z,scroll,visibility,note,finishing\n")
        var total=0;var maximumApex=0.0;var maxError=0.0;var samples=0
        val results=mutableListOf<String>()
        for(track in ArcadeCatalog.tracks){
            val s=MusicSimulation(MusicCourses.get(track.id));s.place()
            var first=-1.0;var count=0;var localError=0.0
            GZIPOutputStream(File(directory,"track-${track.id.toString().padStart(2,'0')}.csv.gz").outputStream()).bufferedWriter().use{path->
                path.write("time,x,y,z,note,flying,finishing,visibility\n")
                repeat(((s.course.runSeconds+4)*480).toInt()){
                    val p=s.physicalBall
                    path.write("${s.time},${p.x},${p.y},${p.z},${s.noteIndex},${s.flying},${s.finishing},${s.ballVisibility}\n")
                    display.write("${track.id},${s.time},${p.x},${p.y},${p.z},${s.scroll},${s.ballVisibility},${s.noteIndex},${s.finishing}\n")
                    s.step(1f/480);samples++
                    assertFalse("Missed track ${track.id}/${s.noteIndex}: ${s.failureReason}",s.missed)
                    while(s.events.isNotEmpty()){
                        val i=s.events.removeFirst();val face=s.course.impacts[i]
                        if(first<0)first=s.impactTime.toDouble()
                        localError=max(localError,abs(s.impactTime-first-track.notes[i].time))
                        assertTrue("Single real contact ${track.id}/$i",s.physicalImpactsPerPlate()[i]==1)
                        assertTrue(s.incomingNormalSpeed<0&&s.outgoingNormalSpeed>0)
                        assertTrue("Passive steel impact",face.bounce in 0.0..1.0)
                        maximumApex=max(maximumApex,max(0f,s.impactOutgoing.y).toDouble().pow(2)/(2*9.81))
                        val q=s.impactPosition;val a=s.impactIncoming;val b=s.impactOutgoing
                        contacts.write("${track.id},$i,${s.impactTime},${q.x},${q.y},${q.z},${a.x},${a.y},${a.z},${b.x},${b.y},${b.z},${face.w},${face.d},${face.cornerRadius}\n")
                        count++;total++
                    }
                }
            }
            assertTrue("Track ${track.id} finishes",s.completed)
            assertEquals(track.notes.size,count)
            assertTrue("No capture teleport",s.maxCatchCorrection<.0002f)
            assertTrue("Incoming tangent continuous",s.maxCaptureAngle<.001f)
            assertTrue("MIDI timing ${track.id}: $localError",localError<.002)
            maxError=max(maxError,localError)
            results+="""{"track":${track.id},"contacts":$count,"maxOnsetErrorS":$localError,"seconds":${s.time}}"""
        }
        contacts.close();display.close()
        assertEquals(2348,total)
        assertTrue("Manufactured physical apex <= 8.16 mm: $maximumApex",maximumApex<=.008156)
        File(root,"build/reports/simulation/v027-physical-acceptance.json").apply{requireNotNull(parentFile).mkdirs()}.writeText("""{"tracks":20,"contacts":$total,"samples":$samples,"maximumReboundApexM":$maximumApex,"maxRelativeOnsetErrorS":$maxError,"courses":[${results.joinToString(",")}]}""")
    }
    @Test fun renderFramesConsumeTheExactElapsedTimeWithoutAnOuterPoseHold(){
        val repo=LocalGameRepository();val e=GameRulesUseCase(GameKind.C,repo.contacts(),repo.score());e.demo()
        var elapsed=0.0
        for(dt in listOf(.003f,.002f,.009f,.011f,.016f,.007f,.001f,.021f)){
            val before=e.musicPhysics.time;e.step(dt);elapsed+=dt.toDouble()
            assertTrue("Every positive render frame advances",e.musicPhysics.time>before)
            assertEquals(elapsed,e.musicPhysics.time.toDouble(),.0000001)
            assertEquals(e.musicPhysics.ball,e.ball)
        }
    }
    @Test fun shortVisibleTailDoesNotClampThePhysicalBallOrCutTheResonance(){
        val s=MusicSimulation(MusicCourses.get(8));s.place()
        while(!s.finishing)s.step(1f/480)
        s.step(.13f)
        assertEquals(0f,s.ballVisibility,0f);assertFalse(s.completed)
        val p=s.physicalBall;val v=s.velocity;s.step(.01f)
        assertEquals(p.y+v.y*.01f-.5f*9.81f*.01f*.01f,s.physicalBall.y,.000002f)
        assertTrue(s.physicalBall.y<p.y);assertFalse(s.completed)
        while(!s.completed)s.step(1f/120)
        assertTrue(s.time-s.impactTime>=2.4f)
    }
}
