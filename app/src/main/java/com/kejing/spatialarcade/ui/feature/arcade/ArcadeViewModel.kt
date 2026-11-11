package com.kejing.spatialarcade.ui.feature.arcade
import androidx.lifecycle.ViewModel
import com.kejing.spatialarcade.domain.model.*
import com.kejing.spatialarcade.domain.usecase.GameRulesUseCase
import com.kejing.spatialarcade.data.repository.GameRepository
import com.kejing.spatialarcade.data.repository.LocalGameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ArcadeViewModel(val kind:GameKind,repository:GameRepository=LocalGameRepository(),initialEnglish:Boolean=false,private val saveMusicLanguage:(Boolean)->Unit={}):ViewModel(){
    val contacts=repository.contacts()
    val engine=GameRulesUseCase(kind,contacts,repository.score())
    private val _state=MutableStateFlow(ArcadeUiState(game=engine.snapshot,selector=kind==GameKind.A,english=kind==GameKind.C&&initialEnglish))
    val state=_state.asStateFlow()
    fun setMusicHudProbe(title:String?){if(com.kejing.spatialarcade.BuildConfig.DEBUG&&kind==GameKind.C)_state.value=_state.value.copy(musicTitleProbe=title)}
    fun sync(){_state.value=_state.value.copy(game=engine.snapshot)}
    /** A real press exits a debug still frame at the start of the selected song.
     * Ordinary presses retain the existing pause/resume clock and momentum. */
    fun playFromPhysicalButton(capturedFrame:Boolean){
        if(capturedFrame)onEvent(ArcadeEvent.Reset)
        onEvent(ArcadeEvent.MusicPlayback)
    }
    /** Shared real/debug book selection. A stale open menu is re-mounted after
     * the physical opening completes; the current song clock is preserved. */
    fun selectFromPhysicalBook():Boolean {
        if(kind!=GameKind.C||!state.value.ready)return false
        onEvent(ArcadeEvent.SelectMusicBook)
        return true
    }
    fun loading(){_state.value=_state.value.copy(ready=false,error=null)}
    fun ready(){_state.value=_state.value.copy(ready=true)}
    fun error(message:String){_state.value=_state.value.copy(error=message)}
    fun onEvent(event:ArcadeEvent){when(event){
        ArcadeEvent.OpenSelector->_state.value=_state.value.copy(selector=kind==GameKind.A)
        is ArcadeEvent.Select->{if(event.id in contacts.indices){engine.setContact(event.id);_state.value=_state.value.copy(pending=event.id)}}
        ArcadeEvent.Confirm->Unit // Older debug clients remain harmless; selection is immediate.
        ArcadeEvent.Cancel->Unit
        ArcadeEvent.Pause->{if(kind!=GameKind.A)engine.pause()};ArcadeEvent.Resume->{if(kind!=GameKind.A)engine.resume()};ArcadeEvent.Reset->{if(kind==GameKind.D||kind==GameKind.A)engine.setLayout(0)else engine.reset()}
        ArcadeEvent.ToggleLanguage->{_state.value=_state.value.copy(english=!_state.value.english);if(kind==GameKind.C)saveMusicLanguage(_state.value.english)}
        is ArcadeEvent.MusicLanguage->if(kind==GameKind.C&&event.english!=_state.value.english){
            _state.value=_state.value.copy(english=event.english);saveMusicLanguage(event.english)
        }
        ArcadeEvent.MusicPlayback->{if(kind==GameKind.C&&_state.value.ready){
            _state.value=_state.value.copy(library=false,musicLibraryPending=false,musicBookOpen=false)
            engine.toggleMusicPlayback()
        }}
        ArcadeEvent.ToggleSound->_state.value=_state.value.copy(muted=!_state.value.muted)
        is ArcadeEvent.MusicControlPress->{if(kind==GameKind.C&&_state.value.ready){
            val s=_state.value
            when(event.control){
                com.kejing.spatialarcade.domain.usecase.MusicControl.SpeedDown->onEvent(ArcadeEvent.MusicSpeed(s.musicSpeed-.25f))
                com.kejing.spatialarcade.domain.usecase.MusicControl.SpeedUp->onEvent(ArcadeEvent.MusicSpeed(s.musicSpeed+.25f))
                com.kejing.spatialarcade.domain.usecase.MusicControl.VolumeDown->onEvent(ArcadeEvent.MusicVolume(s.musicVolume-.2f))
                com.kejing.spatialarcade.domain.usecase.MusicControl.VolumeUp->onEvent(ArcadeEvent.MusicVolume(s.musicVolume+.2f))
                com.kejing.spatialarcade.domain.usecase.MusicControl.Mute->onEvent(ArcadeEvent.ToggleSound)
            }
        }}
        is ArcadeEvent.MusicSpeed->if(kind==GameKind.C&&event.value.isFinite())_state.value=_state.value.copy(musicSpeed=event.value.coerceIn(.75f,1.25f))
        is ArcadeEvent.MusicVolume->if(kind==GameKind.C&&event.value.isFinite())_state.value=_state.value.copy(musicVolume=event.value.coerceIn(0f,1f),muted=false)
        ArcadeEvent.Demo->{_state.value=_state.value.copy(library=false,musicLibraryPending=false,musicBookOpen=false);engine.demo()}
        is ArcadeEvent.Layout->{if(kind!=GameKind.A)engine.setLayout(event.index);_state.value=_state.value.copy(library=false)}
        is ArcadeEvent.Track->{engine.selectTrack(event.index);_state.value=_state.value.copy(library=false,musicLibraryPending=false)}
        ArcadeEvent.SelectMusicBook->{if(kind==GameKind.C&&_state.value.ready){
            engine.pause();_state.value=_state.value.copy(library=false,musicBookOpen=true,musicLibraryPending=true)
        }}
        ArcadeEvent.MusicBookOpened->{if(kind==GameKind.C&&_state.value.musicLibraryPending&&_state.value.musicBookOpen)
            _state.value=_state.value.copy(library=true,musicLibraryPending=false)}
        ArcadeEvent.OpenLibrary->{if(kind==GameKind.C){engine.pause();_state.value=_state.value.copy(library=true,musicBookOpen=true,musicLibraryPending=false)}}
        ArcadeEvent.CloseLibrary->_state.value=_state.value.copy(library=false,musicLibraryPending=false)
    };sync()}
}
