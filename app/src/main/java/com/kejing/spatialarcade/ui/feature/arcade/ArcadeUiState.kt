package com.kejing.spatialarcade.ui.feature.arcade
import com.kejing.spatialarcade.domain.model.GameSnapshot
data class ArcadeUiState(val game:GameSnapshot=GameSnapshot(),val selector:Boolean=false,val pending:Int=2,val muted:Boolean=false,val ready:Boolean=false,val error:String?=null,val library:Boolean=false,val english:Boolean=false,val musicSpeed:Float=1f,val musicVolume:Float=.35f,val musicBookOpen:Boolean=true,val musicLibraryPending:Boolean=false,val musicTitleProbe:String?=null)
sealed interface ArcadeEvent {
    data object OpenSelector:ArcadeEvent;data object Confirm:ArcadeEvent;data object Cancel:ArcadeEvent
    data class Select(val id:Int):ArcadeEvent;data object Pause:ArcadeEvent;data object Resume:ArcadeEvent
    data object ToggleLanguage:ArcadeEvent;data object MusicPlayback:ArcadeEvent;data object Reset:ArcadeEvent;data object ToggleSound:ArcadeEvent;data object Demo:ArcadeEvent
    data class Layout(val index:Int):ArcadeEvent
    data object OpenLibrary:ArcadeEvent;data object CloseLibrary:ArcadeEvent
    data object SelectMusicBook:ArcadeEvent;data object MusicBookOpened:ArcadeEvent
    data class Track(val index:Int):ArcadeEvent
    data class MusicLanguage(val english:Boolean):ArcadeEvent
    data class MusicControlPress(val control:com.kejing.spatialarcade.domain.usecase.MusicControl):ArcadeEvent
    data class MusicSpeed(val value:Float):ArcadeEvent
    data class MusicVolume(val value:Float):ArcadeEvent
}
