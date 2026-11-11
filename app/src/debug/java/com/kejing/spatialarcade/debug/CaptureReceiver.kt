package com.kejing.spatialarcade.debug
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kejing.spatialarcade.ui.feature.arcade.*
import com.kejing.spatialarcade.domain.model.*
/** Debug builds only: drives the same rules as gestures, for reproducible emulator evidence. */
class CaptureReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){
    val scene=ArcadeSystem.active?:return
    val vm=scene.vm
    HudRenderProbe.cancel()
    MusicCaptureProbe.cancel()
    when(intent.getStringExtra("action")){
        "compact-baseline"->CompactHudProbe.baseline(scene)
        "compact-stress"->CompactHudProbe.start(scene)
        "hud-baseline"->HudRenderProbe.baseline(scene)
        "hud-stress"->HudRenderProbe.start(scene)
        "cosmos-mission"->{
            scene.frozenForCapture=true;val e=vm.engine;var found=false
            // Search real trajectories; no score or target-state injection.
            for(p in 1..20){
                e.reset();e.grab("plunger");e.drag(0f,0f,.045f*p/20);e.release()
                for(frame in 0..120*100){
                    val press=e.ball.z>.12f&&e.returnProgress<0&&e.launchProgress<0
                    e.setGravityPedal(press);e.step(1f/120)
                    if(e.orbit.objectives.nova>0f){found=true;break}
                    if(e.returnProgress>=0)break
                }
                if(found)break
            }
            scene.render();vm.sync()
            Log.i("ArcadeCosmos","mission reached=$found beacons=${e.orbit.objectives.count} nova=${e.orbit.objectives.nova} score=${e.snapshot.score} pulses=${e.gravityPulse.serial}")
        }
        "energy-hold"->{
            scene.frozenForCapture=true;val e=vm.engine;e.reset();e.demo()
            for(frame in 0..120*40){
                if(e.launchProgress<0&&e.returnProgress<0)e.setGravityPedal(true)
                e.step(1f/120)
                if(frame%120==0)Log.i("ArcadeEnergy","t=${frame/120} active=${e.gravityField} cells=${e.gravityPulse.cells} score=${e.snapshot.score} bought=${e.gravityPulse.serial}")
                if(e.gravityPulse.serial>=2||e.returnProgress>=0)break
            }
            scene.render();vm.sync()
            Log.i("ArcadeEnergy","COMPLETE active=${e.gravityField} cells=${e.gravityPulse.cells} score=${e.snapshot.score} earned=${e.orbit.points} bought=${e.gravityPulse.serial} fieldZ=${com.kejing.spatialarcade.domain.usecase.OrbitSimulation.fieldCenter.z}")
        }
        "cosmos-pulse"->{
            scene.frozenForCapture=true;val e=vm.engine;e.reset();e.demo()
            for(frame in 0..2400){e.step(1f/120);if(e.snapshot.score>=50&&e.launchProgress<0)break}
            val before=e.snapshot.score;scene.pressGravityPedal(true)
            repeat(intent.getIntExtra("frames",0)){e.step(1f/120)}
            scene.render();vm.sync()
            Log.i("ArcadeCosmos","pulse before=$before after=${e.snapshot.score} active=${e.gravityField} cells=${e.gravityPulse.cells} serial=${e.gravityPulse.serial}")
        }
        "demo"->{scene.frozenForCapture=false;vm.onEvent(ArcadeEvent.Demo)}
        "reset"->{scene.frozenForCapture=false;vm.onEvent(ArcadeEvent.Reset)}
        "selector"->vm.onEvent(ArcadeEvent.OpenSelector)
        "preset"->vm.onEvent(ArcadeEvent.Select(intent.getIntExtra("index",0)))
        "confirm"->vm.onEvent(ArcadeEvent.Confirm)
        "pause"->vm.onEvent(ArcadeEvent.Pause)
        "resume"->{scene.frozenForCapture=false;vm.onEvent(ArcadeEvent.Resume)}
        "language"->vm.onEvent(ArcadeEvent.ToggleLanguage)
        "music-button"->{scene.pressMusicButton(intent.getBooleanExtra("pressed",true))}
        "music-book"->{scene.pressMusicBook(intent.getBooleanExtra("pressed",true))}
        "music-control-3d"->{
            val control=com.kejing.spatialarcade.domain.usecase.MusicControl.entries.firstOrNull{it.name==intent.getStringExtra("control")}
            if(control!=null)scene.pressMusicControl(control,intent.getBooleanExtra("pressed",true))
        }
        "music-v020-state","music-v021-state","music-v022-state","music-v023-state","music-v024-state","music-v026-state","music-v027-state"->{
            val p=vm.engine.musicPhysics
            Log.i("ArcadeMusicV027","track=${vm.engine.snapshot.track} note=${p.noteIndex} physicalContacts=${p.physicalImpactCount} time=${p.time} phase=${vm.engine.snapshot.phase} launching=${p.launching} returning=${p.returning} finishing=${p.finishing} visibility=${p.ballVisibility} position=${p.physicalBall} scroll=${p.scroll} speed=${vm.state.value.musicSpeed} volume=${vm.state.value.musicVolume} muted=${vm.state.value.muted} ready=${vm.state.value.ready} english=${vm.state.value.english} frozen=${scene.frozenForCapture}")
        }
        "music-v020-capture"->MusicCaptureProbe.start(scene,intent.getIntExtra("note",8),intent.getIntExtra("frames",48))
        "music-v021-capture","music-v022-capture","music-v023-capture","music-v024-capture","music-v026-capture","music-v027-capture"->MusicCaptureProbe.start(scene,intent.getIntExtra("note",8),intent.getIntExtra("frames",24),intent.getIntExtra("index",0))
        "music-controls"->{vm.onEvent(ArcadeEvent.MusicSpeed(intent.getFloatExtra("speed",1f)));vm.onEvent(ArcadeEvent.MusicVolume(intent.getFloatExtra("volume",.35f)));Log.i("ArcadeMusicControls","speed=${vm.state.value.musicSpeed} volume=${vm.state.value.musicVolume}")}
        "music-close-book"->vm.onEvent(ArcadeEvent.CloseLibrary)
        "music-focus"->{scene.frozenForCapture=true;scene.settleMusicPresentation();scene.render();vm.sync()}
        "capture"->{scene.frozenForCapture=true;vm.engine.demo();repeat(intent.getIntExtra("frames",60)){vm.engine.step(1f/60f)};scene.render();vm.sync()}
        "shot"->{scene.frozenForCapture=true;vm.engine.setLayout(intent.getIntExtra("level",0));vm.engine.setContact(intent.getIntExtra("contact",0));vm.engine.grab("boot");vm.engine.drag(0f,0f,.07f*intent.getFloatExtra("power",.3f));vm.engine.release();repeat(intent.getIntExtra("frames",24)){vm.engine.step(1f/60)};scene.render();vm.sync()}
        "cosmos"->{scene.frozenForCapture=true;vm.engine.reset();vm.engine.grab("plunger");vm.engine.drag(0f,0f,.045f*intent.getFloatExtra("power",.6f));vm.engine.release();vm.engine.grab("handle");repeat(intent.getIntExtra("frames",540)){if(intent.getBooleanExtra("assist",false))vm.engine.drag(if(vm.engine.ball.x<0)-.1f else .1f,0f,0f);vm.engine.step(1f/60)};scene.render();vm.sync()}
        "music-launch"->{scene.frozenForCapture=true;vm.onEvent(ArcadeEvent.Track(intent.getIntExtra("index",0)));vm.engine.grab("plunger");vm.engine.drag(0f,0f,.028f);vm.engine.release();repeat(intent.getIntExtra("frames",40)){vm.engine.step(1f/60)};scene.render();vm.sync()}
        "music-impact"->{
            scene.frozenForCapture=true;vm.onEvent(ArcadeEvent.Track(intent.getIntExtra("index",0)));vm.engine.demo()
            val target=intent.getIntExtra("note",0)
            for(i in 0..480*240){vm.engine.step(1f/480);vm.engine.consumeMusicNotes();if(vm.engine.noteIndex>=target)break}
            val p=vm.engine.musicPhysics
            Log.i("ArcadeMusicImpact","CONTACT note=${p.noteIndex} time=${p.time} position=${p.impactPosition} incoming=${p.incomingNormalSpeed} outgoing=${p.outgoingNormalSpeed}")
            repeat(intent.getIntExtra("frames",0)){vm.engine.step(1f/480);vm.engine.consumeMusicNotes()}
            scene.render();vm.sync()
        }
        "advance"->{scene.frozenForCapture=true;repeat(intent.getIntExtra("frames",120)){vm.engine.step(1f/60)};scene.render();vm.sync()}
        "music-scroll","music-physics"->{
            scene.frozenForCapture=true;vm.onEvent(ArcadeEvent.Track(intent.getIntExtra("index",2)));vm.engine.demo()
            // Accelerated evidence must not play a backlog of notes all at once.
            var delivered=0
            repeat(intent.getIntExtra("frames",6000)){vm.engine.step(1f/120);delivered+=vm.engine.consumeMusicNotes().size}
            scene.render();vm.sync();Log.i("ArcadeTest","music-physics delivered=$delivered time=${vm.engine.musicPhysics.time} distance=${vm.engine.musicPhysics.travelled} speed=${vm.engine.musicPhysics.speed} spring=${vm.engine.musicPhysics.springCount} returning=${vm.engine.musicPhysics.returning}")
        }
        "music-spring","music-return"->{
            scene.frozenForCapture=true;vm.onEvent(ArcadeEvent.Track(intent.getIntExtra("index",0)));vm.engine.demo()
            for(i in 0..240*120){vm.engine.step(1f/120);vm.engine.consumeMusicNotes()
                if(if(intent.getStringExtra("action")=="music-spring")vm.engine.musicPhysics.flying else vm.engine.musicPhysics.returning)break}
            repeat(intent.getIntExtra("frames",6)){vm.engine.step(1f/120);vm.engine.consumeMusicNotes()};scene.render();vm.sync()
        }
        "gravity-pedal"->{
            scene.frozenForCapture=true;vm.engine.reset();vm.engine.demo();repeat(150){vm.engine.step(1f/120)}
            vm.engine.setGravityPedal(intent.getBooleanExtra("pressed",true))
            repeat(intent.getIntExtra("frames",0)){vm.engine.step(1f/120)};scene.render();vm.sync()
            Log.i("ArcadeTest","field=${vm.engine.gravityField} saves=${vm.engine.orbit.saves}")
        }
        "engine-hold","engine-fire"->{
            scene.frozenForCapture=true;vm.engine.reset();vm.engine.grab("plunger")
            vm.engine.drag(0f,0f,.045f*intent.getFloatExtra("power",.8f))
            if(intent.getStringExtra("action")=="engine-fire"){
                vm.engine.release();repeat(intent.getIntExtra("frames",18)){vm.engine.step(1f/120)}
            }
            scene.render();vm.sync()
            Log.i("ArcadeTest","engine piston=${vm.engine.launcher.displacement} firing=${vm.engine.launcher.firing} burn=${vm.engine.launcher.burn} speed=${vm.engine.launcher.launchSpeed}")
        }
        "sound"->{if(vm.state.value.muted!=intent.getBooleanExtra("muted",false))vm.onEvent(ArcadeEvent.ToggleSound)}
        "return-track"->{
            scene.frozenForCapture=true;val e=vm.engine;e.reset();e.grab("plunger");e.drag(0f,0f,.027f);e.release()
            for(i in 0..120*90){e.step(1f/120);if(e.returnProgress>=0f)break}
            repeat(intent.getIntExtra("frames",80)){e.step(1f/120)};scene.render();vm.sync()
        }
        "brick-cycle"->{
            scene.frozenForCapture=true;vm.engine.setLayout(0)
            repeat(intent.getIntExtra("stage",6).coerceIn(0,18)){
                val e=vm.engine;e.bricks.fill(0);e.grab("paddle")
                for(i in 0..2000){if(e.ball.y in .263f.. .298f){val col=kotlin.math.round((e.ball.x+.252f)/.056f).toInt().coerceIn(0,9);e.bricks.fill(0);e.bricks[70+col]=1};e.drag(e.ball.x-e.paddle,0f,0f);e.step(1f/120);if(e.levelTransition>0)break}
                repeat(125){e.step(1f/120)}
            };scene.render();vm.sync()
        }
        "hold"->{scene.frozenForCapture=true;vm.engine.reset();vm.engine.grab("boot");vm.engine.drag(0f,0f,.07f);scene.render();vm.sync()}
        "library"->vm.onEvent(ArcadeEvent.OpenLibrary)
        "track"->vm.onEvent(ArcadeEvent.Track(intent.getIntExtra("index",0)))
        "layout"->vm.onEvent(ArcadeEvent.Layout(intent.getIntExtra("index",0)))
    }
    Log.i("ArcadeTest","${vm.kind} ${intent.getStringExtra("action")} ball=${vm.engine.ball} return=${vm.engine.returnProgress} ${vm.engine.snapshot}")
}}
