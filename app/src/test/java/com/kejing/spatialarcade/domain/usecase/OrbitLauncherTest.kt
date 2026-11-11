package com.kejing.spatialarcade.domain.usecase

import com.kejing.spatialarcade.data.repository.LocalGameRepository
import com.kejing.spatialarcade.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class OrbitLauncherTest {
    private fun game()=LocalGameRepository().let{GameRulesUseCase(GameKind.B,it.contacts(),it.score())}
    @Test fun ballWaitsForPistonContactAndStrongerPullTransfersMoreEnergy(){
        val speeds=mutableListOf<Float>()
        for(pull in listOf(.006f,.023f,.045f)){
            val e=game();e.grab("plunger");e.drag(0f,0f,pull);e.release()
            assertEquals(pull,e.launcher.displacement,.00001f);assertEquals(e.initialBall(),e.ball)
            e.step(1f/120);assertEquals(e.initialBall(),e.ball)
            assertTrue(e.launcher.displacement<pull&&e.launcher.displacement>0f)
            repeat(30){e.step(1f/120)}
            assertTrue(e.launcher.fired);assertNotEquals(e.initialBall(),e.ball);speeds+=e.launcher.launchSpeed
            repeat(180){e.step(1f/120)};assertTrue(abs(e.launcher.displacement)<.0001f)
        }
        assertTrue(speeds.zipWithNext().all{it.second>it.first})
    }
    @Test fun cancellingAndTappingDoNotLaunchOrDeductBalls(){
        val e=game();e.grab("plunger");e.release();assertEquals(Phase.READY,e.snapshot.phase)
        e.grab("plunger");e.drag(0f,0f,.045f);e.release(true)
        repeat(240){e.step(1f/120)}
        assertEquals(Phase.READY,e.snapshot.phase);assertEquals(3,e.snapshot.lives)
        assertEquals(e.initialBall(),e.ball);assertEquals(0,e.launcher.serial)
        assertEquals(0f,e.launcher.displacement,.0001f)
    }
    @Test fun eventHistoryRetainsImmediatePreviousMessageWithoutFrameDuplicates(){
        val e=game();val ready=e.snapshot.status;e.grab("plunger")
        assertEquals(ready,e.snapshot.previousStatus);val charging=e.snapshot.status;val serial=e.snapshot.statusSerial
        repeat(5){e.drag(0f,0f,.005f)}
        assertEquals(serial,e.snapshot.statusSerial);assertEquals(ready,e.snapshot.previousStatus)
        e.release();assertEquals(charging,e.snapshot.previousStatus)
        var previous=e.snapshot
        repeat(1800){
            e.step(1f/120)
            if(e.snapshot.statusSerial!=previous.statusSerial){assertEquals(previous.status,e.snapshot.previousStatus)}
            previous=e.snapshot
        }
        assertTrue(e.snapshot.statusSerial>serial+2)
    }
    @Test fun railUsesArcLengthAndReachesTheOrbitWithoutPositionJump(){
        assertEquals(Point(.133f,.074f,.16f),OrbitLaunchRail.position(0f))
        assertEquals(Point(-.057f,.074f,-.156f).x,OrbitLaunchRail.position(10f).x,.0001f)
        var prev=OrbitLaunchRail.position(0f)
        for(i in 1..1000){val p=OrbitLaunchRail.position(OrbitLaunchRail.length*i/1000)
            assertTrue(sqrt((p.x-prev.x).pow(2)+(p.y-prev.y).pow(2)+(p.z-prev.z).pow(2))<.00055f);prev=p}
    }
}
