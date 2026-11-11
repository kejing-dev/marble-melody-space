package com.kejing.spatialarcade.debug

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.kejing.spatialarcade.domain.model.*
import com.kejing.spatialarcade.ui.feature.arcade.*

/** Native Compose redraw regression. Only A/C/D; B's accepted probe is untouched. */
object CompactHudProbe {
    private val handler=Handler(Looper.getMainLooper())
    private var job:Runnable?=null
    fun baseline(scene:ArcadeScene){
        job?.let{handler.removeCallbacks(it)};job=null
        val vm=scene.vm;if(vm.kind==GameKind.B)return
        scene.frozenForCapture=true
        vm.setMusicHudProbe(null)
        if(vm.kind==GameKind.C)vm.onEvent(ArcadeEvent.Track(0)) else vm.onEvent(ArcadeEvent.Reset)
        vm.engine.setContact(2)
        if(vm.state.value.english)vm.onEvent(ArcadeEvent.ToggleLanguage)
        if(vm.state.value.muted)vm.onEvent(ArcadeEvent.ToggleSound)
        if(vm.kind==GameKind.C){vm.onEvent(ArcadeEvent.MusicSpeed(1f));vm.onEvent(ArcadeEvent.MusicVolume(.35f))}
        scene.render();vm.sync()
    }
    fun start(scene:ArcadeScene){
        baseline(scene);var count=0
        val r=object:Runnable {override fun run(){
            if(ArcadeSystem.active!==scene)return
            val vm=scene.vm
            when(vm.kind){
                GameKind.A->{vm.onEvent(ArcadeEvent.Select(count%9));vm.onEvent(ArcadeEvent.ToggleLanguage)}
                GameKind.C->{vm.setMusicHudProbe(com.kejing.spatialarcade.data.repository.ArcadeCatalog.tracks[count%20].title);vm.engine.setContact(count%9);vm.onEvent(ArcadeEvent.MusicSpeed(.75f+.25f*(count%3)));vm.onEvent(ArcadeEvent.MusicVolume((count%11)/10f));vm.engine.toggleMusicPlayback();if(vm.engine.snapshot.phase==Phase.PLAYING){vm.engine.step(.05f);vm.engine.consumeMusicNotes()}}
                GameKind.D->{if(count%40==0){vm.engine.setLayout((count/40)%6);vm.engine.grab("paddle")};repeat(5){vm.engine.drag(vm.engine.ball.x-vm.engine.paddle,0f,0f);vm.engine.step(1f/120)}}
                GameKind.B->return
            }
            scene.render();vm.sync();count++
            if(count<320)handler.postDelayed(this,50) else {baseline(scene);Log.i("CompactHudProbe","COMPLETE ${vm.kind} rendered_updates=$count")}
        }}
        job=r;handler.post(r)
    }
}
