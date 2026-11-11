package com.kejing.spatialarcade.debug

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.kejing.spatialarcade.ui.feature.arcade.*

/** Debug fixture waits for the real ECS book animation before freezing a contact. */
object MusicCaptureProbe {
    private val handler=Handler(Looper.getMainLooper())
    private var pending:Runnable?=null
    fun cancel(){pending?.let{handler.removeCallbacks(it)};pending=null}
    fun start(scene:ArcadeScene,note:Int,frames:Int,track:Int=scene.vm.engine.snapshot.track){
        cancel()
        scene.frozenForCapture=false
        val vm=scene.vm
        val selectedTrack=track.coerceIn(0,19)
        vm.onEvent(ArcadeEvent.Track(selectedTrack))
        vm.onEvent(ArcadeEvent.Reset)
        var polls=0
        var playbackRequested=false
        val job=object:Runnable{override fun run(){
            if(ArcadeSystem.active!==scene){cancel();return}
            if(!scene.isMusicTrackReady(selectedTrack)){
                if(++polls<600)handler.postDelayed(this,50)
                else {Log.e("ArcadeMusicV024","CAPTURE rejected: requested track=$track did not finish loading");cancel()}
                return
            }
            if(!playbackRequested){vm.onEvent(ArcadeEvent.MusicPlayback);playbackRequested=true;polls=0}
            if(!scene.isMusicBookClosed()){
                if(++polls<60)handler.postDelayed(this,50)
                else {Log.e("ArcadeMusicV024","CAPTURE rejected: real book animation did not finish");cancel()}
                return
            }
            scene.frozenForCapture=true
            // Rewind the test fixture only after the visible book really settled.
            vm.onEvent(ArcadeEvent.Reset);vm.onEvent(ArcadeEvent.MusicPlayback)
            val target=note.coerceIn(vm.engine.notes.indices)
            for(i in 0..480*240){vm.engine.step(1f/480);vm.engine.consumeMusicNotes();if(vm.engine.noteIndex>=target)break}
            scene.render()
            repeat(frames.coerceIn(0,120)){
                vm.engine.step(1f/480);vm.engine.consumeMusicNotes();scene.render()
            }
            vm.sync();pending=null
            Log.i("ArcadeMusicV024","CAPTURE track=$track routeReady=true bookClosed=true scoreRaised=true contact=${vm.engine.noteIndex} physicalContacts=${vm.engine.musicPhysics.physicalImpactCount} time=${vm.engine.musicPhysics.time} maxVisibleTrail=3 maxVisibleSpark=2")
        }}
        pending=job;handler.postDelayed(job,50)
    }
}
