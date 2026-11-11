package com.kejing.spatialarcade.domain.usecase

import com.kejing.spatialarcade.data.repository.*
import com.kejing.spatialarcade.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GameplayV09Test {
    private fun game(k:GameKind)=LocalGameRepository().let{GameRulesUseCase(k,it.contacts(),it.score())}
    private fun report(name:String,text:String){
        val cwd=File(requireNotNull(System.getProperty("user.dir")));val root=if(cwd.name=="app")requireNotNull(cwd.parentFile) else cwd
        File(root,"build/reports/simulation/$name").apply{requireNotNull(parentFile).mkdirs()}.apply{requireNotNull(parentFile).mkdirs();writeText(text)}
    }
    @Test fun everyKickHasForgivingSolutionsAndNoUniversalShot(){
        val solutions=mutableListOf<Set<Pair<Int,Int>>>()
        val rows=mutableListOf("level,contact,power,goal")
        for((i,l)in ArcadeCatalog.kicks.withIndex()){
            val valid=mutableSetOf<Pair<Int,Int>>()
            for(c in LocalGameRepository().contacts().indices)for(p in 4..100){
                val e=game(GameKind.A);e.setLayout(i);e.setContact(c);e.grab("boot");e.drag(0f,0f,.07f*p/100);e.release()
                repeat(240){e.step(1f/120)}
                if(e.snapshot.score==2)valid+=c to p
                rows+="$i,$c,$p,${e.snapshot.score==2}"
                assertEquals("one outcome per kick",1,e.snapshot.count)
            }
            assertTrue("${l.title} has >= 10 percentage points of tolerance",valid.size>=10)
            assertTrue("example really scores: ${l.title}",l.exampleContact to (l.examplePower*100).toInt() in valid)
            solutions+=valid
        }
        assertTrue(solutions.reduce{a,b->a.intersect(b)}.isEmpty())
        assertTrue("Changing contact alone at a fixed power cannot solve every level",solutions.map{set->set.map{it.second}.toSet()}.reduce{a,b->a.intersect(b)}.isEmpty())
        assertTrue(solutions[2].all{it.first in listOf(1,6,8)});assertTrue(solutions[3].all{it.first in listOf(2,5,7)})
        for((index,contact)in listOf(4 to 8,5 to 7,6 to 6,7 to 5))assertTrue("diagonal needed at level $index",solutions[index].all{it.first==contact})
        assertTrue(0 to 30 in solutions[0]);assertFalse(0 to 90 in solutions[0])
        assertTrue(3 to 63 in solutions[1]);assertFalse(3 to 25 in solutions[1])
        report("kick-solutions-v09.csv",rows.joinToString("\n"))
    }
    @Test fun footballUsesOnlySpawnAndWallPositionsAsSceneVariables(){
        for(i in ArcadeCatalog.kicks.indices){val e=game(GameKind.A);e.setLayout(i)
            assertEquals(4,e.kickLevel.defenders);assertFalse(e.kickLevel.keeper)
            assertEquals(e.kickLevel.startX,e.ball.x,0f)
            val bootEnd=e.kickLevel.startZ+.04f+.07f+.033f;assertTrue(bootEnd<.35f)
            e.grab("boot");e.drag(.5f,0f,.035f);assertEquals(e.kickLevel.startX,e.shot(.2f,0).x,0f)
        }
    }
    @Test fun idleOrbitStaysPlayableAndAllCosmicSystemsAreReachable(){
        val rows=mutableListOf("power,seconds,points,hits,saves,warps,comets,nebulaCharge")
        val times=mutableListOf<Float>();var totalWarps=0;var totalComets=0;var totalCharge=0f
        for(p in 1..20){val e=OrbitSimulation();e.start(p/20f)
            for(i in 0 until 120*120){e.step(1f/120,0f);assertTrue(e.ball.x.isFinite()&&e.ball.y.isFinite()&&e.ball.z.isFinite());if(e.drained)break}
            times+=e.age;totalWarps+=e.warps;totalComets+=e.cometHits;totalCharge+=e.charge
            rows+="${p/20f},${e.age},${e.points},${e.hits},${e.saves},${e.warps},${e.cometHits},${e.charge}"
            assertEquals("No invisible automatic save",0,e.saves);assertTrue("no unbounded idle loop $p: ${e.age}",e.drained)
        }
        report("orbit-idle-v09.csv",rows.joinToString("\n"))
        println("ORBIT idle seconds ${times.sorted()} warps=$totalWarps comets=$totalComets charge=$totalCharge")
        assertTrue(totalWarps>0);assertTrue(totalComets>0);assertTrue(totalCharge>0)
    }
    @Test fun SkilledOrbitCanStayUpForAtLeastSixtySeconds(){
        val e=OrbitSimulation();e.start(.65f)
        repeat(65*120){e.step(1f/120,if(e.ball.x<0)-1f else 1f)}
        assertFalse(e.drained);assertTrue(e.age>=60f);assertTrue(e.points>0)
    }
    @Test fun orbitReleaseContinuesAndExplicitPauseStopsTime(){
        val e=game(GameKind.B);e.demo();repeat(180){e.step(1f/120)};e.release();val before=e.snapshot.flightSeconds
        repeat(120){e.step(1f/120)};assertTrue(e.snapshot.flightSeconds>before)
        e.pause();val paused=e.ball;repeat(120){e.step(1f/120)};assertEquals(paused,e.ball)
    }
    @Test fun legacyMusicStartAndPlaybackCapUseTheSameShortDrop(){
        for(t in 0..2)for(launch in listOf(false,true)){
            val e=game(GameKind.C);e.selectTrack(t);assertEquals(e.entry.x,e.ball.x,0f);assertEquals(e.entry.z,e.ball.z,0f)
            if(launch){e.grab("plunger");e.drag(0f,0f,.028f);e.release();repeat(60){e.step(1f/120)};assertTrue(e.ball.y>.067f);assertTrue(e.noteIndex>=0);assertFalse(e.musicPhysics.launching)}else e.demo()
            repeat(((e.notes.last().time+e.notes.last().duration+8)*120).toInt()){e.step(1f/120)}
            assertEquals(e.notes.size,e.snapshot.count);assertEquals(Phase.READY,e.snapshot.phase);assertEquals(0f,e.musicPhysics.ballVisibility,0f)
        }
    }
    @Test fun cancelledPlacementDoesNotStartAndChangingAlbumStopsTheDrop(){
        val e=game(GameKind.C);e.grab("ball");e.drag(.2f,0f,0f);e.release();assertEquals(Phase.READY,e.snapshot.phase)
        e.grab("plunger");e.drag(0f,0f,.04f);e.release(true);assertEquals(Phase.READY,e.snapshot.phase)
        e.grab("plunger");e.drag(0f,0f,.04f);e.release();e.step(.1f);e.selectTrack(2)
        assertEquals(-1f,e.launchProgress,0f);assertEquals(e.initialBall(),e.ball)
    }
    private fun clearCurrentLevel(e:GameRulesUseCase){
        // Last-brick fixture on the live ball path, exercising the production collision and transition.
        e.bricks.fill(0);e.grab("paddle")
        for(i in 0..2000){
            if(e.ball.y in .263f.. .298f){
                val column=kotlin.math.round((e.ball.x+.252f)/.056f).toInt().coerceIn(0,9)
                e.bricks.fill(0);e.bricks[70+column]=1
            }
            e.drag(e.ball.x-e.paddle,0f,0f);e.step(1f/120)
            if(e.levelTransition>0)return
        }
        fail("Last brick did not trigger automatic transition: ${e.snapshot}")
    }

    @Test fun brickProgressesThroughEighteenStagesThenRestoresFullPaddle(){
        val e=game(GameKind.D);var previousScore=0
        for(stage in 0..17){
            assertEquals(stage%6,e.snapshot.layout);assertEquals(stage/6,e.snapshot.circuit)
            assertEquals(1f-(stage/6)/3f,e.paddleScale,.00001f)
            clearCurrentLevel(e);assertTrue(e.snapshot.score>previousScore);previousScore=e.snapshot.score
            e.pause();val layout=e.snapshot.layout;repeat(180){e.step(1f/120)};assertEquals(layout,e.snapshot.layout)
            e.resume();repeat(125){e.step(1f/120)}
        }
        assertEquals(0,e.snapshot.layout);assertEquals(0,e.snapshot.circuit);assertEquals(1f,e.paddleScale,0f)
        assertEquals(previousScore,e.snapshot.score)
    }
}
