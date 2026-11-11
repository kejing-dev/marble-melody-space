package com.kejing.spatialarcade.ui.feature.arcade

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.kejing.spatialarcade.domain.model.*
import com.kejing.spatialarcade.data.repository.ArcadeCatalog
import com.kejing.spatialarcade.domain.usecase.MusicPresentation
import com.kejing.spatialarcade.domain.usecase.MusicScoreDisplay
import com.kejing.spatialarcade.domain.usecase.MusicBookMotion
import com.kejing.spatialarcade.domain.usecase.MusicControl
import com.kejing.spatialarcade.domain.usecase.MusicControlDeck
import com.kejing.spatialarcade.domain.usecase.MusicCourse
import com.kejing.spatialarcade.domain.usecase.MusicSkins
import com.kejing.spatialarcade.domain.usecase.MusicSkinSwitch
import com.kejing.spatialarcade.domain.usecase.PressLatch
import com.kejing.spatialarcade.domain.usecase.OrbitObjectives
import com.kejing.spatialarcade.domain.usecase.OrbitSimulation
import kotlinx.coroutines.*
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.System as EcsSystem
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.math.EulerAngles
import kotlin.math.*

/** One ECS system per process; a scene unregisters itself on composition disposal. */
class ArcadeSystem:EcsSystem(){
    override fun update(context:SceneUpdateContext){active?.update(context.deltaTime)}
    companion object { var active:ArcadeScene?=null }
}
class ArcadeScene(val vm:ArcadeViewModel,context:Context){
    private var owner:Entity?=null
    private var fit:Entity?=null
    private var model:Entity?=null
    private var musicModel:Entity?=null
    private var musicModelTrack=-1
    private var musicLoadJob:Job?=null
    private var musicLoading=false
    private val musicSkinSwitch=MusicSkinSwitch()
    private var musicSkinMaterials:MusicSkinMaterials?=null
    private val musicPlates=mutableMapOf<String,Entity>()
    private val plateBases=mutableMapOf<String,Vector3>()
    private val ringing=mutableMapOf<Int,Float>()
    private data class MusicPart(val entity:Entity,val low:Float,val high:Float,val note:Int?=null,val railAfter:Int?=null,var visible:Boolean=true)
    private val musicParts=mutableListOf<MusicPart>()
    private var renderedImpactTime=-100f
    private val sceneScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var disposed=false
    private var sceneReady=false
    private var content:SpatialViewContent?=null
    private val nodes=mutableMapOf<String,Entity>()
    private val bases=mutableMapOf<String,Vector3>()
    private var fitted=false
    private var fitResetPending=false
    private var verifyFrames=0
    private var uiClock=0f
    private var lastNote=-1
    private var lastTrack=-1
    private var lastKick=0
    private var lastGoal=0
    private var lastHit=0
    private var lastOrbitImpact=0
    private var lastLauncher=0
    private val loadedSounds=java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    private val shardAge=FloatArray(48){1f}
    private val shardOrigin=Array(48){Point(0f,0f,0f)}
    private var shardBatch=0
    private var scale=1f
    private var pedalTravel=0f
    private var ambientTime=0f
    private val musicPresentation=MusicPresentation()
    private val musicButton=PressLatch()
    private var musicButtonTravel=0f
    private val musicControls=MusicControlDeck()
    private val musicControlTravel=FloatArray(MusicControl.entries.size)
    private data class LightSample(val point:Point,val time:Float)
    private val musicTrail=ArrayDeque<LightSample>()
    private var musicEffectTime=-1f
    private var musicEffectImpact=-100f
    private var musicCompleteLogged=false
    private var musicSparkOrigin=Point(0f,0f,0f)
    private var musicAudioPaused=false
    private var musicMuted=false
    private var musicVolume=-1f
    private val musicBook=PressLatch()
    private var musicBookFocused=false
    private var musicBookTime=0f
    private val musicBookMotion=MusicBookMotion()
    private var bookPoseLogged=-1
    private var bookVerifyFrames=0
    private val bookStitches=mutableListOf<Entity>()
    private var scorePage=-1
    private var scoreTrack=-1
    private var scoreContact=-2
    private val musicStreams=ArrayDeque<Int>()
    private var musicWallNanos=0L
    private var musicLogTime=0f
    private var musicWallSeconds=0.0
    private val sound=SoundPool.Builder().setMaxStreams(if(vm.kind==GameKind.C)12 else 4).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).build().also{pool->pool.setOnLoadCompleteListener{_,id,status->
        if(status==0)loadedSounds.add(id)
        if(com.kejing.spatialarcade.BuildConfig.DEBUG)Log.i("ArcadeAudio","sample=$id loadStatus=$status")
    }}
    private val sounds=(if(vm.kind==GameKind.C)ArcadeCatalog.tracks.flatMap{it.notes}.map{it.midi}.distinct() else emptyList()).associateWith{context.assets.openFd("note_$it.wav").use{f->sound.load(f,1)}}
    private val effects=(when(vm.kind){GameKind.A->listOf("kick","goal");GameKind.B->listOf("orbit_rail","orbit_body","orbit_flipper","orbit_comet","orbit_launch");GameKind.D->listOf("brick");GameKind.C->emptyList()}).associateWith{context.assets.openFd("$it.wav").use{f->sound.load(f,1)}}
    var frozenForCapture=false
    // Debug evidence can dismiss an initial Sheet before SpatialView starts loading.
    init { ArcadeSystem.active=this }
    suspend fun load(c:SpatialViewContent){
        content=c
        val root=Entity().apply{setName("placementOwner");components.set(TransformComponent())}
        val fittedRoot=Entity().apply{setName("fittedSceneRoot");components.set(TransformComponent())}
        root.addChild(fittedRoot);c.addEntity(root);owner=root;fit=fittedRoot
        Log.i("Arcade","loading asset=${vm.kind.asset}")
        val loaded=Entity.loadSuspend("asset://${vm.kind.asset}")
        if(disposed){loaded.destroy();return}
        Log.i("Arcade","asset ready=${vm.kind.asset}")
        fittedRoot.addChild(loaded);model=loaded
        fun visit(e:Entity){nodes[e.getName()]=e;e.components[TransformComponent::class.java]?.let{bases[e.getName()]=it.position};e.getChildren().forEach{visit(it)}}
        visit(loaded)
        if(vm.kind==GameKind.C){
            musicSkinMaterials=MusicSkinMaterials(nodes)
            applyMusicTheme(MusicSkins.forTrack(vm.engine.snapshot.track).family)
            requireNotNull(nodes["Score reading hinge"]).components[TransformComponent::class.java]!!
                .setEulerAngles(EulerAngles(MusicScoreDisplay.readingDegrees,0f,0f))
            Log.i("ArcadeScore","POSE fixed=true angle=${MusicScoreDisplay.readingDegrees} header=false")
        }
        if(vm.kind==GameKind.C)bookStitches.addAll(nodes.filterKeys{it.startsWith("Fine linen stitch")}.values)
        nodes.filterKeys{it.contains("design only",true)||it.contains("Trajectory",true)||it.startsWith("C Trail ")||it.startsWith("C Spark ")}.values.forEach{it.enabled=false}
        val targets=when(vm.kind){GameKind.A->listOf("Boot / heel grab pivot" to .035f);GameKind.B->listOf("Gravity pedal" to .048f);GameKind.C->listOf("Steel ball / pick and place" to .020f);GameKind.D->listOf("Paddle / captured horizontal drag" to .055f)}
        for((name,radius)in targets){val e=requireNotNull(nodes[name]){"Missing interaction model: $name"};e.components.set(InteractableComponent());e.components.set(CollisionComponent(collisionShape=listOf(ShapeResource.createSphere(radius)),physicsMaterial=PhysicsMaterialResource()))}
        if(vm.kind==GameKind.A){
            nodes["Boot / heel grab pivot"]!!.components.set(CollisionComponent(collisionShape=listOf(ShapeResource.createBox(Vector3(.064f,.080f,.064f))),physicsMaterial=PhysicsMaterialResource()))
            Log.i("ArcadeInput","boot grab box=(.064,.080,.064) metres; visual shoe stays small")
        }
        if(vm.kind==GameKind.C){
            val button=Entity().apply{
                setName("Music playback hit target")
                components.set(TransformComponent().setPosition(Vector3(-.28f,.094f,.310f)))
                components.set(InteractableComponent())
                components.set(CollisionComponent(collisionShape=listOf(ShapeResource.createBox(Vector3(.128f,.028f,.120f))),physicsMaterial=PhysicsMaterialResource()))
            }
            loaded.addChild(button);nodes[button.getName()]=button
            for(control in MusicControl.entries){
                val target=Entity().apply{
                    setName(control.hitName)
                    components.set(TransformComponent().setPosition(Vector3(control.center.x,control.center.y,control.center.z)))
                    components.set(InteractableComponent())
                    components.set(CollisionComponent(collisionShape=listOf(ShapeResource.createBox(Vector3(.070f,.035f,.070f))),physicsMaterial=PhysicsMaterialResource()))
                }
                loaded.addChild(target);nodes[target.getName()]=target
            }
            val book=Entity().apply{
                setName("Music book hit target")
                components.set(TransformComponent().setPosition(Vector3(.26293663f,.23180306f,.15065002f)))
                components.set(InteractableComponent())
                components.set(CollisionComponent(collisionShape=listOf(ShapeResource.createBox(Vector3(.24593916f,.18841979f,.21624935f))),physicsMaterial=PhysicsMaterialResource()))
            }
            loaded.addChild(book);nodes[book.getName()]=book
            Log.i("ArcadeInput","music cap fixed hit box=(.128,.028,.120); travel=.003; book fixed box=(.245939,.188420,.216249), measured across 41 hinge poses")
        }
        // Fixed hit volume surrounds the moving cap, avoiding poke release chatter as it sinks.
        nodes["Gravity pedal"]?.components?.set(CollisionComponent(collisionShape=listOf(ShapeResource.createBox(Vector3(.10f,.034f,.064f))),physicsMaterial=PhysicsMaterialResource()))
        if(vm.kind==GameKind.B){
            val grab=Entity().apply{
                setName("Engine launcher hit target")
                components.set(TransformComponent().setPosition(Vector3(.133f,.074f,.229f)))
                components.set(InteractableComponent())
                components.set(CollisionComponent(collisionShape=listOf(ShapeResource.createBox(Vector3(.052f,.052f,.090f))),physicsMaterial=PhysicsMaterialResource()))
            }
            loaded.addChild(grab);nodes[grab.getName()]=grab
            Log.i("ArcadeInput","launcher hit box center=(.133,.074,.229) size=(.052,.052,.090), pointer capture enabled")
        }
        fittedRoot.components[TransformComponent::class.java]!!.setEulerAngles(EulerAngles(if(vm.kind in listOf(GameKind.A,GameKind.B))48f else 0f,0f,0f))
        if(vm.kind==GameKind.C)loadMusicTrack(vm.engine.snapshot.track)
        if(disposed)return
        sceneReady=true
        ArcadeSystem.active=this;EcsSystem.register(ArcadeSystem::class.java)
        render();vm.ready();Log.i("Arcade","loaded ${vm.kind} nodes=${nodes.size}")
    }
    private fun fitOnce(){
        val c=content?:return;val f=fit?:return;val o=owner?:return
        val corner=c.convertPosition(Vector3.ZERO,ViewCoordinateSpace.Local,c.localSpatialCoordinateSpace)
        // The SDK updates visual bounds on the next frame after Transform writes.
        if(!fitResetPending){
            if(vm.kind==GameKind.C){
                enable("Steel ball / pick and place",false)
                nodes.filterKeys{it.startsWith("C Trail ")||it.startsWith("C Spark ")}.values.forEach{it.enabled=false}
            }
            f.components[TransformComponent::class.java]!!.setScaleVector(Vector3(1f,1f,1f)).setPosition(Vector3.ZERO)
            fitResetPending=true;return
        }
        // A scrolling score can be tens of metres long; fit its fixed display frame only.
        val bounds=if(vm.kind==GameKind.C)model!!.getVisualBounds(o,true,true) else f.getVisualBounds(o,true,vm.kind!=GameKind.B)
        val h=bounds.halfExtent
        if(h.x<=0f||abs(corner.x)<.01f||abs(corner.y)<.01f||abs(corner.z)<.01f)return
        // Leave projection room around the protruding board so it cannot cover the control panels.
        scale=minOf(abs(corner.x)*.88f/h.x,abs(corner.y)*.84f/h.y,abs(corner.z)*.84f/h.z).coerceAtMost(2f)*(if(vm.kind==GameKind.C).97f else .88f)
        val rightInset=if(vm.kind==GameKind.A)(abs(corner.x)*.88f-h.x*scale).coerceAtLeast(0f)*.65f else 0f
        val y=if(vm.kind==GameKind.C)-abs(corner.y)+.012f-(bounds.center.y-h.y)*scale else -bounds.center.y*scale+.03f
        f.components[TransformComponent::class.java]!!.setScaleVector(Vector3(scale,scale,scale)).setPosition(Vector3(-bounds.center.x*scale+rightInset,y,-bounds.center.z*scale))
        fitted=true;fitResetPending=false
        verifyFrames=3
        val final=f.getVisualBounds(o,true,true)
        Log.i("ArcadeBounds","${vm.kind} scale=$scale halfRegion=$corner finalCenter=${final.center} finalHalf=${final.halfExtent}")
    }
    fun update(dt:Float){
        // A surviving registered system may tick a newly recreated scene while
        // loadSuspend is still waiting. No fit/book/score node exists yet.
        if(disposed||!sceneReady){musicWallNanos=0L;return}
        if(!fitted)fitOnce()
        if(verifyFrames>0 && --verifyFrames==0){
            val b=fit!!.getVisualBounds(owner,true,true);Log.i("ArcadeBounds","${vm.kind} settled center=${b.center} half=${b.halfExtent}")
            if(vm.kind==GameKind.C){
                val region=content!!.convertPosition(Vector3.ZERO,ViewCoordinateSpace.Local,content!!.localSpatialCoordinateSpace)
                val body=model!!.getVisualBounds(owner,true,true)
                val epsilon=.001f
                val contained=abs(body.center.x)+body.halfExtent.x<=abs(region.x)+epsilon&&abs(body.center.y)+body.halfExtent.y<=abs(region.y)+epsilon&&abs(body.center.z)+body.halfExtent.z<=abs(region.z)+epsilon
                val bottomGap=body.center.y-body.halfExtent.y+abs(region.y)
                Log.i("SpatialPlacementEvidence","revision=C-v027 owner=placementOwner scope=fixed-instrument region=SpatialView half=$region center=${body.center} half=${body.halfExtent} scale=$scale epsilon=$epsilon contained=$contained floorGap=$bottomGap")
                nodes["Music playback cap"]?.let{cap->
                    val b=cap.getVisualBounds(model,true,true)
                    val restCenterY=b.center.y+.003f*musicButtonTravel
                    val capContained=abs(b.center.x+.28f)+b.halfExtent.x<=.064f+epsilon&&abs(b.center.y-.094f)+b.halfExtent.y<=.014f+epsilon&&abs(b.center.z-.310f)+b.halfExtent.z<=.060f+epsilon
                    val sweepContained=abs(b.center.x+.28f)+b.halfExtent.x<=.064f+epsilon&&restCenterY-b.halfExtent.y-.003f>=.080f-epsilon&&restCenterY+b.halfExtent.y<=.108f+epsilon&&abs(b.center.z-.310f)+b.halfExtent.z<=.060f+epsilon
                    Log.i("ArcadeMusicControls","BOUNDS Playback owner=C_Root center=${b.center} half=${b.halfExtent} hitCenter=(-.280,.094,.310) hitSize=(.128,.028,.120) travel=.003 hitContained=$capContained fullPressSweepContained=$sweepContained")
                }
                for(control in MusicControl.entries){
                    val cap=nodes[control.capName]?:continue
                    val b=cap.getVisualBounds(model,true,true);val c=control.center
                    val hitContained=abs(b.center.x-c.x)+b.halfExtent.x<=.035f+epsilon&&abs(b.center.y-c.y)+b.halfExtent.y<=.0175f+epsilon&&abs(b.center.z-c.z)+b.halfExtent.z<=.035f+epsilon
                    Log.i("ArcadeMusicControls","BOUNDS ${control.name} owner=C_Root center=${b.center} half=${b.halfExtent} hitCenter=$c hitSize=(.070,.035,.070) hitContained=$hitContained")
                }
            }
        }
        val now=System.nanoTime()
        val musicDt=if(musicWallNanos==0L)0f else ((now-musicWallNanos)/1e9).toFloat()
        musicWallNanos=now
        if(!frozenForCapture&&(vm.kind!=GameKind.C||musicModelTrack==vm.engine.snapshot.track)){
            vm.engine.step(if(vm.kind==GameKind.C)musicDt*vm.state.value.musicSpeed else dt)
            if(vm.kind==GameKind.C&&vm.engine.snapshot.phase==Phase.PLAYING){musicWallSeconds+=musicDt;musicLogTime+=musicDt
                if(com.kejing.spatialarcade.BuildConfig.DEBUG&&musicLogTime>2f){musicLogTime=0f;Log.i("ArcadeMusicClock","wall=$musicWallSeconds physics=${vm.engine.musicPhysics.time} frame=$musicDt sdkDt=$dt note=${vm.engine.noteIndex}")}}
        }
        if(vm.kind==GameKind.C){
            if(!vm.engine.musicPhysics.completed)musicCompleteLogged=false
            if(vm.engine.musicPhysics.completed&&!musicCompleteLogged){
                musicCompleteLogged=true
                Log.i("ArcadeMusicV024","COMPLETE track=${vm.engine.snapshot.track} notes=${vm.engine.noteIndex+1}/${vm.engine.notes.size} physicalContacts=${vm.engine.musicPhysics.physicalImpactCount} time=${vm.engine.musicPhysics.time} lastImpact=${vm.engine.musicPhysics.impactTime} visibility=${vm.engine.musicPhysics.ballVisibility} returning=${vm.engine.musicPhysics.returning}")
            }
            val paused=vm.engine.snapshot.phase==Phase.PAUSED
            if(paused!=musicAudioPaused){if(paused)sound.autoPause() else sound.autoResume();musicAudioPaused=paused}
            val state=vm.state.value
            if(musicMuted!=state.muted||musicVolume!=state.musicVolume){musicMuted=state.muted;musicVolume=state.musicVolume;val volume=if(musicMuted)0f else musicVolume*.63f;musicStreams.forEach{sound.setVolume(it,volume,volume)}}
            if(!frozenForCapture){
                musicBookMotion.update(dt.coerceIn(0f,.1f),state.musicBookOpen)
                if(musicBookMotion.fullyOpen&&state.musicLibraryPending){
                    vm.onEvent(ArcadeEvent.MusicBookOpened)
                    Log.i("ArcadeBook","OPEN_COMPLETE menu=true")
                }
                if(musicBookMotion.settled){
                    val pose=if(musicBookMotion.fullyOpen)1 else 0
                    if(bookPoseLogged!=pose){bookPoseLogged=pose;bookVerifyFrames=3;Log.i("ArcadeBook","SETTLED open=$pose menu=${vm.state.value.library}")}
                }else bookPoseLogged=-1
                if(!musicBookFocused&&!state.library&&!state.musicLibraryPending)musicBookTime+=dt.coerceIn(0f,.1f)
            }
            if(bookVerifyFrames>0&&--bookVerifyFrames==0){
                val b=nodes["Floating scorebook / leather and gilt"]!!.getVisualBounds(model,true,true)
                val contained=abs(b.center.x-.26293663f)+b.halfExtent.x<=.122970f&&abs(b.center.y-.23180306f)+b.halfExtent.y<=.094210f&&abs(b.center.z-.15065002f)+b.halfExtent.z<=.108125f
                Log.i("ArcadeBook","BOUNDS open=$bookPoseLogged owner=C_Root center=${b.center} half=${b.halfExtent} hitContained=$contained")
            }
            if(!frozenForCapture&&!paused)musicPresentation.update(musicDt,vm.engine.musicPhysics)
            MusicControl.entries.forEach{control->
                val i=control.ordinal;val target=if(musicControls.down(control))1f else 0f
                musicControlTravel[i]+=(target-musicControlTravel[i])*(1f-exp(-dt.coerceIn(0f,.1f)*32f))
            }
            musicButtonTravel+=((if(musicButton.down)1f else 0f)-musicButtonTravel)*(1f-exp(-dt.coerceIn(0f,.1f)*32f))
        }
        // Submit impacts before Entity work; rendering should not add another frame
        // of latency. Timing still comes exclusively from actual plane contacts.
        for(i in vm.engine.consumeMusicNotes()){
            if(!vm.state.value.muted)sounds[vm.engine.notes[i].midi]?.let{if(it in loadedSounds){val volume=vm.state.value.musicVolume*.63f;sound.play(it,volume,volume,1,0,1f).also{stream->musicStreams.addLast(stream);if(musicStreams.size>24)musicStreams.removeFirst()}}}
            if(com.kejing.spatialarcade.BuildConfig.DEBUG)Log.i("ArcadeMusicNote","track=${vm.engine.snapshot.track} index=$i midi=${vm.engine.notes[i].midi} sim=${vm.engine.musicPhysics.time} score=${vm.engine.notes[i].time} wallNs=${System.nanoTime()}")
        }
        if(vm.kind==GameKind.B){
            if(!frozenForCapture)ambientTime+=dt.coerceIn(0f,.1f)
            pedalTravel+=((if(vm.engine.gravityPulse.down)1f else 0f)-pedalTravel)*(1f-exp(-dt.coerceIn(0f,.1f)*24f))
        }
        render()
        animateShards(if(frozenForCapture)0f else dt)
        val current=vm.engine.snapshot
        if(lastTrack!=current.track || vm.engine.noteIndex<0){lastTrack=current.track;lastNote=-1}
        if(current.kickSerial!=lastKick){lastKick=current.kickSerial;playEffect("kick")}
        if(current.goalSerial!=lastGoal){lastGoal=current.goalSerial;playEffect("goal")}
        if(vm.kind==GameKind.B&&vm.engine.launcher.serial!=lastLauncher){lastLauncher=vm.engine.launcher.serial;playEffect("orbit_launch",.17f+.12f*current.power,.7f,.90f+.15f*current.power)
            Log.i("ArcadeInput","engine ignition speed=${vm.engine.launcher.launchSpeed} power=${current.power}")}
        if(vm.kind==GameKind.B)vm.engine.orbit.impact?.let{hit->if(hit.serial!=lastOrbitImpact){
            lastOrbitImpact=hit.serial
            playEffect("orbit_${hit.kind.name.lowercase()}",.16f+.12f*hit.strength,(hit.x/.15f).coerceIn(-1f,1f),.94f+.12f*hit.strength)
        }}
        uiClock+=dt;if(uiClock>.1f){vm.sync();uiClock=0f}
    }
    private fun move(name:String,p:Point){nodes[name]?.components?.get(TransformComponent::class.java)?.setPosition(Vector3(p.x,p.y,p.z))}
    fun render(){
        val e=vm.engine
        val ballName=when(vm.kind){GameKind.A->"Ball / contact selectable";GameKind.B->"Steel ball";GameKind.C->"Steel ball / pick and place";GameKind.D->"Game ball"}
        if(vm.kind==GameKind.C&&!e.musicPhysics.started)musicPresentation.reset(e.snapshot.track)
        move(ballName,if(vm.kind==GameKind.C)musicPresentation.map(e.ball) else e.ball)
        when(vm.kind){
            GameKind.A->{
                move("Boot / heel grab pivot",Point(e.kickLevel.startX,.052f,e.kickLevel.startZ+.04f+e.pull))
                for(i in 0..3){nodes["Defender_$i"]?.enabled=i<e.kickLevel.defenders;move("Defender_$i",Point(e.kickLevel.wallX+(i-(e.kickLevel.defenders-1)/2f)*.028f,0f,e.kickLevel.wallZ))}
                nodes["Goalkeeper"]?.enabled=e.kickLevel.keeper;move("Goalkeeper",Point(e.keeperX,0f,-.221f))
            }
            GameKind.B->{
                val time=if(e.snapshot.phase==Phase.READY&&!frozenForCapture)ambientTime else e.orbit.age
                val objectives=e.orbit.objectives
                for(i in 0..2)nodes["Universe_$i"]?.enabled=i==e.snapshot.universe
                for(u in 0..2)for(i in 0..2){
                    nodes["Cosmic body ${u}_$i"]?.components?.get(TransformComponent::class.java)?.setEulerAngles(EulerAngles(0f,time*(12f+i*7f),0f))
                    nodes["Planet ring ${u}_$i"]?.components?.get(TransformComponent::class.java)?.setEulerAngles(EulerAngles(18f+8f*sin(time*.6f+i),time*18f,9f*cos(time*.5f)))
                    val halo=1f+.04f*sin(time*2.2f+i)
                    nodes["Gravity halo ${u}_$i"]?.components?.get(TransformComponent::class.java)?.setScaleVector(Vector3(halo,1f,halo))
                }
                for(i in 0..1){val moon=e.orbit.moon(i);move("Moon_$i",Point(moon.x,.081f,moon.z))
                    nodes["Moon_$i"]?.components?.get(TransformComponent::class.java)?.setEulerAngles(EulerAngles(0f,time*(35f+i*14f),0f))}
                for(i in 0..2){
                    val lit=objectives.mask and (1 shl i)!=0
                    nodes["Beacon link_$i"]?.enabled=lit
                    val pulse=if(lit)1.1f+.08f*sin(time*6) else .82f+.06f*sin(time*2+i)
                    nodes["Beacon glow_$i"]?.components?.get(TransformComponent::class.java)?.setScaleVector(Vector3(pulse,1f,pulse))
                    val p=OrbitObjectives.beacons[i]
                    move("Beacon core_$i",p.copy(y=.075f+.003f*sin(time*3+i)))
                    nodes["Beacon core_$i"]?.components?.get(TransformComponent::class.java)?.setEulerAngles(EulerAngles(0f,time*60f,0f))
                }
                nodes["Nova wave"]?.enabled=objectives.nova>0f
                val novaSize=1f+((8f-objectives.nova)*1.5f%1f)*2.6f
                nodes["Nova wave"]?.components?.get(TransformComponent::class.java)?.setScaleVector(Vector3(novaSize,1f,novaSize))
                for(j in 0 until 36){val a=j*2.39996f+time*(if(objectives.nova>0f).9f else .15f);val r=.050f+(j%9)*.006f
                    move("Cosmic dust_$j",Point(cos(a)*r,.069f+.004f*sin(time+j),-.05f+sin(a)*r*.8f))}
                for(j in 0 until 10)nodes["Charge light_$j"]?.enabled=e.gravityPulse.cells>j+.001f
                nodes["Comet"]?.enabled=e.orbit.cometVisible&&e.snapshot.phase!=Phase.READY
                move("Comet",e.orbit.comet)
                nodes["Nebula shield"]?.enabled=e.orbit.shieldOn&&e.returnProgress<0f&&e.snapshot.phase==Phase.PLAYING
                for(i in 0..2)nodes["Portal pulse_$i"]?.components?.get(TransformComponent::class.java)?.setScaleVector(Vector3(1f+.09f*sin(e.orbit.age*3f+i),1f,1f+.09f*sin(e.orbit.age*3f+i)))
                bases["Gravity pedal cap"]?.let{move("Gravity pedal cap",Point(it.x,it.y-.007f*pedalTravel,it.z))}
                for(j in 0 until 64){val n="Void_$j";nodes[n]?.enabled=e.gravityField
                    if(e.gravityField){val a=j*2.39996f+e.orbit.age*(1.6f+j%3);val r=.016f+(j%12)*.0075f
                        move(n,Point(cos(a)*r,.078f+.009f*sin(a*2),OrbitSimulation.fieldCenter.z+sin(a)*r*.50f))}}
                val travel=e.launcher.displacement
                bases["Plunger head"]?.let{move("Plunger head",Point(it.x,it.y,it.z+travel))}
                nodes["Engine spring"]?.components?.get(TransformComponent::class.java)?.setScaleVector(Vector3(1f,1f,(.066f-travel)/.066f))
                for(j in 0 until 24){val name="Engine exhaust_$j";nodes[name]?.enabled=e.launcher.burn>0f
                    if(e.launcher.burn>0f){val u=((.42f-e.launcher.burn)*4f+j/24f)%1f;val spread=.002f+.005f*u
                        move(name,Point(.133f+cos(j*2.4f)*spread,.074f+sin(j*2.4f)*spread,.297f+.026f*u))
                        val size=(1f-u)*(.7f+e.snapshot.power*.6f);nodes[name]?.components?.get(TransformComponent::class.java)?.setScaleVector(Vector3(size,size,size*1.8f))}}
            }
            GameKind.C->{
                bases["Floating scorebook / leather and gilt"]?.let{move("Floating scorebook / leather and gilt",Point(it.x,it.y+.004f*sin(musicBookTime*2f*PI.toFloat()/3f),it.z))}
                val closed=musicBookMotion.closedAmount
                // Exported book-local Blender +Y maps to SDK -Z. Scale only the
                // arched page thickness as the stack settles; page width is unchanged.
                nodes["Book left hinge"]?.components?.get(TransformComponent::class.java)?.setEulerAngles(EulerAngles(0f,0f,-180f*closed))?.setScaleVector(Vector3(1f,1f-.65f*closed,1f))
                nodes["Book right hinge"]?.components?.get(TransformComponent::class.java)?.setScaleVector(Vector3(1f,1f-.65f*closed,1f))
                bases["Supple leather spine"]?.let{move("Supple leather spine",Point(it.x,it.y+.017f*closed,it.z))}
                nodes["Supple leather spine"]?.components?.get(TransformComponent::class.java)?.setScaleVector(Vector3(1f,1f+.5f*closed,1f))
                bookStitches.forEach{it.enabled=closed<.6f}
                renderMusicScore()
                if(musicModelTrack!=e.snapshot.track&&!musicLoading&&musicLoadJob?.isActive!=true){
                    musicLoadJob=sceneScope.launch{vm.loading();try{loadMusicTrack(e.snapshot.track);vm.ready()}catch(t:CancellationException){throw t}catch(t:Exception){vm.error(t.message?:"轨道加载失败")}}
                }
                bases["Music playback cap"]?.let{move("Music playback cap",Point(it.x,it.y-.003f*musicButtonTravel,it.z))}
                for(control in MusicControl.entries){
                    bases[control.capName]?.let{move(control.capName,Point(it.x,it.y-.003f*musicControlTravel[control.ordinal],it.z))}
                }
                for(i in 0..2)enable("C Speed indicator $i",i==((vm.state.value.musicSpeed-.75f)/.25f).roundToInt())
                for(i in 0..4)enable("C Volume indicator $i",!vm.state.value.muted&&vm.state.value.musicVolume>i*.2f+.001f)
                enable("C Mute active",vm.state.value.muted)
                val playing=e.snapshot.phase==Phase.PLAYING
                nodes["Music play icon"]?.enabled=!playing
                for(i in 0..1)nodes["Music pause icon $i"]?.enabled=playing
                val zoom=musicPresentation.scale;val offset=musicPresentation.routeOffset(e.musicScroll)
                musicModel?.enabled=musicModelTrack==e.snapshot.track
                musicModel?.components?.get(TransformComponent::class.java)?.setScaleVector(Vector3(zoom,zoom,zoom))?.setPosition(Vector3(offset.x,offset.y,offset.z))
                val marbleSize=zoom*e.musicPhysics.ballVisibility
                enable(ballName,musicModelTrack==e.snapshot.track&&fitted&&verifyFrames==0&&e.musicPhysics.ballVisibility>.001f)
                nodes[ballName]?.components?.get(TransformComponent::class.java)?.setScaleVector(Vector3(marbleSize,marbleSize,marbleSize))
                renderMusicEffects()
                for(part in musicParts){
                    val inPhrase=part.note?.let{musicPresentation.visibleNote(it)}
                        ?:part.railAfter?.let{musicPresentation.visibleRail(it)}?:true
                    val visible=inPhrase&&musicPresentation.visible(part.low,part.high,e.musicScroll)
                    if(part.visible!=visible){part.entity.enabled=visible;part.visible=visible}
                }
                nodes[ballName]?.components?.get(TransformComponent::class.java)?.setEulerAngles(EulerAngles(e.musicPhysics.roll*180f/PI.toFloat(),0f,e.musicPhysics.roll*31f))
                if(e.musicPhysics.impactTime!=renderedImpactTime){
                    renderedImpactTime=e.musicPhysics.impactTime
                    if(e.noteIndex>=0)ringing[e.noteIndex]=renderedImpactTime
                }
                val iterator=ringing.iterator()
                while(iterator.hasNext()){
                    val (i,at)=iterator.next();val age=e.musicPhysics.time-at
                    val name="Impact plate ${i.toString().padStart(3,'0')}";val glow="Impact glow ${i.toString().padStart(3,'0')}"
                    val normal=e.musicPhysics.course.impacts[i].normal
                    // Compression stays below the collision face, never pushes a visual plate into the departing ball.
                    val displacement=if(age in 0f.. .28f)-.00012f*exp(-age*24f)*abs(sin(age*150f)) else 0f
                    plateBases[name]?.let{p->musicPlates[name]?.components?.get(TransformComponent::class.java)?.setPosition(Vector3(p.x+normal.x*displacement,p.y+normal.y*displacement,p.z+normal.z*displacement))}
                    musicPlates[glow]?.enabled=i==e.noteIndex&&age>=0f&&age<musicSkinSwitch.active.contactSeconds
                    if(age<0f||age>.28f)iterator.remove()
                }
            }
            GameKind.D->{
                move("Paddle / captured horizontal drag",Point(e.paddle,.078f,.027f))
                nodes["Paddle / captured horizontal drag"]?.components?.get(TransformComponent::class.java)?.setScaleVector(Vector3(e.paddleScale,1f,1f))
                for(i in e.bricks.indices){val suffix="R${i/10+1}_C${i%10+1}";nodes["Brick_$suffix"]?.enabled=e.bricks[i]>0;nodes["Steel_$suffix"]?.enabled=e.bricks[i]==-1;nodes["Armor_$suffix"]?.enabled=e.bricks[i]==2}
                e.hit?.let{h->if(h.serial!=lastHit){lastHit=h.serial;playEffect("brick");val start=(shardBatch++%3)*16;for(j in start until start+16){shardAge[j]=if(frozenForCapture).09f else 0f;shardOrigin[j]=h.position}}}
            }
        }
    }
    /** Steel has no glowing silhouette or ghost spheres; contact is shown on the pad. */
    private fun renderMusicEffects(){
        nodes.filterKeys{it=="C Ball aura"||it.startsWith("C Trail ")||it.startsWith("C Spark ")}
            .values.forEach{if(it.enabled)it.enabled=false}
    }
    private fun enable(name:String,value:Boolean){nodes[name]?.let{if(it.enabled!=value)it.enabled=value}}
    private fun applyMusicTheme(family:String){
        for((name,node) in nodes){
            val prefix=when{ name.startsWith("Theme ")->"Theme ";name.startsWith("Book theme ")->"Book theme ";else->continue }
            node.enabled=name==prefix+family
        }
        Log.i("ArcadeMusicTheme","family=$family geometry=themed-side-vignette plate=inlay steel=true")
    }
    private fun renderMusicScore(){
        val e=vm.engine;val start=MusicScoreDisplay.pageStart(e.noteIndex)
        if(scorePage!=start||scoreTrack!=e.snapshot.track||scoreContact!=e.noteIndex){
            scorePage=start;scoreTrack=e.snapshot.track;scoreContact=e.noteIndex
            for(i in 0..19)enable("Score title ${i.toString().padStart(2,'0')}",i==scoreTrack)
            for(slot in 0 until MusicScoreDisplay.slots){
                val index=start+slot;val note=e.notes.getOrNull(index);val step=note?.let{MusicScoreDisplay.staffStep(MusicScoreDisplay.displayMidi(it.midi))}?:0
                enable("Score octave $slot",note!=null&&MusicScoreDisplay.octaveRaised(note.midi))
                val y=MusicScoreDisplay.rowOrigin(slot)+step*MusicScoreDisplay.pitchStep;val x=MusicScoreDisplay.x(slot)
                val selected=MusicScoreDisplay.tone(index,e.noteIndex).nodeState
                for(state in listOf("normal","active","past")){
                    val name="Score note $slot $state";enable(name,note!=null&&state==selected)
                    move(name,Point(x,y,MusicScoreDisplay.noteDepth))
                    nodes[name]?.components?.get(TransformComponent::class.java)?.setEulerAngles(EulerAngles(0f,0f,if(step>=6)180f else 0f))
                    enable("Score flag $slot $state",note!=null&&note.duration<.32f)
                }
                for(i in 0..1){
                    val sharp=note!=null&&MusicScoreDisplay.sharp(note.midi)
                    enable("Score sharp vertical $slot $i",sharp);move("Score sharp vertical $slot $i",Point(x-.012f+i*.004f,y,MusicScoreDisplay.sharpDepth))
                    enable("Score sharp cross $slot $i",sharp);move("Score sharp cross $slot $i",Point(x-.010f,y-.004f+i*.007f,MusicScoreDisplay.sharpDepth))
                }
                for(level in -5..9)if(level !in 0..4)enable("Score ledger $slot $level",note!=null&&((level<0&&step<=level*2)||(level>4&&step>=level*2)))
            }
            if(com.kejing.spatialarcade.BuildConfig.DEBUG)Log.i("ArcadeScore","track=$scoreTrack page=${start/MusicScoreDisplay.slots} row=${MusicScoreDisplay.row(e.noteIndex)} contact=$scoreContact goldOnPage=${(e.noteIndex-start+1).coerceIn(0,MusicScoreDisplay.slots)} activeMidi=${e.notes.getOrNull(e.noteIndex)?.midi}")
        }
        val sweep=MusicScoreDisplay.sweep(e.notes,e.noteIndex,e.musicPhysics.time-e.musicPhysics.impactTime)
        move("Score sweep",Point(MusicScoreDisplay.firstX+sweep*MusicScoreDisplay.columnGap,MusicScoreDisplay.rowOrigin(e.noteIndex)+.020f,MusicScoreDisplay.cursorDepth))
        enable("Score sweep",e.noteIndex>=0)
    }
    private suspend fun loadMusicTrack(index:Int){
        musicLoading=true
        val ticket=musicSkinSwitch.begin(index)
        try{
        val asset=MusicSkins.routeAsset(index)
        val loaded=Entity.loadSuspend(asset)
        if(disposed||!musicSkinSwitch.accepts(ticket,vm.engine.snapshot.track)){loaded.destroy();return}
        val skin=MusicSkins.forTrack(index)
        val paletteReady=musicSkinMaterials?.apply(skin)==true
        applyMusicTheme(skin.family)
        check(musicSkinSwitch.commit(ticket,vm.engine.snapshot.track,paletteReady))
        musicPresentation.reset(index);musicStreams.forEach{sound.stop(it)};musicStreams.clear();musicModel?.destroy();fit!!.addChild(loaded);musicModel=loaded;musicModelTrack=index
        musicPlates.clear();plateBases.clear();musicParts.clear();ringing.clear();renderedImpactTime=-100f;musicTrail.clear();musicEffectTime=-1f;musicEffectImpact=-100f
        val course=vm.engine.musicPhysics.course
        fun visit(e:Entity){val name=e.getName()
            if(name.startsWith("Cascade note ")){val i=name.substringAfterLast(' ').toInt();val y=course.impacts[i].contact.y;musicParts+=MusicPart(e,y-.025f,y+.02f,note=i)}
            if(name.startsWith("Cascade rail ")){
                val a=name.split(' ');val rail=course.rails[a[2].toInt()]
                // No visible entrance chute or collection run in the Moonlight instrument.
                if(rail.after<0||rail.after==course.impacts.lastIndex){e.enabled=false;return}
                val path=rail.path.subList(a[3].toInt(),a[4].toInt()+1)
                musicParts+=MusicPart(e,path.minOf{it.y}.toFloat()-.015f,path.maxOf{it.y}.toFloat()+.015f,railAfter=rail.after)
            }
            if(name.startsWith("Impact ")){musicPlates[name]=e;e.components[TransformComponent::class.java]?.let{plateBases[name]=it.position};if(name.startsWith("Impact glow"))e.enabled=false};e.getChildren().forEach{visit(it)}}
        visit(loaded)
        Log.i("ArcadeMusicSkin","SKIN_APPLIED track=$index skinId=${musicSkinSwitch.active.id} requestedSkin=${skin.id} family=${skin.family} asset=$asset bookRgb=${if(paletteReady)skin.bookCoverRgb.toString(16) else "asset-default"} glowRgb=${if(paletteReady)skin.glowRgb.toString(16) else "asset-default"} paletteReady=$paletteReady maxSparks=${musicSkinSwitch.active.sparkCount} contactMs=${(musicSkinSwitch.active.contactSeconds*1000).roundToInt()}")
        Log.i("ArcadeMusicLayout","track=$index staticPhraseScale=${musicPresentation.scale} offset=${musicPresentation.routeOffset(0f)} fixedBaseFitUnchanged=true")
        Log.i("ArcadeMusic","loaded course=$index nodes=${loaded.getChildren().size} length=${vm.engine.musicPhysics.course.distances.last()}m")
        }finally{musicLoading=false}
    }
    private fun playEffect(name:String,volume:Float=.28f,pan:Float=0f,rate:Float=1f){
        if(vm.state.value.muted)return
        effects[name]?.let{if(it in loadedSounds){val stream=sound.play(it,volume*(1-.25f*pan),volume*(1+.25f*pan),1,0,rate)
            if(com.kejing.spatialarcade.BuildConfig.DEBUG)Log.i("ArcadeAudio","effect=$name stream=$stream volume=$volume")}}
    }
    /** Bounded Entity particle pool: 3 overlapping bursts × 16 shards, lifetime 450 ms. */
    private fun animateShards(dt:Float){if(vm.kind!=GameKind.D)return
        for(i in shardAge.indices){shardAge[i]+=dt;val age=shardAge[i];val n=nodes["Shard_$i"]?:continue;n.enabled=age<.45f
            if(age<.45f){val a=(i%16)*2.39996f;val speed=.06f+(i%5)*.018f;val p=shardOrigin[i]
                move("Shard_$i",p+Point(cos(a)*speed*age,sin(a)*speed*age+.07f*age-.24f*age*age,.04f*age+(i%3)*.012f*age))
                n.components[TransformComponent::class.java]?.setScaleVector(Vector3(1-age/.45f,1-age/.45f,1-age/.45f))?.setEulerAngles(EulerAngles(age*310+i*19f,age*180f,age*230f))}}
    }
    fun musicControl(entity:Entity?)=if(vm.kind==GameKind.C)MusicControl.entries.firstOrNull{it.hitName==entity?.getName()} else null
    fun pressMusicControl(control:MusicControl,pressed:Boolean){
        if(vm.kind!=GameKind.C)return
        if(musicControls.press(control,pressed)){
            vm.onEvent(ArcadeEvent.MusicControlPress(control))
            Log.i("ArcadeMusicControls","3D ${control.name} speed=${vm.state.value.musicSpeed} volume=${vm.state.value.musicVolume} mute=${vm.state.value.muted}")
        }
    }
    fun releaseMusicControls(){musicControls.releaseAll()}
    fun isMusicButton(entity:Entity?)=vm.kind==GameKind.C&&entity?.getName()=="Music playback hit target"
    fun isMusicBook(entity:Entity?)=vm.kind==GameKind.C&&entity?.getName()=="Music book hit target"
    fun isMusicBookClosed()=musicBookMotion.openFraction==0f
    fun isMusicTrackReady(track:Int)=musicModelTrack==track&&!musicLoading&&musicSkinSwitch.appliedTrack==track&&vm.state.value.ready
    fun focusMusicBook(focused:Boolean){musicBookFocused=focused}
    fun pressMusicBook(pressed:Boolean){
        if(vm.kind!=GameKind.C)return
        if(musicBook.press(pressed&&vm.state.value.ready)&&vm.selectFromPhysicalBook()){
            val exitedCapture=frozenForCapture
            frozenForCapture=false
            musicWallNanos=System.nanoTime()
            // Stop audio immediately with the selection, without waiting for an
            // ECS/render frame. The pending opening still uses normal ECS time.
            sound.autoPause();musicAudioPaused=true
            Log.i("ArcadeBook","SELECT opening=true menu=false phase=${vm.engine.snapshot.phase} exitedCapture=$exitedCapture frozen=$frozenForCapture")
        }
    }
    fun pressMusicButton(pressed:Boolean){
        if(vm.kind!=GameKind.C)return
        val accepted=musicButton.press(pressed)
        val exitedCapture=accepted&&frozenForCapture
        if(accepted){
            if(exitedCapture){frozenForCapture=false;musicTrail.clear();musicEffectTime=-1f;musicEffectImpact=-100f;musicStreams.forEach{sound.stop(it)};musicStreams.clear()}
            vm.playFromPhysicalButton(exitedCapture)
            musicWallNanos=System.nanoTime()
            if(vm.engine.snapshot.phase==Phase.PAUSED){sound.autoPause();musicAudioPaused=true}
            else if(musicAudioPaused){sound.autoResume();musicAudioPaused=false}
        }
        Log.i("ArcadeInput","music button down=$pressed accepted=$accepted exitedCapture=$exitedCapture frozen=$frozenForCapture ready=${vm.state.value.ready} phase=${vm.engine.snapshot.phase}")
    }
    fun settleMusicPresentation(){repeat(120){musicPresentation.update(1f/60,vm.engine.musicPhysics)}}
    fun isGravityPedal(entity:Entity?)=vm.kind==GameKind.B&&entity?.getName()=="Gravity pedal"
    fun isOrbitLauncher(entity:Entity?)=vm.kind==GameKind.B&&entity?.getName()=="Engine launcher hit target"
    fun pressGravityPedal(pressed:Boolean){
        val before=vm.engine.gravityPulse.down;val score=vm.engine.snapshot.score;vm.engine.setGravityPedal(pressed);vm.sync()
        if(com.kejing.spatialarcade.BuildConfig.DEBUG&&before!=pressed)Log.i("ArcadeInput","3D pedal pressed=$pressed active=${vm.engine.gravityField} score=$score->${vm.engine.snapshot.score} cells=${vm.engine.gravityPulse.cells}")
    }
    fun grab(entity:Entity?){val name=entity?.getName().orEmpty();val target=when{ isOrbitLauncher(entity)||name.contains("Plunger")->"plunger";name.contains("Boot")->"boot";name.contains("Paddle")->"paddle";else->"ball"};vm.engine.grab(target);vm.sync()
        if(vm.kind==GameKind.B)Log.i("ArcadeInput","grab target=$name phase=${vm.engine.snapshot.phase}")}
    fun dragPixels(x:Float,y:Float,z:Float){
        val c=content?:return;val f=fit?:return
        val origin=c.convertPosition(Vector3.ZERO,ViewCoordinateSpace.Local,f)
        val delta=c.convertPosition(Vector3(x,y,z),ViewCoordinateSpace.Local,f)-origin
        vm.engine.drag(delta.x,delta.y,delta.z)
        if(vm.kind==GameKind.B&&vm.engine.snapshot.phase==Phase.HELD)Log.i("ArcadeInput","piston drag delta=$delta pull=${vm.engine.pull}")
        render()
    }
    fun release(cancel:Boolean){vm.engine.release(cancel);vm.sync();if(vm.kind==GameKind.B)Log.i("ArcadeInput","piston release cancel=$cancel power=${vm.engine.snapshot.power} phase=${vm.engine.snapshot.phase}")}
    fun dispose(){musicControls.releaseAll();musicTrail.clear();musicButton.press(false);musicBook.press(false);disposed=true;sceneScope.cancel();musicSkinMaterials=null;musicModel=null;musicParts.clear();musicPlates.clear();plateBases.clear();ringing.clear();bookStitches.clear();vm.engine.setGravityPedal(false);if(ArcadeSystem.active===this){ArcadeSystem.active=null;EcsSystem.unregister(ArcadeSystem::class.java)};owner?.destroy();owner=null;nodes.clear();sound.release()}
}
