package com.kejing.spatialarcade.domain.usecase
import org.junit.Test
import org.junit.Assert.*
import com.kejing.spatialarcade.data.repository.LocalGameRepository
import com.kejing.spatialarcade.domain.model.*
class GameRulesUseCaseTest {
    private fun game(k:GameKind)=LocalGameRepository().let{GameRulesUseCase(k,it.contacts(),it.score())}
    @Test fun oppositeContactsBendOppositeWays(){val e=game(GameKind.A);val center=e.shot(.5f,0);assertTrue(e.shot(.5f,1).x>center.x);assertTrue(e.shot(.5f,2).x<center.x);assertTrue(e.shot(.5f,3).y<center.y);assertTrue(e.shot(.5f,4).y>center.y)}
    @Test fun cancelledKickDoesNotSpendShot(){val e=game(GameKind.A);e.grab("boot");e.drag(0f,0f,.07f);e.release(true);repeat(120){e.step(1f/60)};assertEquals(0,e.snapshot.count);assertEquals(e.initialBall(),e.ball)}
    @Test fun fiveGoalsAdvanceWithoutEndingRound(){val e=game(GameKind.A);repeat(5){e.demo();repeat(300){e.step(1f/120)}};assertEquals(5,e.snapshot.count);assertEquals(5,e.snapshot.layout);assertEquals(Phase.READY,e.snapshot.phase)}
    @Test fun pinballLaunchDoesNotWaitForHandle(){for(power in listOf(.002f,.022f,.045f)){val e=game(GameKind.B);e.grab("plunger");e.drag(0f,0f,power);e.release();assertEquals(Phase.PLAYING,e.snapshot.phase);val start=e.ball;repeat(100){e.step(1f/120)};assertNotEquals(start,e.ball);repeat(70){e.step(1f/120)};assertEquals(-1f,e.launchProgress,0f);assertEquals(3,e.snapshot.lives);e.grab("handle");e.release();assertEquals(Phase.PLAYING,e.snapshot.phase)}}
    @Test fun musicRequiresReleaseNearTheSuspendedEntrance(){val e=game(GameKind.C);e.grab("ball");e.drag(.2f,0f,0f);e.step(.1f);assertEquals(Phase.HELD,e.snapshot.phase);e.release();assertEquals(Phase.READY,e.snapshot.phase);e.demo();assertEquals(Phase.PLAYING,e.snapshot.phase);repeat(((e.notes.last().time+e.notes.last().duration+8)*60).toInt()){e.step(1f/60)};assertEquals(e.notes.size,e.snapshot.count);assertTrue(e.musicPhysics.completed);assertEquals(0f,e.musicPhysics.ballVisibility,0f)}
    @Test fun emptyMusicStopsCleanly(){val e=GameRulesUseCase(GameKind.C,LocalGameRepository().contacts(),emptyList());e.demo();repeat(100){e.step(1f/60)};assertEquals(Phase.READY,e.snapshot.phase);assertTrue(e.snapshot.status.contains("曲谱为空"))}
    @Test fun paddleClampsAndReleasePreservesLives(){val e=game(GameKind.D);e.grab("paddle");e.drag(10f,10f,10f);assertEquals(e.paddleLimit,e.paddle,.0001f);e.release();val p=e.ball;e.step(.1f);assertEquals(p,e.ball);assertEquals(3,e.snapshot.lives)}
    @Test fun levelsHaveTargetsAndExcludeSteelFromCompletion(){val e=game(GameKind.D);for(i in 0..5){e.setLayout(i);assertEquals(80,e.bricks.size);assertEquals(e.bricks.count{it>0},e.snapshot.remaining);assertTrue(e.snapshot.remaining>20);assertTrue(e.bricks.count{it==0}>0)};e.setLayout(1);assertTrue(e.bricks.any{it==-1})}
    @Test fun kickPowerMatchesPullDistanceExactly(){val e=game(GameKind.A);e.grab("boot");e.drag(0f,0f,.0175f);assertEquals(.25f,e.snapshot.power,.00001f);e.drag(0f,0f,.0175f);assertEquals(.5f,e.snapshot.power,.00001f);e.drag(0f,0f,1f);assertEquals(1f,e.snapshot.power,0f);e.drag(0f,0f,-1f);assertEquals(0f,e.snapshot.power,0f)}
    @Test fun untouchedBootDoesNotSpendShot(){val e=game(GameKind.A);e.grab("boot");e.release();assertEquals(Phase.READY,e.snapshot.phase);assertEquals(0,e.snapshot.kickSerial)}
    @Test fun changingContactInFlightOnlyAffectsNextShot(){val a=game(GameKind.A);val b=game(GameKind.A);a.demo();b.demo();a.setContact(4);repeat(25){a.step(1f/120);b.step(1f/120)};assertEquals(b.ball,a.ball);assertEquals(4,a.snapshot.contact)}
    @Test fun aValidGoalEmitsExactlyOneGoalSoundEvent(){val e=game(GameKind.A);e.setContact(0);e.demo();repeat(200){e.step(1f/120)};assertEquals(1,e.snapshot.goalSerial);assertEquals(1,e.snapshot.kickSerial);assertEquals(2,e.snapshot.score)}
    @Test fun kickLevelsChangeDistanceAndWall(){val e=game(GameKind.A);val z=e.ball.z;e.setLayout(3);assertTrue(e.ball.z>z);assertEquals(4,e.kickLevel.defenders)}
    @Test fun allAlbumsFinishWithoutACollectionRun(){val e=game(GameKind.C);for(i in 0..2){e.selectTrack(i);assertEquals(Phase.READY,e.snapshot.phase);assertEquals(-1,e.noteIndex);e.demo();assertEquals(Phase.PLAYING,e.snapshot.phase);repeat(((e.notes.last().time+e.notes.last().duration+8)*60).toInt()){e.step(1f/60)};assertEquals(e.notes.size,e.snapshot.count);assertTrue(e.musicPhysics.completed);assertEquals(0f,e.musicPhysics.ballVisibility,0f)}}
    @Test fun delayRailsHaveDifferentPhysicalLengthsWithTheSameMaterialDrag(){
        val rails=(0..19).flatMap{MusicCourses.get(it).rails.drop(1).dropLast(1)}
        assertTrue(rails.size>20);val lengths=rails.map{it.lengths.sum()}
        assertTrue(lengths.max()-lengths.min()>.04);assertTrue("Compact connectors, no timing helices",lengths.max()<.26);assertEquals(listOf(12.0),rails.map{it.drag}.distinct())
    }
    @Test fun switchingAlbumsStopsPreviousTrack(){val e=game(GameKind.C);e.demo();repeat(90){e.step(1f/60)};e.selectTrack(2);assertEquals(Phase.READY,e.snapshot.phase);assertEquals(0,e.snapshot.count);assertEquals(com.kejing.spatialarcade.data.repository.ArcadeCatalog.tracks[2].notes.size,e.notes.size)}
    @Test fun levelCollisionProducesARealHitEvent(){val e=game(GameKind.D);e.demo();repeat(900){if(e.snapshot.phase==Phase.PLAYING)e.drag(e.ball.x-e.paddle,0f,0f);e.step(1f/120)};assertNotNull(e.hit);assertTrue(e.snapshot.score>0)}
    @Test fun resettingLevelClearsComboAndRestoresTargets(){val e=game(GameKind.D);e.demo();repeat(400){e.drag(e.ball.x-e.paddle,0f,0f);e.step(1f/120)};e.setLayout(5);assertEquals(0,e.snapshot.combo);assertEquals(0,e.snapshot.score);assertNull(e.hit);assertEquals(Phase.READY,e.snapshot.phase)}
    @Test fun fixedStepProducesConsistentResults(){val a=game(GameKind.D);val b=game(GameKind.D);a.demo();b.demo();repeat(60){a.step(1f/60)};repeat(120){b.step(1f/120)};assertEquals(a.ball.x,b.ball.x,.0001f);assertEquals(a.ball.y,b.ball.y,.0001f)}
}
