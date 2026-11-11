package com.kejing.spatialarcade.debug

import android.util.Log
import com.kejing.spatialarcade.ui.feature.arcade.ArcadeScene
import com.kejing.spatialarcade.ui.feature.arcade.ArcadeSystem
import kotlinx.coroutines.*

/** Replays production state changes across rendered frames, then restores a repeatable HUD.
 * Capture baseline and completed output at the same camera pose to detect retained glyphs.
 */
object HudRenderProbe {
    private var job:Job?=null
    fun cancel(){job?.cancel();job=null}
    fun baseline(scene:ArcadeScene){
        scene.frozenForCapture=true
        val e=scene.vm.engine
        e.reset();e.demo();e.setGravityPedal(true)
        repeat(390){e.step(1f/120f)}
        scene.render();scene.vm.sync()
        Log.i("ArcadeHud","BASELINE score=${e.snapshot.score} previous=${e.snapshot.previousStatus} current=${e.snapshot.status}")
    }
    fun start(scene:ArcadeScene){
        cancel()
        job=CoroutineScope(Dispatchers.Main.immediate).launch {
            val e=scene.vm.engine
            scene.frozenForCapture=true
            var updates=0
            suspend fun present(){
                if(ArcadeSystem.active!==scene)throw CancellationException("Scene disposed")
                scene.render();scene.vm.sync();updates++;delay(80)
            }
            repeat(40){round->
                e.reset();present()
                e.grab("plunger");present()
                e.drag(0f,0f,.008f+(round%5)*.009f);present()
                e.release();repeat(18){e.step(1f/120f)};present()
                repeat(150){e.step(1f/120f)};present()
                e.setGravityPedal(true);repeat(300){e.step(1f/120f)};present()
                e.setGravityPedal(false)
                for(i in 0..120*90){e.step(1f/120f);if(e.returnProgress>=0f)break};present()
                repeat(300){e.step(1f/120f)};present()
            }
            if(ArcadeSystem.active===scene){baseline(scene);Log.i("ArcadeHud","COMPLETE rendered_updates=$updates")}
        }
    }
}
