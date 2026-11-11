package com.kejing.spatialarcade.domain.usecase
import com.kejing.spatialarcade.domain.model.*
import com.kejing.spatialarcade.data.repository.LocalGameRepository
import org.junit.Test
import org.junit.Assert.*

class GravityPedalTest {
    @Test fun unpressedFieldIsPermeableAndPressedFieldCoversBothSides(){
        val center=OrbitSimulation.fieldCenter
        for(x in listOf(-.06f,0f,.06f))assertEquals(Point(0f,0f,0f),OrbitSimulation.fieldAcceleration(center.copy(x=x),false))
        val l=OrbitSimulation.fieldAcceleration(center.copy(x=-.06f),true)
        val r=OrbitSimulation.fieldAcceleration(center.copy(x=.06f),true)
        assertTrue(l.z<0&&r.z<0);assertTrue(l.x>0&&r.x<0)
        assertEquals(l.z,r.z,.000001f)
        assertEquals(0f,OrbitSimulation.fieldAcceleration(center,true).x,0f)
        assertEquals(Point(0f,0f,0f),OrbitSimulation.fieldAcceleration(center.copy(z=-.1f),true))
    }
    @Test fun pedalReleasePauseCancelAndResetImmediatelyRemoveForce(){
        val repo=LocalGameRepository();val e=GameRulesUseCase(GameKind.B,repo.contacts(),repo.score());e.demo()
        for(i in 0..2400){e.step(1f/120);if(e.snapshot.score>=50&&e.launchProgress<0)break}
        assertTrue(e.snapshot.score>=50)
        e.setGravityPedal(true);assertTrue(e.gravityField)
        e.setGravityPedal(false);assertFalse(e.gravityField)
        e.setGravityPedal(true);e.pause();assertFalse(e.gravityField);e.resume();assertFalse(e.gravityField)
        e.setGravityPedal(true);e.release(true);assertFalse(e.gravityField)
        e.setGravityPedal(true);e.reset();assertFalse(e.gravityField)
        e.setGravityPedal(true);assertFalse("Cannot energize while ready",e.gravityField)
    }
    @Test fun noInputNeverCreatesAnAutomaticSave(){
        for(power in listOf(.2f,.5f,.8f,1f)){val e=OrbitSimulation();e.start(power)
            repeat(120*120){e.step(1f/120,false)}
            assertTrue(e.drained);assertEquals(0,e.saves);assertFalse(e.shieldOn)
        }
    }
    @Test fun storedCellsPowerAContinuousHoldWithoutChargingPoints(){
        val p=GravityPulse();assertEquals(GravityPulse.Result.STARTED,p.press(true,true,0))
        repeat(9*120){assertNotEquals(GravityPulse.Result.BOUGHT,p.step(1f/120,true,0));assertTrue(p.active)}
        assertEquals(1f,p.cells,.0001f);assertEquals(0,p.serial)
        repeat(100){p.press(true,true,999)}
        assertEquals(0,p.serial);assertEquals(1f,p.cells,.0001f)
    }
    @Test fun emptyBatteryBuysExactlyOneCellPerSecondAndPreservesChange(){
        val p=GravityPulse();p.press(true,true,150)
        repeat(1200){p.step(1f/120,true,150)}
        var score=150
        repeat(300){if(p.step(1f/120,true,score)==GravityPulse.Result.BOUGHT)score-=50;assertTrue(p.active)}
        assertEquals(0,score);assertEquals(3,p.serial);assertEquals(.5f,p.cells,.0001f)
        p.press(false,true,score);assertFalse(p.active)
        repeat(480){p.step(1f/120,true,score)}
        assertEquals(1.5f,p.cells,.0001f)
        p.press(true,true,score);assertTrue(p.active);assertEquals(3,p.serial)
    }
    @Test fun insufficientFundsStopForceWithoutOverdraftOrAutomaticChargingWhileHeld(){
        val p=GravityPulse();p.press(true,true,49)
        repeat(1320){assertNotEquals(GravityPulse.Result.BOUGHT,p.step(1f/120,true,49))}
        assertEquals(0f,p.cells,0f);assertFalse(p.active);assertEquals(0,p.serial)
        repeat(600){p.step(1f/120,true,49)}
        assertEquals(0f,p.cells,0f)
        assertEquals(GravityPulse.Result.BOUGHT,p.step(1f/120,true,50));assertTrue(p.active)
    }
    @Test fun cancelRequiresReleaseAndIneligibleStatesCannotSpend(){
        val p=GravityPulse();p.press(true,true,0);repeat(240){p.step(1f/120,true,0)}
        p.cancel();repeat(120){p.step(1f/120,true,1000)}
        assertFalse(p.active);assertEquals(8f,p.cells,.0001f)
        p.press(false,true,0);p.press(true,true,0);assertTrue(p.active)
        repeat(1000){p.step(1f/120,true,0)}
        assertEquals(GravityPulse.Result.UNAVAILABLE,p.press(true,false,1000))
        repeat(120){p.step(1f/120,false,1000)}
        assertEquals(0,p.serial);assertFalse(p.active)
        p.reset();assertEquals(10f,p.cells,0f);assertFalse(p.down)
    }
    @Test fun lowerFieldCanCatchLateApproachesBeforeTheDrain(){
        assertEquals(.153f,OrbitSimulation.fieldCenter.z,0f)
        for(x in listOf(-.04f,0f,.04f)){
            var z=.165f;var velocity=.24f;var stopped=false
            repeat(120){
                if(!stopped){velocity+=(OrbitSimulation.fieldAcceleration(Point(x,.074f,z),true).z+.027f)/480;z+=velocity/480
                    assertTrue("late approach should not cross return gutter",z<.184f)
                    if(velocity<=0)stopped=true}
            }
            assertTrue(stopped)
        }
    }
}
