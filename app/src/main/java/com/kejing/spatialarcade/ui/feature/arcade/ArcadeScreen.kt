package com.kejing.spatialarcade.ui.feature.arcade
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kejing.spatialarcade.domain.model.GameKind
import com.kejing.spatialarcade.ui.feature.arcade.components.ArcadeContent

@Composable fun ArcadeScreen(kind:GameKind){
    val context=LocalContext.current
    val vm=remember(kind){
        if(kind==GameKind.C){
            val preferences=context.applicationContext.getSharedPreferences("music_preferences",android.content.Context.MODE_PRIVATE)
            ArcadeViewModel(kind,initialEnglish=preferences.getBoolean("english",false),saveMusicLanguage={english->
                preferences.edit().putBoolean("english",english).apply()
            })
        }else ArcadeViewModel(kind)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val scene=remember(vm){ArcadeScene(vm,context)}
    val lifecycle=LocalLifecycleOwner.current
    DisposableEffect(scene,lifecycle){
        val observer=LifecycleEventObserver{_,event->
            if(event==Lifecycle.Event.ON_PAUSE){vm.engine.release(true);vm.engine.pause();vm.sync()}
            if(event==Lifecycle.Event.ON_RESUME&&kind in listOf(GameKind.A,GameKind.B)){vm.engine.resume();vm.sync()}
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose{lifecycle.lifecycle.removeObserver(observer);scene.dispose()}
    }
    ArcadeContent(kind,state,vm.contacts,vm.engine::shot,scene,vm::onEvent)
}
