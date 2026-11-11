package com.kejing.spatialarcade.domain.usecase
import com.kejing.spatialarcade.domain.model.*
import kotlin.math.*
import com.kejing.spatialarcade.data.repository.ArcadeCatalog

/** Deterministic simulation. Positions are authored model coordinates in PICO metres. */
class GameRulesUseCase(val kind:GameKind,val contacts:List<Contact>,initialNotes:List<Note>) {
    var notes=initialNotes;private set
    var snapshot=GameSnapshot(status=readyText())
        private set(value){
            val changed=kind==GameKind.B&&(value.status!=field.status||value.score!=field.score)
            field=value.copy(previousStatus=if(changed)field.status else field.previousStatus,statusSerial=field.statusSerial+if(changed)1 else 0)
        }
    val musicPhysics by lazy { MusicSimulation(MusicCourses.get(snapshot.track)) }
    var ball=initialBall();private set
    var paddle=0f;private set
    var pull=0f;private set
    var aim=0f;private set
    var noteIndex=-1;private set
    var musicScroll=if(kind==GameKind.C)musicPhysics.scroll else 0f;private set
    private val musicEvents=ArrayDeque<Int>()
    fun consumeMusicNotes():List<Int> = musicEvents.toList().also{musicEvents.clear()}
    var flipper=0f;private set
    val gravityPulse=GravityPulse()
    val gravityField get()=gravityPulse.active
    private var pedalNotice=""
    private var pedalNoticeTime=0f
    fun setGravityPedal(pressed:Boolean){
        if(kind!=GameKind.B)return
        applyGravityResult(gravityPulse.press(pressed,gravityEligible(),snapshot.score))
        syncGravity()
    }
    private fun gravityEligible()=snapshot.phase==Phase.PLAYING&&returnProgress<0f&&launchProgress<0f
    private fun applyGravityResult(result:GravityPulse.Result){
        val notice=when(result){
            GravityPulse.Result.STARTED->"引力开启 · 按住持续耗能"
            GravityPulse.Result.BOUGHT->"能量补充 +1 格 · −50 分"
            GravityPulse.Result.EMPTY->"能量不足 · 松开充能或积累 50 分"
            else->""
        }
        if(notice.isNotEmpty()){
            pedalNotice=notice;pedalNoticeTime=1.3f
            snapshot=snapshot.copy(score=snapshot.score-if(result==GravityPulse.Result.BOUGHT)GravityPulse.cost else 0,status=notice)
        }
    }
    private fun syncGravity(){snapshot=snapshot.copy(gravityCells=gravityPulse.cells,gravityActive=gravityField)}
    private fun releaseFields(){gravityPulse.cancel();syncGravity()}
    val bricks=IntArray(80)
    var launchProgress=-1f;private set
    var orbitProgress=-1f;private set
    var returnProgress=-1f;private set
    private var returnRoute:OrbitReturnRoute?=null
    var hit:HitEvent?=null;private set
    val orbit=OrbitSimulation()
    val launcher=OrbitLauncher()
    private var launchDistance=0f
    private var railSpeed=0f
    private var orbitLastPoints=0
    private var serial=0
    private var flightContact=2
    private var resultDelay=0f
    private var pendingKickAdvance=false
    private var stall=0f
    private val cooldown=FloatArray(3)
    val kickLevel get()=ArcadeCatalog.kicks[snapshot.layout.coerceIn(ArcadeCatalog.kicks.indices)]
    val brickLevel get()=ArcadeCatalog.levels[snapshot.layout.coerceIn(0,5)]
    val keeperX get()=sin(elapsed*2)*.055f
    val entry get()=musicPhysics.entryBall
    private var elapsed=0f
    private var accumulator=0f
    private var vx=.13f;private var vy=.27f
    private var held=""
    private var savedPhase=Phase.READY
    var levelTransition=0f;private set
    val paddleScale get()=1f-snapshot.circuit/3f
    val paddleHalfWidth get()=.0675f*paddleScale
    val paddleLimit get()=.287f-paddleHalfWidth
    init {if(kind==GameKind.D)setLayout(0)}
    fun readyText()=when(kind){GameKind.A->"拉球鞋蓄力，松手射门";GameKind.B->"拉发射头，开始一段宇宙航行";GameKind.C->"按 ▶ 让星光落下 · 点击书本选曲";GameKind.D->"抓住挡板，从侧路突破"}
    fun initialBall()=when(kind){GameKind.A->Point(kickLevel.startX,.053f,kickLevel.startZ);GameKind.B->Point(.133f,.074f,.16f);GameKind.C->musicPhysics.entryBall;GameKind.D->Point(0f,.099f,.031f)}
    fun setContact(id:Int){if(id !in contacts.indices)return;snapshot=snapshot.copy(contact=id,status=if(snapshot.phase==Phase.PLAYING)"下一脚：${contacts[id].label}" else contacts[id].label)}
    fun reset(){gravityPulse.reset();pedalNotice="";pedalNoticeTime=0f;launcher.reset();launchDistance=0f;railSpeed=0f;if(kind==GameKind.C)musicPhysics.reset();releaseFields();val old=snapshot;snapshot=GameSnapshot(contact=old.contact,layout=old.layout,track=old.track,status=readyText(),kickSerial=old.kickSerial,goalSerial=old.goalSerial);ball=initialBall();paddle=0f;pull=0f;aim=0f;elapsed=0f;accumulator=0f;held="";noteIndex=-1;musicScroll=if(kind==GameKind.C)musicPhysics.scroll else 0f;musicEvents.clear();launchProgress=-1f;orbitProgress=-1f;returnProgress=-1f;returnRoute=null;resultDelay=0f;pendingKickAdvance=false;stall=0f;flipper=0f;cooldown.fill(0f);hit=null;orbit.start(.65f);orbitLastPoints=0;levelTransition=0f;if(kind==GameKind.D)populateBricks()}
    private fun populateBricks(){brickLevel.cells.forEachIndexed{i,v->bricks[i]=v};snapshot=snapshot.copy(remaining=bricks.count{it>0},status=brickLevel.hint)}
    fun setLayout(index:Int){if(kind !in listOf(GameKind.A,GameKind.D))return;snapshot=snapshot.copy(layout=index.coerceIn(0,if(kind==GameKind.A)ArcadeCatalog.kicks.lastIndex else 5));reset()}
    fun selectTrack(index:Int){if(kind!=GameKind.C||index !in ArcadeCatalog.tracks.indices)return;notes=ArcadeCatalog.tracks[index].notes;snapshot=snapshot.copy(track=index);musicPhysics.course=MusicCourses.get(index);reset();snapshot=snapshot.copy(status="${ArcadeCatalog.tracks[index].title}已就绪 · 按 ▶ 开始")}
    fun grab(target:String){
        // With no restart control, pulling the physical launcher starts the next round.
        if(kind==GameKind.B&&snapshot.phase==Phase.RESULT&&target=="plunger")reset()
        if(kind==GameKind.D&&snapshot.phase==Phase.RESULT&&target=="paddle")setLayout(0)
        if(snapshot.phase==Phase.RESULT)return
        if(kind==GameKind.B&&returnProgress>=0f)return
        if(kind==GameKind.B&&target=="plunger"&&snapshot.phase==Phase.PLAYING)return
        if(kind==GameKind.B&&target!="plunger")return
        held=target
        when(kind){
            GameKind.A->{if(snapshot.phase==Phase.PLAYING){held="";return};pull=0f;snapshot=snapshot.copy(phase=Phase.HELD,power=0f,status="后拉蓄力 · 击球点决定弧线")}
            GameKind.B->{if(target=="plunger"){pull=0f;launcher.grab();snapshot=snapshot.copy(phase=Phase.HELD,power=0f,status="后拉活塞蓄能 · 松手点火")}
            }
            GameKind.C->{if(snapshot.phase==Phase.PLAYING||snapshot.phase==Phase.PAUSED){held="";return};pull=0f;snapshot=snapshot.copy(phase=Phase.HELD,power=0f,status="在起始音板上方松手，开始演奏")}
            GameKind.D->{if(snapshot.phase==Phase.READY){vx=.13f;vy=sqrt(brickLevel.speed.pow(2)-vx*vx)};snapshot=snapshot.copy(phase=Phase.PLAYING,status="左右拖板，松手暂停")}
        }
    }
    fun drag(dx:Float,dy:Float,dz:Float){if(held.isEmpty())return
        when(kind){
            GameKind.A->{pull=(pull+dz).coerceIn(0f,.07f);snapshot=snapshot.copy(power=pull/.07f)}
            GameKind.B->{if(held=="plunger"){pull=(pull+dz).coerceIn(0f,OrbitLauncher.stroke);launcher.pull(pull);snapshot=snapshot.copy(power=pull/OrbitLauncher.stroke)}}
            GameKind.C->{if(held=="plunger"){pull=(pull+dz).coerceIn(0f,.04f);snapshot=snapshot.copy(power=pull/.04f);return};ball=Point((ball.x+dx).coerceIn(-.36f,.36f),(ball.y+dy).coerceIn(.05f,.80f),(ball.z+dz).coerceIn(-.30f,.30f))}
            GameKind.D->{paddle=(paddle+dx).coerceIn(-paddleLimit,paddleLimit)}
        }
    }
    fun release(cancel:Boolean=false){if(cancel)releaseFields();if(held.isEmpty())return
        val target=held;held=""
        if(cancel){if(kind==GameKind.B)launcher.release(true);if(kind==GameKind.A||kind==GameKind.C||kind==GameKind.B){ball=initialBall();pull=0f;snapshot=snapshot.copy(phase=Phase.READY,power=0f,status="手势中断，未扣次数")}else pause();return}
        when(kind){
            GameKind.A->{if(snapshot.power<.04f){pull=0f;snapshot=snapshot.copy(phase=Phase.READY,status="再往后拉一点");return};elapsed=0f;resultDelay=0f;flightContact=snapshot.contact;pull=0f;snapshot=snapshot.copy(phase=Phase.PLAYING,kickSerial=snapshot.kickSerial+1,status="射门！")}
            GameKind.B->{if(target=="plunger"){
                if(!launcher.release()){pull=0f;snapshot=snapshot.copy(phase=Phase.READY,power=0f,status="再向后拉一点 · 松手点火");return}
                launchProgress=0f;launchDistance=0f;railSpeed=0f;ball=initialBall();savedPhase=Phase.PLAYING
                snapshot=snapshot.copy(phase=Phase.PLAYING,flightSeconds=0f,energy=48f,universe=0,status="活塞回弹 · 引擎点火")}}
            GameKind.C->{
                if(target=="plunger"||MusicCourse.distance(ball,entry)<.06f){
                    musicPhysics.place();ball=musicPhysics.ball;elapsed=0f;noteIndex=-1;musicScroll=if(kind==GameKind.C)musicPhysics.scroll else 0f;musicEvents.clear()
                    snapshot=snapshot.copy(phase=Phase.PLAYING,status="星光落下 · 旋律即将开始")
                }else{ball=initialBall();snapshot=snapshot.copy(phase=Phase.READY,status="按 ▶ 开始演奏")}
            }
            GameKind.D->pause()
        }
    }
    /** The physical cap starts a short natural drop, or pauses the complete physics clock. */
    fun toggleMusicPlayback(){
        if(kind!=GameKind.C)return
        when(snapshot.phase){
            Phase.PLAYING->pause()
            Phase.PAUSED->resume()
            Phase.READY,Phase.RESULT->{
                musicPhysics.place();ball=musicPhysics.ball;launchProgress=-1f;elapsed=0f
                accumulator=0f;noteIndex=-1;musicScroll=if(kind==GameKind.C)musicPhysics.scroll else 0f;musicEvents.clear();held=""
                snapshot=snapshot.copy(phase=Phase.PLAYING,count=0,status="星光落下 · 再按 Ⅱ 暂停")
            }
            Phase.HELD->Unit
        }
    }
    fun pause(){releaseFields();if(snapshot.phase==Phase.PLAYING||snapshot.phase==Phase.HELD){savedPhase=snapshot.phase;snapshot=snapshot.copy(phase=Phase.PAUSED,status="已暂停，进度为你保留")};held="";flipper=0f}
    fun resume(){if(snapshot.phase==Phase.PAUSED)snapshot=snapshot.copy(phase=if(savedPhase==Phase.HELD)Phase.READY else savedPhase,status="继续这一局")}
    /** Uses precisely the same path for preview dots and the actual shot. */
    fun shot(t:Float,contact:Int=snapshot.contact):Point {
        val p=contacts[contact];val seconds=t.coerceIn(0f,1f)*1.1f;val speed=.28f+.66f*snapshot.power
        return Point(kickLevel.startX+p.curve*4f*seconds*seconds,
            .053f+(.26f+snapshot.power*.34f+p.lift*2f)*seconds-.425f*seconds*seconds,
            kickLevel.startZ-speed*seconds)
    }
    fun step(dt:Float){if(snapshot.phase==Phase.PAUSED||(snapshot.phase!=Phase.PLAYING&&kind!=GameKind.B))return
        // MusicSimulation already solves at <=1/480 s and consumes the final
        // partial step. Passing elapsed time directly avoids an extra 120 Hz
        // pose hold followed by a double jump on irregular render frames.
        if(kind==GameKind.C){tick(dt.coerceIn(0f,1f));return}
        accumulator+=dt.coerceIn(0f,.1f);while(accumulator>=1f/120f){if(kind==GameKind.B){launcher.step(1f/120f);applyGravityResult(gravityPulse.step(1f/120f,gravityEligible(),snapshot.score));pedalNoticeTime=(pedalNoticeTime-1f/120f).coerceAtLeast(0f);syncGravity()};tick(1f/120f);accumulator-=1f/120f}
    }
    private fun tick(dt:Float){if(snapshot.phase!=Phase.PLAYING)return;elapsed+=dt
        when(kind){GameKind.A->football(dt)
            GameKind.C->music(dt)
            GameKind.D->breakout(dt)
            GameKind.B->pinball(dt)
        }
    }
    private fun finishKick(goal:Boolean,message:String){pendingKickAdvance=goal;snapshot=snapshot.copy(score=snapshot.score+if(goal)2 else 0,count=snapshot.count+1,goalSerial=snapshot.goalSerial+if(goal)1 else 0,status=if(goal)"进球！ +2 · 即将进入下一关" else message);resultDelay=.8f}
    private fun football(dt:Float){
        if(resultDelay>0){resultDelay-=dt;if(resultDelay<=0){
            val next=if(pendingKickAdvance)(snapshot.layout+1)%ArcadeCatalog.kicks.size else snapshot.layout
            val status=if(pendingKickAdvance)"${ArcadeCatalog.kicks[next].title} · 拉球鞋开始" else snapshot.status
            snapshot=snapshot.copy(layout=next,phase=Phase.READY,power=0f,status=status)
            ball=initialBall();elapsed=0f;pendingKickAdvance=false
        };return}
        val old=ball;ball=shot(elapsed/1.1f,flightContact)
        // The four-person wall is fixed: 106 x 16 x 112 mm; sweep the 10 mm ball.
        for(z in listOf(kickLevel.wallZ+.018f,kickLevel.wallZ,kickLevel.wallZ-.018f)) {
            if(old.z>z&&ball.z<=z){
                val t=(old.z-z)/(old.z-ball.z);val p=old*(1-t)+ball*t
                if(p.y<.122f&&abs(p.x-kickLevel.wallX)<.063f){finishKick(false,"被人墙挡住 · 换击球点或减力轻挑");return}
            }
        }
        if(old.z>-.24f&&ball.z<=-.24f){
            val t=(old.z+.24f)/(old.z-ball.z);val p=old*(1-t)+ball*t
            val goal=abs(p.x)<.101f&&p.y in .053f.. .154f
            ball=p
            finishKick(goal,if(goal)"进球！ +2" else if(p.y>.154f)"越过横梁 · 减力或选下坠球" else "偏出球门 · 调整力度与击球点");return
        }
        if(ball.y<.053f||abs(ball.x)>.226f||elapsed>1.1f)finishKick(false,"未到球门或弯得过多 · 调整力度")
    }

    private fun music(dt:Float){
        if(notes.isEmpty()){snapshot=snapshot.copy(phase=Phase.READY,status="曲谱为空，请换一首");return}
        musicPhysics.step(dt);ball=musicPhysics.ball;noteIndex=musicPhysics.noteIndex;musicScroll=musicPhysics.scroll
        launchProgress=if(musicPhysics.launching)((ball.y-initialBall().y)/(entry.y-initialBall().y)).coerceIn(0f,1f) else -1f
        while(musicPhysics.events.isNotEmpty())musicEvents.addLast(musicPhysics.events.removeFirst())
        snapshot=snapshot.copy(count=noteIndex+1,status=when{
            musicPhysics.failedLaunch->"按 ▶ 重新开始"
            musicPhysics.missed->"钢球偏离音板 · 重新放球开始"
            musicPhysics.completed->"演奏完成 · 按 ▶ 再听一次"
            musicPhysics.noteIndex<0->"星光落向第一音板"
            musicPhysics.finishing->"尾音回响"
            musicPhysics.fallingToPlate->"离开滑道 · 落向音板"
            musicPhysics.flying->"撞板发声 · 自然反弹"
            else->"${musicPhysics.course.name} · 第 ${noteIndex+1} / ${notes.size} 音"
        },phase=if(musicPhysics.completed||musicPhysics.failedLaunch||musicPhysics.missed)Phase.READY else Phase.PLAYING)
    }
    private fun nextBrickLevel(){
        val last=snapshot.layout==ArcadeCatalog.levels.lastIndex
        val lap=if(last)(snapshot.circuit+1)%3 else snapshot.circuit
        snapshot=snapshot.copy(layout=if(last)0 else snapshot.layout+1,circuit=lap,combo=0)
        populateBricks();paddle=paddle.coerceIn(-paddleLimit,paddleLimit);ball=Point(paddle,.099f,.031f);vx=.10f;vy=sqrt(brickLevel.speed.pow(2)-vx*vx);hit=null
        snapshot=snapshot.copy(status="第 ${lap+1} 轮 · ${brickLevel.title} · 挡板 ${(paddleScale*100).roundToInt()}%")
    }
    private fun breakout(dt:Float){
        if(levelTransition>0){levelTransition-=dt;if(levelTransition<=0)nextBrickLevel();return}
        var x=ball.x+vx*dt;var y=ball.y+vy*dt
        if(x<-.287f||x>.287f){vx=-vx;x=x.coerceIn(-.287f,.287f)}
        if(y>.507f){vy=-abs(vy);y=.507f}
        if(vy<0&&y<=.099f&&ball.y>=.094f&&abs(x-paddle)<paddleHalfWidth+.009f){val edge=((x-paddle)/(paddleHalfWidth+.009f)).coerceIn(-.88f,.88f);vx=brickLevel.speed*edge;vy=sqrt(brickLevel.speed.pow(2)-vx*vx);y=.100f;snapshot=snapshot.copy(combo=0)}
        for(i in bricks.indices){if(bricks[i]==0)continue;val bx=-.252f+(i%10)*.056f;val by=.479f-(i/10)*.028f;val dx=x-bx;val dy=y-by
            if(abs(dx)<.032f&&abs(dy)<.019f){
                if(abs(ball.x-bx)>=.032f){vx=if(dx>0)abs(vx) else -abs(vx);x=bx+if(dx>0).0321f else -.0321f}
                else {vy=if(dy>0)abs(vy) else -abs(vy);y=by+if(dy>0).0191f else -.0191f}
                if(bricks[i]>0){bricks[i]--;val broken=bricks[i]==0;hit=HitEvent(++serial,Point(bx,by,.041f),i/10,broken)
                    snapshot=snapshot.copy(score=snapshot.score+if(broken)20+snapshot.combo*5 else 5,combo=snapshot.combo+if(broken)1 else 0,remaining=bricks.count{it>0})
                    if(snapshot.remaining==0){levelTransition=1f;snapshot=snapshot.copy(status=if(snapshot.layout==5)"本轮完成 · 下一轮挡板 ${((1f-((snapshot.circuit+1)%3)/3f)*100).roundToInt()}%" else "关卡完成 · 即将进入下一关")}};break}
        }
        if(abs(vy)<.075f){vy=if(vy<0)-.075f else .075f;vx=(if(vx<0)-1 else 1)*sqrt(brickLevel.speed.pow(2)-vy*vy)}
        ball=Point(x,y,.031f);if(y<.038f)loseBall()
    }
    fun launchPosition(t:Float):Point {val u=t.coerceIn(0f,1f)
        return if(u<.7f)Point(.133f,.074f,.16f-u/.7f*.316f) else {val v=(u-.7f)/.3f;Point(.133f-.190f*v,.074f+.012f*sin(v*PI.toFloat()),-.156f-.025f*sin(v*PI.toFloat()))}}
    fun orbitPosition(t:Float):Point {val a=PI.toFloat()+t*PI.toFloat()*1.6f;return Point(.092f*cos(a),.088f+.04f*sin(PI.toFloat()*t),-.045f+.10f*sin(a))}
    private fun pinball(dt:Float){
        if(returnProgress>=0f){
            returnProgress=(returnProgress+dt/OrbitReturnRoute.duration).coerceAtMost(1f)
            ball=requireNotNull(returnRoute).position(returnProgress)
            if(returnProgress>=1f){returnProgress=-1f;returnRoute=null;loseBall()}
            return
        }
        if(launchProgress>=0){
            if(!launcher.fired)return
            if(railSpeed==0f){railSpeed=launcher.launchSpeed;snapshot=snapshot.copy(status="点火升空 · 推力 ${(snapshot.power*100).roundToInt()}%")}
            railSpeed=(railSpeed-dt*.10f).coerceAtLeast(.30f);launchDistance+=railSpeed*dt
            launchProgress=launchDistance/OrbitLaunchRail.length;ball=OrbitLaunchRail.position(launchDistance)
            if(launchProgress>=1){launchProgress=-1f;orbit.start(snapshot.power);orbitLastPoints=0;ball=orbit.ball};return}
        orbit.step(dt,gravityField);ball=orbit.ball
        val earned=orbit.points-orbitLastPoints
        if(earned>0)pedalNoticeTime=0f
        snapshot=snapshot.copy(score=snapshot.score+earned,status=if(orbit.drained)"沿底部轨道回收 · 滑向右侧发射区" else if(pedalNoticeTime>0f)pedalNotice else orbit.message,
            flightSeconds=orbit.age,energy=orbit.energy,universe=orbit.universe,warpCount=orbit.warps,
            saves=orbit.saves,cosmicHits=orbit.hits,cometHits=orbit.cometHits,nebulaCharge=orbit.charge,
            beaconCount=orbit.objectives.count,novaSeconds=orbit.objectives.nova)
        orbitLastPoints=orbit.points
        if(orbit.drained){releaseFields();returnRoute=OrbitReturnRoute(ball);returnProgress=0f;held="";flipper=0f;snapshot=snapshot.copy(status="沿底部轨道回收 · 滑向右侧发射区")}
    }
    private fun loseBall(){
        if(kind==GameKind.B){orbit.objectives.reset();snapshot=snapshot.copy(beaconCount=0,novaSeconds=0f)}
        val lives=snapshot.lives-1;snapshot=snapshot.copy(lives=lives,phase=if(lives==0)Phase.RESULT else Phase.READY,status=if(lives==0){if(kind==GameKind.B)"本局结束 · 拉发射头开始新一局" else "这一局结束，再玩一次吧"}else if(kind==GameKind.B)"钢球已滑回发射区 · 拉杆再出发" else "还剩 $lives 颗球");ball=initialBall();held=""
    }
    fun demo(){
        when(kind){GameKind.A->{setContact(kickLevel.exampleContact);grab("boot");drag(0f,0f,.07f*kickLevel.examplePower);release()}
            GameKind.B->{grab("plunger");drag(0f,0f,.055f);release()}
            GameKind.C->{reset();toggleMusicPlayback()}
            GameKind.D->grab("paddle")}
    }
}
