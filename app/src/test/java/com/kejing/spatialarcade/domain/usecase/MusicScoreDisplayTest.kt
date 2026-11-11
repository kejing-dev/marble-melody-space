package com.kejing.spatialarcade.domain.usecase

import com.kejing.spatialarcade.domain.model.*
import com.kejing.spatialarcade.data.repository.*
import com.kejing.spatialarcade.ui.feature.arcade.*
import org.junit.Test
import org.junit.Assert.*

class MusicScoreDisplayTest {
    @Test fun notationPreservesPitchAndSharps(){
        assertEquals(0,MusicScoreDisplay.staffStep(64));assertEquals(-2,MusicScoreDisplay.staffStep(60))
        assertEquals(7,MusicScoreDisplay.staffStep(76));assertTrue(MusicScoreDisplay.sharp(66));assertFalse(MusicScoreDisplay.sharp(65))
        for(track in ArcadeCatalog.tracks)for(note in track.notes){
            for(slot in 0 until MusicScoreDisplay.slots){
                val relativeY=MusicScoreDisplay.pitchY(slot,note.midi)-MusicScoreDisplay.rowOrigin(slot)
                assertTrue("Staff pitch ${note.midi} row ${MusicScoreDisplay.row(slot)}",relativeY in -.043f.. .083f)
            }
            if(MusicScoreDisplay.octaveRaised(note.midi))assertEquals(note.midi-12,MusicScoreDisplay.displayMidi(note.midi))
        }
    }
    @Test fun highlightPagesOnlyFollowActualContacts(){
        assertEquals(0,MusicScoreDisplay.pageStart(-1));assertEquals(0,MusicScoreDisplay.pageStart(7));assertEquals(0,MusicScoreDisplay.pageStart(8))
        assertEquals(0,MusicScoreDisplay.pageStart(15));assertEquals(0,MusicScoreDisplay.pageStart(16))
        assertEquals(0,MusicScoreDisplay.pageStart(23));assertEquals(24,MusicScoreDisplay.pageStart(24))
        val notes=ArcadeCatalog.tracks[0].notes
        assertEquals(0f,MusicScoreDisplay.sweep(notes,-1,100f),0f)
        assertEquals(7f,MusicScoreDisplay.sweep(notes,7,100f),0f)
        assertEquals(0,MusicScoreDisplay.row(7));assertEquals(1,MusicScoreDisplay.row(8))
        assertEquals(7f,MusicScoreDisplay.sweep(notes,15,100f),0f)
        assertEquals(0f,MusicScoreDisplay.sweep(notes,16,0f),0f)
        assertEquals(2,MusicScoreDisplay.row(16))
        assertEquals(7f,MusicScoreDisplay.sweep(notes,23,100f),0f)
        assertEquals(0f,MusicScoreDisplay.sweep(notes,24,0f),0f)
        assertEquals(0,MusicScoreDisplay.row(24))
    }
    @Test fun laterRowsKeepAllCompletedRowsGoldAndFutureNotesUnplayed(){
        for(track in ArcadeCatalog.tracks)for(contact in track.notes.indices){
            val start=MusicScoreDisplay.pageStart(contact)
            val visible=(start until minOf(start+MusicScoreDisplay.slots,track.notes.size))
            assertTrue(visible.contains(contact))
            assertEquals(1,visible.count{MusicScoreDisplay.tone(it,contact)==MusicScoreDisplay.Tone.Current})
            assertEquals(contact-start+1,visible.count{MusicScoreDisplay.tone(it,contact).gold})
            if(MusicScoreDisplay.row(contact)>0){
                assertTrue((start until start+MusicScoreDisplay.row(contact)*8).all{MusicScoreDisplay.tone(it,contact)==MusicScoreDisplay.Tone.Played})
            }
            assertTrue(visible.filter{it>contact}.all{!MusicScoreDisplay.tone(it,contact).gold})
        }
    }
    @Test fun allThreeRowsKeepEveryCatalogPitchInsideTheFloorScore(){
        for(track in ArcadeCatalog.tracks)for(note in track.notes)for(slot in 0 until MusicScoreDisplay.slots){
            val worldZ=-.100f-MusicScoreDisplay.pitchY(slot,note.midi)
            assertTrue("Note ${note.midi} slot $slot escapes floor score",worldZ-.006f>=-.3201f&&worldZ+.006f<=.1201f)
            val x=MusicScoreDisplay.x(slot)-.045f
            assertTrue(x-.012f>=-.348f&&x+.012f<=.258f)
        }
        assertTrue(MusicScoreDisplay.rowOrigin(0)-MusicScoreDisplay.rowOrigin(8)>.118f)
        assertTrue(MusicScoreDisplay.rowOrigin(8)-MusicScoreDisplay.rowOrigin(16)>.118f)
    }
    @Test fun speedDoesNotSkipContactsAndPausePreservesTheScoreAtAllThreeRates(){
        for(rate in listOf(.75f,1f,1.25f)){
            val e=LocalGameRepository().let{GameRulesUseCase(GameKind.C,it.contacts(),it.score())};e.toggleMusicPlayback()
            val seen=mutableListOf<Int>()
            repeat(((MusicCourses.get(0).runSeconds+12)*60/rate).toInt()){
                e.step(rate/60);seen.addAll(e.consumeMusicNotes())
                if(it==240){e.toggleMusicPlayback();val p=e.ball;val t=e.musicPhysics.time;repeat(50){e.step(rate/60)};assertEquals(p,e.ball);assertEquals(t,e.musicPhysics.time,0f);e.toggleMusicPlayback()}
            }
            assertEquals(e.notes.indices.toList(),seen);assertEquals(Phase.READY,e.snapshot.phase)
        }
    }
    @Test fun bookPausesAndControlsAreClampedWithoutChangingSong(){
        val vm=ArcadeViewModel(GameKind.C);vm.ready();vm.onEvent(ArcadeEvent.MusicPlayback);vm.engine.step(.5f)
        vm.onEvent(ArcadeEvent.OpenLibrary);assertEquals(Phase.PAUSED,vm.engine.snapshot.phase)
        val pos=vm.engine.ball;vm.onEvent(ArcadeEvent.MusicSpeed(20f));vm.onEvent(ArcadeEvent.MusicVolume(-1f))
        assertEquals(pos,vm.engine.ball);assertEquals(1.25f,vm.state.value.musicSpeed,0f);assertEquals(0f,vm.state.value.musicVolume,0f)
        vm.onEvent(ArcadeEvent.MusicSpeed(Float.NaN));assertEquals(1.25f,vm.state.value.musicSpeed,0f)
    }
}
