package com.kejing.spatialarcade.domain.usecase

import org.junit.Test
import org.junit.Assert.*
import com.kejing.spatialarcade.data.repository.LocalGameRepository
import com.kejing.spatialarcade.domain.model.*

class OrbitObjectivesTest {
    @Test fun distinctTargetsRequiredAndNovaCannotBeFarmedInPlace(){
        val m=OrbitObjectives()
        assertEquals(40,m.contact(OrbitObjectives.beacons[0]));assertEquals(0,m.contact(OrbitObjectives.beacons[0]))
        assertEquals(40,m.contact(OrbitObjectives.beacons[2]));assertEquals(1,m.multiplier)
        assertEquals(300,m.contact(OrbitObjectives.beacons[1]));assertEquals(2,m.multiplier);assertEquals(3,m.count)
        repeat(100){assertEquals(0,m.contact(OrbitObjectives.beacons[it%3]))}
        m.step(7.99f);assertEquals(2,m.multiplier);m.step(.02f)
        assertEquals(0,m.count);assertEquals(1,m.multiplier)
        assertEquals(40,m.contact(OrbitObjectives.beacons[1]));assertEquals(1,m.rounds)
    }
    @Test fun movingMoonsStayWithinBoardAndKeepClearOfLaunchRail(){
        val e=OrbitSimulation();e.start(.6f)
        for(frame in 0..1200){e.step(1f/120,true)
            for(i in 0..1){val m=e.moon(i);assertTrue(m.x-m.radius>-.116f);assertTrue(m.x+m.radius<.112f);assertTrue(m.z-m.radius>-.163f);assertTrue(m.z+m.radius<.1f)}
        }
    }
    @Test fun heldEnergyStrategyHasReachableMissionsAndExactSpend(){
        val repo=LocalGameRepository();var beacons=0;var longest=0f;var pulses=0;var novas=0
        for(p in 1..20){
            val e=GameRulesUseCase(GameKind.B,repo.contacts(),repo.score());e.grab("plunger");e.drag(0f,0f,.045f*p/20);e.release()
            var maxBeacons=0
            for(i in 0..120*100){
                val press=e.launchProgress<0&&e.returnProgress<0
                e.setGravityPedal(press);e.step(1f/120)
                assertTrue(e.snapshot.score>=0)
                maxBeacons=maxOf(maxBeacons,e.orbit.objectives.count)
                if(e.returnProgress>=0)break
            }
            longest=maxOf(longest,e.orbit.age);beacons+=maxBeacons;pulses+=e.gravityPulse.serial
            novas+=e.orbit.objectives.rounds
            assertEquals(e.orbit.points-GravityPulse.cost*e.gravityPulse.serial,e.snapshot.score)
        }
        println("PAID COSMOS longest=$longest beacons=$beacons pulses=$pulses novas=$novas")
        assertTrue(beacons>0);assertTrue(pulses>0);assertTrue(novas>0);assertTrue(longest>=20f)
        val cwd=java.io.File(requireNotNull(System.getProperty("user.dir")))
        val root=if(cwd.name=="app")requireNotNull(cwd.parentFile) else cwd
        java.io.File(root,"build/reports/simulation/v011-energy-gameplay.json").apply{requireNotNull(parentFile).mkdirs()}.writeText("""{"sample_powers":20,"longest_flight_seconds":$longest,"total_beacons":$beacons,"paid_cells":$pulses,"completed_novas":$novas,"nonnegative_balance":true,"exact_spend_accounting":true,"strategy":"Hold continuously after launch. Energy first; buy a cell only on empty. Domain simulation, not native input."}""")
    }
}
