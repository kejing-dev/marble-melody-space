package com.kejing.spatialarcade.domain.usecase

import com.kejing.spatialarcade.domain.model.Point
import com.kejing.spatialarcade.domain.model.OrbitImpact
import com.kejing.spatialarcade.domain.model.OrbitImpactKind
import kotlin.math.*

/** Arcade gravity in the table's X/Z plane, with explicit finite energy and cooldowns. */
class OrbitSimulation {
    data class Body(val x:Float,val z:Float,val radius:Float,val gravity:Float,val points:Int)
    companion object {
        val bodies=listOf(Body(0f,-.055f,.021f,.0021f,80),Body(-.064f,-.105f,.015f,.0015f,60),Body(.060f,.008f,.015f,.0017f,60))
        val portals=listOf(Point(0f,.080f,-.144f),Point(-.092f,.080f,-.027f),Point(.087f,.080f,-.075f))
        val nebula=Point(-.070f,.074f,.051f)
        val fieldCenter=Point(0f,.077f,.153f)
        /** Acceleration, not a contact plane: outside this volume a pressed pedal has no effect. */
        fun fieldAcceleration(p:Point,pressed:Boolean):Point {
            if(!pressed)return Point(0f,0f,0f)
            val d=hypot(p.x/.118f,(p.z-fieldCenter.z)/.065f)
            if(d>=1f)return Point(0f,0f,0f)
            val weight=(1f-d*d).pow(2)
            return Point(-p.x/.118f*.9f*weight,0f,-3.8f*weight)
        }
    }
    var ball=Point(.133f,.074f,.16f);private set
    var age=0f;private set
    var energy=48f;private set
    var universe=0;private set
    var warps=0;private set
    var saves=0;private set
    var hits=0;private set
    var cometHits=0;private set
    var charge=0f;private set
    var points=0;private set
    val objectives=OrbitObjectives()
    var combo=0;private set
    private var comboWindow=0f
    private var lastBody=-1
    var message="点亮三座信标 · 开启超新星双倍得分";private set
    var drained=false;private set
    var warpProgress=-1f;private set
    var impact:OrbitImpact?=null;private set
    private var impactSerial=0
    private var impactCooldown=0f
    private var childAge=0f
    private var vx=-.15f
    private var vz=.16f
    private var cooldown=FloatArray(5)
    private var gateCooldown=0f
    private var saveCooldown=0f
    private var cometCooldown=0f
    private var pulseCooldown=0f
    private var warpFrom=ball
    private var warpTo=ball
    private var targetUniverse=0
    private var lastEventAt=-10f
    val cometPhase get()=(age+7f)%15f
    val cometVisible get()=cometPhase in 11f..14f
    val cometWarning get()=cometPhase in 9f..11f
    val comet get():Point {val u=((cometPhase-11f)/3f).coerceIn(0f,1f);return Point(-.110f+.216f*u,.096f,-.12f+.095f*u)}
    val shieldOn get()=false // No automatic barrier: an unpowered field is completely permeable.
    val worldName get()=listOf("母宇宙 · 日心海","子宇宙 · 翡翠潮汐","子宇宙 · 紫罗兰双星")[universe]
    /** Moving moons use exactly the same centers for visible meshes and ball collision. */
    fun moon(index:Int):Body {
        val a=age*(if(index==0).8f else -1.1f)+index*2f
        return if(index==0)Body(.055f+.009f*cos(a),-.129f+.007f*sin(a),.007f,.00035f,90)
        else Body(-.031f+.009f*cos(a),.019f+.007f*sin(a),.008f,.00040f,90)
    }
    fun body(index:Int)=if(index<3)bodies[index] else moon(index-3)
    fun start(power:Float){
        ball=Point(-.057f,.074f,-.156f);age=0f;energy=48f;universe=0;warps=0;saves=0;hits=0;cometHits=0;charge=0f;points=0
        vx=-.10f+.20f*power;vz=.18f;drained=false;warpProgress=-1f;childAge=0f
        cooldown.fill(0f);gateCooldown=2f;saveCooldown=0f;cometCooldown=0f;pulseCooldown=0f;lastEventAt=-10f
        impact=null;impactCooldown=0f
        objectives.reset();combo=0;comboWindow=0f;lastBody=-1
        message="点亮三座信标 · 开启超新星双倍得分"
    }
    private fun event(text:String){message=text;lastEventAt=age}
    private fun impact(kind:OrbitImpactKind,speed:Float,x:Float){
        // At most one cue per 60 ms, including contact jitter across adjacent geometry.
        if(impactCooldown>0f)return
        impact=OrbitImpact(++impactSerial,kind,(speed/.36f).coerceIn(.2f,1f),x)
        impactCooldown=.06f
    }
    private fun beginWarp(portal:Int,home:Boolean=false){
        warpFrom=ball;targetUniverse=if(home)0 else (universe+1)%3
        val exits=listOf(Point(.062f,.080f,.076f),Point(-.057f,.080f,.032f),Point(.025f,.080f,-.107f))
        warpTo=exits[(portal+universe)%3];warpProgress=0f;gateCooldown=4f
        energy=(energy-8f).coerceAtLeast(0f)
        event(if(home)"子宇宙合拢 · 回到母宇宙" else "虫洞穿越 · 保留得分与能量")
    }
    fun step(dt:Float,flipper:Float)=step(dt,abs(flipper)>.1f)
    fun step(dt:Float,pressed:Boolean){
        if(drained)return
        age+=dt;gateCooldown-=dt;saveCooldown-=dt;cometCooldown-=dt;pulseCooldown-=dt;impactCooldown-=dt
        objectives.step(dt);comboWindow-=dt;if(comboWindow<=0f)combo=0
        for(i in cooldown.indices)cooldown[i]-=dt
        energy=(energy-dt*(if(age<30f)1.25f else if(age<60f)2.6f else 8f)).coerceAtLeast(0f)
        if(warpProgress>=0f){
            warpProgress+=dt/.72f;val u=warpProgress.coerceIn(0f,1f)
            ball=warpFrom*(1-u)+warpTo*u+Point(0f,.060f*sin(PI.toFloat()*u),0f)
            if(u>=1){universe=targetUniverse;childAge=0f;warps++;val reward=180*objectives.multiplier;points+=reward;warpProgress=-1f;vx=if(ball.x>0)-.17f else .17f;vz=-.17f;event("${worldName} · 穿越 +$reward")}
            return
        }
        if(universe!=0){childAge+=dt;if(childAge>12f){beginWarp(0,true);return}}
        // A weak downward drift allows natural drainage; softened gravity prevents singularities.
        var ax=0f;var az=.027f
        for(i in 0..4){val b=body(i)
            val dx=b.x-ball.x;val dz=b.z-ball.z;val d2=dx*dx+dz*dz;val d=sqrt(d2).coerceAtLeast(.01f)
            val force=(b.gravity/(d2+.0035f)).coerceAtMost(.23f)*(if(universe==1).70f else if(universe==2)1.25f else 1f)
            ax+=dx/d*force;az+=dz/d*force
            // Tangential gravity is an intentional arcade slingshot, not an orbital-physics simulation.
            val spin=if((i+universe)%2==0)1f else -1f
            ax+=-dz/d*force*.24f*spin;az+=dx/d*force*.24f*spin
        }
        val field=fieldAcceleration(ball,pressed)
        ax+=field.x;az+=field.z
        if(field.z<-.01f){
            energy=(energy-dt*4f).coerceAtLeast(0f)
            if(vz>0f&&vz+az*dt<=0f){saves++;event("引力接球成功 · 瞄准信标");impact(OrbitImpactKind.FLIPPER,hypot(vx,vz),ball.x)}
        }
        vx+=ax*dt;vz+=az*dt
        var x=ball.x+vx*dt;var z=ball.z+vz*dt
        if(x<-.116f||x>.112f){impact(OrbitImpactKind.RAIL,abs(vx),x);vx=if(x<0)abs(vx) else -abs(vx);x=x.coerceIn(-.116f,.112f)}
        if(z<-.163f){impact(OrbitImpactKind.RAIL,abs(vz),x);vz=abs(vz);z=-.163f}
        for(i in 0..4){val b=body(i)
            val dx=x-b.x;val dz=z-b.z;val d=sqrt(dx*dx+dz*dz);val radius=b.radius+.009f
            if(d<radius){
                val nx=if(d<.0001f)1f else dx/d;val nz=if(d<.0001f)0f else dz/d
                x=b.x+nx*(radius+.0003f);z=b.z+nz*(radius+.0003f)
                val dot=vx*nx+vz*nz
                if(dot<0){impact(OrbitImpactKind.BODY,abs(dot),x);vx-=2*dot*nx;vz-=2*dot*nz;vx+=nx*.035f;vz+=nz*.035f
                    if(cooldown[i]<=0){
                        cooldown[i]=.65f;combo=if(comboWindow>0f&&lastBody!=i)(combo+1).coerceAtMost(3) else 1
                        comboWindow=2.5f;lastBody=i
                        val reward=b.points*(universe+1)*combo*objectives.multiplier;points+=reward;hits++
                        val name=if(i==0)"恒星" else if(i<3)"行星弹弓" else "巡游卫星"
                        event("$name +$reward"+if(combo>1)" · ${combo}连击" else "")
                    }}
            }
        }
        if(hypot(x-nebula.x,z-nebula.z)<.038f){val add=min(100f-energy,dt*24f);energy+=add;charge+=add;if(age-lastEventAt>1f)event("星云充能 · 引力与穿越能量")}
        val beaconReward=objectives.contact(Point(x,.074f,z))
        if(beaconReward>0){points+=beaconReward;impact(OrbitImpactKind.COMET,.32f,x)
            event(if(objectives.nova>0f)"超新星 +300 · 8 秒双倍得分" else "星际信标 ${objectives.count}/3 +40")}
        if(cometVisible&&cometCooldown<=0&&hypot(x-comet.x,z-comet.z)<.030f){
            impact(OrbitImpactKind.COMET,.36f,x)
            val reward=250*objectives.multiplier
            cometCooldown=3f;cometHits++;points+=reward;energy=min(100f,energy+18f);vx=if(x>0)-.19f else .19f;vz=-.19f;event("彗星来访！ +$reward · 能量 +18")
        }
        if(gateCooldown<=0f&&age>=6f){
            for((i,p)in portals.withIndex())if(hypot(x-p.x,z-p.z)<.022f){ball=Point(x,.080f,z);beginWarp(i);return}
        }
        // The outlanes funnel into the open field and collection gutter.
        if(z>.072f&&abs(x)>.080f)vx=if(x>0)-abs(vx)-dt*.12f else abs(vx)+dt*.12f
        val speed=hypot(vx,vz)
        if(speed>.36f){vx*=.36f/speed;vz*=.36f/speed}
        // Never inject an automatic save near the open drain.
        if(speed<.11f&&z<.072f){vz=-.14f;vx=if(x>0)-.09f else .09f}
        ball=Point(x,.074f+.007f*sin(age*5f),z)
        if(z>.184f){drained=true;event("航行 ${age.toInt()} 秒 · 再拉一次发射")}
        else if(age-lastEventAt>2f)message=if(cometWarning)"彗星即将来访 · 接住它补能" else if(objectives.nova>0f)"超新星双倍得分中 · 追击天体" else "信标 ${objectives.count}/3 · 按住接球，松开充能"
    }
}
