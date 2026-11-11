package com.kejing.spatialarcade.domain.usecase

import com.kejing.spatialarcade.data.repository.*
import com.kejing.spatialarcade.domain.model.*
import com.kejing.spatialarcade.ui.feature.arcade.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GameplayV091Test {
    private fun game(k:GameKind)=LocalGameRepository().let{GameRulesUseCase(k,it.contacts(),it.score())}
    private fun launch(e:GameRulesUseCase){e.grab("plunger");e.drag(0f,0f,.027f);e.release()}
    private fun waitForReturn(e:GameRulesUseCase){for(i in 0..120*90){e.step(1f/120);if(e.returnProgress>=0)return};fail("No return")}
    @Test fun returnRouteAcceptsFullDrainWidthAndEndsExactlyAtLauncher(){
        for(x in listOf(-.116f,-.08f,0f,.08f,.112f)){
            val start=Point(x,.067f,.186f);val route=OrbitReturnRoute(start)
            assertEquals(start,route.position(0f));assertEquals(OrbitReturnRoute.dock,route.position(1f))
            var previous=start
            for(i in 1..240){val p=route.position(i/240f)
                assertTrue(p.x in -.1161f.. .1331f);assertTrue(p.z in .1599f.. .1901f)
                val distance=sqrt((p.x-previous.x).pow(2)+(p.y-previous.y).pow(2)+(p.z-previous.z).pow(2))
                assertTrue("No teleport: $distance",distance<.003f);previous=p
            }
        }
    }
    @Test fun returningBallCannotRelaunchAndPausePreservesRoute(){
        val e=game(GameKind.B);launch(e);waitForReturn(e)
        assertEquals(3,e.snapshot.lives);assertNotEquals(e.initialBall(),e.ball)
        e.grab("plunger");e.drag(0f,0f,.045f);e.release();assertEquals(-1f,e.launchProgress,0f)
        repeat(70){e.step(1f/120)};val middle=e.ball;e.pause();repeat(200){e.step(1f/120)};assertEquals(middle,e.ball)
        e.resume();repeat(240){e.step(1f/120)}
        assertEquals(e.initialBall(),e.ball);assertEquals(2,e.snapshot.lives);assertEquals(Phase.READY,e.snapshot.phase)
        repeat(200){e.step(1f/120)};assertEquals(2,e.snapshot.lives)
        launch(e);assertEquals(Phase.PLAYING,e.snapshot.phase)
    }
    @Test fun finalBallAlsoReturnsBeforeResultAndResetCancelsReturn(){
        val e=game(GameKind.B)
        repeat(3){launch(e);waitForReturn(e);repeat(240){e.step(1f/120)}}
        assertEquals(Phase.RESULT,e.snapshot.phase);assertEquals(0,e.snapshot.lives);assertEquals(e.initialBall(),e.ball)
        // The model launcher is the only new-round action after the last ball.
        e.grab("plunger");assertEquals(Phase.HELD,e.snapshot.phase);assertEquals(3,e.snapshot.lives);assertEquals(0,e.snapshot.score)
        e.drag(0f,0f,.027f);e.release();assertEquals(Phase.PLAYING,e.snapshot.phase)
        waitForReturn(e);e.reset();assertEquals(-1f,e.returnProgress,0f);assertEquals(e.initialBall(),e.ball)
        e.grab("plunger");e.drag(0f,0f,.02f);e.release(true)
        assertEquals("Cancelling a pull must not require a resume button",Phase.READY,e.snapshot.phase)
        assertEquals(3,e.snapshot.lives)
    }
    @Test fun collisionCuesAreRealBoundedAndResetDoesNotReplayOldEvents(){
        val kinds=mutableSetOf<OrbitImpactKind>();val e=OrbitSimulation();var lastSerial=0
        for(power in 1..20){e.start(power/20f);assertNull(e.impact);var lastTime=-1f
            for(i in 0..120*40){e.step(1f/120,if(i%3==0)if(e.ball.x<0)-1f else 1f else 0f)
                e.impact?.let{hit->if(hit.serial!=lastSerial){assertTrue(hit.serial>lastSerial);assertTrue(e.age-lastTime>=.059f);assertTrue(hit.strength in .2f..1f);lastSerial=hit.serial;lastTime=e.age;kinds+=hit.kind}}
                if(e.drained)break
            }
        }
        assertEquals(OrbitImpactKind.entries.toSet(),kinds)
    }
    @Test fun eightGoalsAutomaticallyLoopAndMissesRetryWithoutAttemptLimit(){
        val e=game(GameKind.A)
        for(i in 0..7){assertEquals(i,e.snapshot.layout);e.demo();repeat(300){e.step(1f/120)};assertEquals(i+1,e.snapshot.goalSerial);assertEquals(Phase.READY,e.snapshot.phase)}
        assertEquals(0,e.snapshot.layout);assertEquals(16,e.snapshot.score)
        repeat(7){e.setContact(0);e.grab("boot");e.drag(0f,0f,.07f);e.release();repeat(300){e.step(1f/120)}}
        assertEquals(0,e.snapshot.layout);assertEquals(16,e.snapshot.score);assertEquals(Phase.READY,e.snapshot.phase)
    }
    @Test fun nineContactPointsHaveFourDistinctDiagonals(){
        val e=game(GameKind.A);assertEquals(9,e.contacts.size)
        for(i in 5..8){val c=e.contacts[i];assertTrue(c.u!=0f&&c.v!=0f);assertTrue(c.curve*c.u<0);assertTrue(c.lift*c.v<0)}
    }
    @Test fun footballUiCannotPauseOrOpenLevelSelection(){
        val vm=ArcadeViewModel(GameKind.A);vm.onEvent(ArcadeEvent.Demo);vm.onEvent(ArcadeEvent.Pause)
        assertEquals(Phase.PLAYING,vm.engine.snapshot.phase);vm.onEvent(ArcadeEvent.OpenLibrary);assertFalse(vm.state.value.library)
        vm.onEvent(ArcadeEvent.Layout(5));assertEquals(0,vm.engine.snapshot.layout)
    }
}
