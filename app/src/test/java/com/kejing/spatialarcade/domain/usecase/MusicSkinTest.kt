package com.kejing.spatialarcade.domain.usecase

import com.kejing.spatialarcade.data.repository.ArcadeCatalog
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.*

class MusicSkinTest {
    @Test fun lowerManufacturedArcsPreservePriorContactTimesWithoutReusingPriorPositions(){
        val cwd=File(requireNotNull(System.getProperty("user.dir")));val root=if(cwd.name=="app")cwd.parentFile else cwd
        val old=File(root,"app/src/test/fixtures/v021-contacts.csv").readLines().drop(1).map{it.split(',')}.associate{(it[0].toInt() to it[1].toInt()) to it}
        var count=0;var changed=0;var maxTimeError=0.0;var oldApex=0.0;var newApex=0.0
        for(track in 0..19){
            val s=MusicSimulation(MusicCourses.get(track));s.place()
            repeat(((s.course.runSeconds+4)*480).toInt()){
                s.step(1f/480)
                while(s.events.isNotEmpty()){
                    val i=s.events.removeFirst();val row=old.getValue(track to i)
                    val error=abs(s.impactTime.toDouble()-row[2].toDouble());maxTimeError=max(maxTimeError,error)
                    assertTrue("Prior MIDI onset retained $track/$i",error<.002)
                    val p=s.impactPosition
                    if(maxOf(abs(p.x.toDouble()-row[3].toDouble()),abs(p.y.toDouble()-row[4].toDouble()),abs(p.z.toDouble()-row[5].toDouble()))>.005)changed++
                    oldApex=max(oldApex,max(0.0,row[10].toDouble()).pow(2)/(2*9.81))
                    newApex=max(newApex,max(0.0,s.impactOutgoing.y.toDouble()).pow(2)/(2*9.81))
                    count++
                }
            }
            assertTrue(s.completed);assertFalse(s.missed)
        }
        assertEquals(2348,count);assertTrue("New physical plate positions are manufactured",changed>2000)
        assertTrue("Physical apex reduced, not visually compressed",newApex<oldApex*.15)
        File(root,"build/reports/simulation/v027-physical-redesign-comparison.json").apply{requireNotNull(parentFile).mkdirs()}.writeText("""{"contacts":$count,"changedPlateLocations":$changed,"maximumContactTimeDifferenceS":$maxTimeError,"previousMaximumApexM":$oldApex,"newMaximumApexM":$newApex,"sameTrajectory":false,"scope":"Actual contacts compared to archived v021/v026 physical contacts; physical geometry changed, full MIDI schedule preserved."}""")
    }
    @Test fun exportedPadGeometryAndRuntimeFiniteFacesHaveIdenticalDimensions(){
        val cwd=File(System.getProperty("user.dir"));val root=if(cwd.name=="app")cwd.parentFile else cwd
        val spec=File(root,"app/src/test/fixtures/v027-plate-specs.json").readText()
        fun values(key:String)=Regex("\"$key\"\\s*:\\s*([-+0-9.eE]+)").findAll(spec).map{it.groupValues[1].toDouble()}.toList()
        val widths=values("width");val depths=values("depth");val corners=values("cornerRadius");val thickness=values("thickness")
        val pads=(0..19).flatMap{MusicCourses.get(it).impacts}
        assertEquals(2348,pads.size);assertEquals(pads.size,widths.size);assertEquals(pads.size,depths.size);assertEquals(pads.size,corners.size)
        assertEquals(pads.size,thickness.size)
        for(i in pads.indices){assertEquals(widths[i],pads[i].w,1e-12);assertEquals(depths[i],pads[i].d,1e-12);assertEquals(corners[i],pads[i].cornerRadius,1e-12);assertEquals(MusicStrikePad.thickness,thickness[i],1e-12)}
    }
    @Test fun compactRoundedPadsKeepEveryRealContactAndPassiveDeparture(){
        val rows=mutableListOf("track,note,width,depth,corner,time,offset_m,physical_contacts")
        var total=0
        for(track in ArcadeCatalog.tracks){
            val s=MusicSimulation(MusicCourses.get(track.id));s.place()
            repeat(((s.course.runSeconds+4)*480).toInt()){
                s.step(1f/480)
                assertFalse("Unexpected physical collision ${track.id}: ${s.failureReason}",s.missed)
                while(s.events.isNotEmpty()){
                    val i=s.events.removeFirst();val p=s.course.impacts[i]
                    val pos=s.impactPosition;val delta=MusicVector(pos.x.toDouble(),pos.y.toDouble(),pos.z.toDouble())-p.center
                    assertTrue("Actual contact inside rounded face ${track.id}/$i",p.contains(delta))
                    assertTrue("12–24 mm physical width",p.w in .012.. .024)
                    assertTrue("Original narrow depth is retained",p.d in .008.. .010)
                    // Dense passive rebounds accumulate micrometre-scale drift;
                    // containment above is authoritative, not an exact centre snap.
                    assertTrue("Contact stays in the central 0.1 mm patch",delta.length()<.0001)
                    assertTrue("One passive rebound onward",s.outgoingNormalSpeed>0&&s.incomingNormalSpeed<0)
                    rows+="${track.id},$i,${p.w},${p.d},${p.cornerRadius},${s.impactTime},${delta.length()},${s.physicalImpactCount}"
                    total++
                }
            }
            assertTrue(s.completed)
            assertEquals(track.notes.size,s.physicalImpactCount)
            assertTrue("Exactly one physical contact on every pad",s.physicalImpactsPerPlate().all{it==1})
        }
        assertEquals(2348,total)
        val cwd=File(System.getProperty("user.dir"));val root=if(cwd.name=="app")cwd.parentFile else cwd
        File(root,"build/reports/simulation/v027-rounded-pad-contacts.csv").apply{requireNotNull(parentFile).mkdirs()}.writeText(rows.joinToString("\n"))
    }
    @Test fun roundedCornerCutsAndActualNarrowWidthCanRejectAFormerlyValidShot(){
        val course=MusicCourses.get(0);val pad=course.impacts.first()
        val side=pad.n.cross(pad.a)
        assertFalse("Removed rounded corner is not a hidden rectangular collider",pad.contains(pad.a*(pad.w*.5-.00001)+side*(pad.d*.5-.00001)))
        assertTrue(pad.contains(pad.a*(pad.w*.5-.00001)))
        val oldFirst=pad.copy(w=.049,cornerRadius=0.0)
        val wide=MusicSimulation(MusicCourse(course.id,course.runSeconds,course.returnOffset,course.lead,listOf(oldFirst)+course.impacts.drop(1),course.rails))
        val narrow=MusicSimulation(course)
        for(s in listOf(wide,narrow)){s.place();s.sidewaysImpulse(pad.a.x*.123,pad.a.z*.123);repeat(100){s.step(1f/480)}}
        assertTrue("Former wide face catches this offset",wide.noteIndex>=0)
        assertEquals("Reduced physical face really misses",-1,narrow.noteIndex)
    }
    @Test fun repeatPhysicalImpactIsDetectedRatherThanHiddenByOneNoteAudioGating(){
        val up=MusicVector(0.0,1.0,0.0);val axis=MusicVector(1.0,0.0,0.0)
        val first=MusicImpact(MusicVector(0.0,0.0,0.0),up,axis,.024,.01,.5,.001)
        val second=first.copy(center=MusicVector(0.0,-1.0,0.0))
        val entry=MusicRail(-1,0.0,listOf(MusicVector(0.0,.10,0.0),MusicVector(0.0,.05,0.0)))
        val exit=MusicRail(1,0.0,listOf(MusicVector(0.0,-1.0,0.0),MusicVector(0.0,-2.0,0.0)))
        val s=MusicSimulation(MusicCourse(0,2f,0.0,0.0,listOf(first,second),listOf(entry,exit)),false)
        s.place();repeat(960){s.step(1f/480)}
        assertTrue(s.missed);assertEquals(2,s.physicalImpactCount)
        assertEquals(2,s.physicalImpactsPerPlate()[0])
        assertTrue(s.failureReason.contains("hit=0 expected=1"))
    }
    @Test fun allSongsChooseDistinctOpticalSkinsWithoutAddingPhysicsControls(){
        assertEquals(ArcadeCatalog.tracks.map{it.id},MusicSkins.all.map{it.trackId})
        assertEquals(20,MusicSkins.all.map{it.id}.distinct().size)
        assertEquals(6,MusicSkins.all.map{it.family}.distinct().size)
        assertTrue(MusicSkins.all.all{it.sparkCount in 0..2&&it.contactSeconds in .1f.. .22f})
        assertEquals("moonlit_crystal",MusicSkins.forTrack(5).family)
        assertEquals("celadon_stream",MusicSkins.forTrack(18).family)
        assertSame(MusicSkins.generic,MusicSkins.forTrack(99))
        assertEquals(20,MusicSkins.all.map{MusicSkins.routeAsset(it.trackId)}.distinct().size)
    }
    @Test fun trackSwitchWaitsForMatchingAssetsAndMissingPaletteCannotBlockTheSong(){
        val session=MusicSkinSwitch();val first=session.begin(0)
        assertTrue(session.commit(first,0,true));assertEquals(0,session.active.trackId)
        val slow=session.begin(18);val latest=session.begin(5)
        assertEquals("Pending loads leave previous skin intact",0,session.appliedTrack)
        assertFalse("Slow old route cannot win",session.commit(slow,5,true))
        assertTrue(session.commit(latest,5,true));assertEquals(5,session.active.trackId)
        val noPalette=session.begin(18)
        assertTrue(session.commit(noPalette,18,false))
        assertEquals("The selected song still commits",18,session.appliedTrack)
        assertSame("Optional tint uses a safe generic fallback",MusicSkins.generic,session.active)
    }
}
