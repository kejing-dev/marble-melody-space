package com.kejing.spatialarcade.domain.usecase

import com.kejing.spatialarcade.data.repository.ArcadeCatalog
import com.kejing.spatialarcade.domain.model.*
import com.kejing.spatialarcade.ui.feature.arcade.*
import com.kejing.spatialarcade.ui.feature.arcade.components.MusicLibraryCopy
import org.junit.Assert.*
import org.junit.Test

class MusicLanguageTest {
    @Test fun persistedLanguageAlsoAppliesDuringTrackLoadingAndAfterFailure(){
        val vm=ArcadeViewModel(GameKind.C,initialEnglish=true)
        vm.ready();vm.onEvent(ArcadeEvent.Track(18));vm.loading()
        assertFalse(vm.state.value.ready)
        assertEquals("Preparing model…",MusicLibraryCopy.preparing(vm.state.value.english))
        assertEquals("12 s elapsed",MusicLibraryCopy.elapsed(12,vm.state.value.english))
        vm.onEvent(ArcadeEvent.MusicLanguage(false))
        assertEquals("正在准备模型…",MusicLibraryCopy.preparing(vm.state.value.english))
        assertEquals(18,vm.engine.snapshot.track)
        vm.onEvent(ArcadeEvent.MusicLanguage(true));vm.error("native error")
        assertEquals("Couldn't load the model",MusicLibraryCopy.loadError(vm.state.value.english))
        vm.loading();vm.ready();assertTrue(vm.state.value.english)
    }
    @Test fun changingLanguageInsideBookPreservesPausedSongAndPersistsAcrossRecreation(){
        var stored=false
        val vm=ArcadeViewModel(GameKind.C,saveMusicLanguage={stored=it});vm.ready()
        vm.onEvent(ArcadeEvent.Track(2));vm.onEvent(ArcadeEvent.MusicPlayback)
        repeat(100){vm.engine.step(.01f)}
        vm.selectFromPhysicalBook();vm.onEvent(ArcadeEvent.MusicBookOpened)
        val time=vm.engine.musicPhysics.time;val position=vm.engine.ball;val contacts=vm.engine.musicPhysics.physicalImpactCount
        repeat(2){vm.onEvent(ArcadeEvent.MusicLanguage(true))}
        assertTrue(stored);assertTrue(vm.state.value.english);assertTrue(vm.state.value.library)
        assertEquals(2,vm.engine.snapshot.track);assertEquals(Phase.PAUSED,vm.engine.snapshot.phase)
        assertEquals(time,vm.engine.musicPhysics.time,0f);assertEquals(position,vm.engine.ball)
        assertEquals(contacts,vm.engine.musicPhysics.physicalImpactCount)
        val reopened=ArcadeViewModel(GameKind.C,initialEnglish=stored)
        assertTrue(reopened.state.value.english)
        vm.onEvent(ArcadeEvent.MusicLanguage(false));assertFalse(stored)
    }
    @Test fun bookTranslationsCoverEverySongAndKeepDuration(){
        assertEquals(20,ArcadeCatalog.tracks.size)
        val english=ArcadeCatalog.tracks.map{MusicLibraryCopy.title(it,true)}
        assertEquals(20,english.toSet().size)
        for(track in ArcadeCatalog.tracks){
            assertEquals(track.title,MusicLibraryCopy.title(track,false))
            assertTrue(MusicLibraryCopy.title(track,true).none{it in '\u4e00'..'\u9fff'})
            assertEquals(track.subtitle.substringBefore(" · "),MusicLibraryCopy.subtitle(track,true).substringBefore(" · "))
        }
    }
}
