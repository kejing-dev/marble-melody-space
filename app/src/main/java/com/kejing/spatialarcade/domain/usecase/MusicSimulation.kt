package com.kejing.spatialarcade.domain.usecase

import com.kejing.spatialarcade.domain.model.Point
import com.kejing.spatialarcade.data.repository.MusicRouteData
import java.io.DataInputStream
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.InflaterInputStream
import kotlin.math.*

/** Double precision preserves one inertial coordinate system throughout a long score. */
data class MusicVector(val x:Double,val y:Double,val z:Double){
    operator fun plus(b:MusicVector)=MusicVector(x+b.x,y+b.y,z+b.z)
    operator fun minus(b:MusicVector)=MusicVector(x-b.x,y-b.y,z-b.z)
    operator fun times(s:Double)=MusicVector(x*s,y*s,z*s)
    fun dot(b:MusicVector)=x*b.x+y*b.y+z*b.z
    fun cross(b:MusicVector)=MusicVector(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x)
    fun length()=sqrt(dot(this))
    fun unit()=this*(1.0/length())
    fun point()=Point(x.toFloat(),y.toFloat(),z.toFloat())
}
data class MusicImpact(val center:MusicVector,val n:MusicVector,val a:MusicVector,val w:Double,val d:Double,val bounce:Double,val cornerRadius:Double=0.0){
    val contact get()=center.point();val normal get()=n.point();val axis get()=a.point()
    val width get()=w.toFloat();val depth get()=d.toFloat();val restitution get()=bounce.toFloat()
    /** Rounded finite top face in the authored plate frame, not a note trigger volume. */
    fun contains(delta:MusicVector):Boolean{
        val x=abs(delta.dot(a));val y=abs(delta.dot(n.cross(a)))
        if(x>w*.5||y>d*.5)return false
        val qx=max(0.0,x-(w*.5-cornerRadius));val qy=max(0.0,y-(d*.5-cornerRadius))
        return qx*qx+qy*qy<=cornerRadius*cornerRadius+1e-18
    }
}
object MusicStrikePad {
    const val maximumWidth=.024;const val maximumDepth=.018;const val thickness=.002
    fun compact(p:MusicImpact):MusicImpact{
        val w=min(p.w,maximumWidth);val d=min(p.d,maximumDepth)
        return p.copy(w=w,d=d,cornerRadius=min(.0012,min(w*.08,d*.12)))
    }
}
class MusicRail(val after:Int,val drag:Double,val path:List<MusicVector>){
    val lengths=path.zipWithNext{a,b->(b-a).length()}
    val tangents=path.zipWithNext{a,b->(b-a).unit()}
    val low=path.minOf{it.y}.toFloat();val high=path.maxOf{it.y}.toFloat()
}
class MusicCourse(val id:Int,val runSeconds:Float,val returnOffset:Double,val lead:Double,val impacts:List<MusicImpact>,val rails:List<MusicRail>){
    val points=rails.first().path.map{it.point()}+impacts.map{it.contact}+rails.last().path.map{it.point()}
    val lengths=FloatArray(points.size-1){distance(points[it],points[it+1])}
    val distances=FloatArray(points.size).also{a->for(i in lengths.indices)a[i+1]=a[i]+lengths[i]}
    val marks=FloatArray(impacts.size){distances[rails.first().path.size+it]}
    val railAfter=rails.withIndex().associate{it.value.after to it.index}
    val name get()=MusicRouteData.names[id]
    fun positionAt(s:Float):Point{val hit=distances.binarySearch(s);val i=(if(hit>=0)hit else -hit-2).coerceIn(lengths.indices)
        val u=((s-distances[i])/lengths[i]).coerceIn(0f,1f);return points[i]*(1-u)+points[i+1]*u}
    companion object {fun distance(a:Point,b:Point)=sqrt((a.x-b.x).pow(2)+(a.y-b.y).pow(2)+(a.z-b.z).pow(2))}
}
object MusicCourses {
    private val cache=mutableMapOf<Int,MusicCourse>()
    fun get(id:Int):MusicCourse=cache.getOrPut(id){
        DataInputStream(InflaterInputStream(ByteArrayInputStream(Base64.getDecoder().decode(MusicRouteData.encoded[id])))).use{r->
            val duration=r.readDouble();val offset=r.readDouble();val lead=r.readDouble()
            fun point()=MusicVector(r.readDouble(),r.readDouble(),r.readDouble())
            val impacts=List(r.readInt()){MusicStrikePad.compact(MusicImpact(point(),point(),point(),r.readDouble(),r.readDouble(),r.readDouble()))}
            val rails=List(r.readInt()){MusicRail(r.readInt(),r.readDouble(),List(r.readInt()){point()})}
            MusicCourse(id,duration.toFloat(),offset,lead,impacts,rails)
        }
    }
}

/** Passive finite-plate collisions and gravity, with rolling constrained by a U channel.
 * No scheduled notes, target velocities, per-note scoops or in-flight correction.
 * Rail length/material determines delay. This is a reduced sphere model, not a
 * general six-degree-of-freedom rigid-body solver.
 */
class MusicSimulation(var course:MusicCourse,private val directEntry:Boolean=true){
    companion object {
        const val gravity=9.81f;const val radius=.006f;val cup=Point(-.28f,.067f,.22f)
        private val G=MusicVector(0.0,-9.81,0.0);private val ZERO=MusicVector(0.0,0.0,0.0)
        private val CUP=MusicVector(-.28,.067,.22)
        private data class Entry(val position:MusicVector,val velocity:MusicVector)
        private val entries=mutableMapOf<Int,Entry>()
        private val readingScrolls=mutableMapOf<Int,MusicReadingScroll>()
        const val resonanceSeconds=2.4f
        const val visibleTailSeconds=.12f
    }
    private var p=CUP;private var v=ZERO;private var rail=0;private var rolling=true
    private var scalarSpeed=0.0;private var along=0.0;private var clock=0.0;private var scrollY=0.0
    private val readingScroll get()=readingScrolls.getOrPut(course.id){MusicReadingScroll(course)}
    private var firstContactTime=Double.NaN
    private var launchVelocity=0.0
    private var tailSeconds=0.0
    private var incomingVector=ZERO
    /** Read the manufactured entrance once, then retain its exact first impact state.
     * Reverse free-fall to a nearby apex, removing only the launch/lead-in hardware.
     * No later impact is steered or synchronized to a music clock. */
    private fun entry():Entry=entries.getOrPut(course.id){
        val probe=MusicSimulation(course,false);probe.place()
        repeat(480*10){if(probe.noteIndex<0&&!probe.missed)probe.step(1f/480)}
        check(probe.noteIndex==0&&!probe.missed){"Invalid manufactured entrance ${course.id}"}
        val incoming=probe.incomingVector
        val lead=min(.16,-incoming.y/9.81)
        check(lead>0){"Entrance must descend ${course.id}"}
        Entry(probe.impactPositionVector-incoming*lead+G*(.5*lead*lead),incoming-G*lead)
    }
    private var impactPositionVector=CUP
    val entryBall get()=(entry().position+MusicVector(0.0,readingScroll.initial,0.0)).point()
    val physicalBall get()=p.point()
    val ball get()=if(!started&&directEntry)entryBall else if(failedLaunch)cup else (p+MusicVector(0.0,scrollY,0.0)).point()
    val scroll get()=scrollY.toFloat()
    val velocity get()=v.point()
    val speed get()=if(rolling)scalarSpeed.toFloat() else v.length().toFloat()
    val time get()=clock.toFloat()
    var segment=0;private set
    val offset get()=along.toFloat()
    var roll=0f;private set
    var noteIndex=-1;private set
    val springCount=0
    var completed=false;private set
    var launching=false;private set
    var started=false;private set
    var failedLaunch=false;private set
    var missed=false;private set
    var failureReason="";private set
    var impactTime=-100f;private set
    var incomingNormalSpeed=0f;private set
    var outgoingNormalSpeed=0f;private set
    var impactIncoming=Point(0f,0f,0f);private set
    var impactOutgoing=Point(0f,0f,0f);private set
    var impactPosition=cup;private set
    var physicalImpactCount=0;private set
    private var plateImpactCounts=IntArray(course.impacts.size)
    fun physicalImpactsPerPlate()=plateImpactCounts.copyOf()
    var maxCatchCorrection=0f;private set
    var maxCaptureAngle=0f;private set
    var travelled=0f;private set
    val flying get()=started&&!rolling&&!launching&&!completed&&!missed&&!finishing
    val fallingToPlate get()=flying&&v.y<0
    val returning get()=!directEntry&&noteIndex==course.impacts.lastIndex
    val finishing get()=directEntry&&noteIndex==course.impacts.lastIndex
    // The low steel rebound disappears before its continuing physical descent
    // enters the inclined score. Position/velocity and resonance are not clamped.
    val ballVisibility get()=if(completed||missed)0f else if(finishing)(1f-(tailSeconds/visibleTailSeconds).toFloat()).coerceIn(0f,1f) else 1f
    val events=ArrayDeque<Int>()
    init {if(directEntry){p=entry().position;scrollY=readingScroll.initial}}
    fun reset(){p=if(directEntry)entry().position else CUP;v=ZERO;rail=0;rolling=true;scalarSpeed=0.0;along=0.0;clock=0.0;scrollY=if(directEntry)readingScroll.initial else 0.0;firstContactTime=Double.NaN;segment=0;roll=0f;noteIndex=-1;completed=false;launching=false;started=false;failedLaunch=false;missed=false;impactTime=-100f;tailSeconds=0.0;maxCatchCorrection=0f;maxCaptureAngle=0f;travelled=0f;physicalImpactCount=0;plateImpactCounts=IntArray(course.impacts.size);events.clear()}
    fun place(){reset();started=true;if(directEntry){val start=entry();p=start.position;v=start.velocity;rolling=false}else p=course.rails.first().path.first()}
    // Kept for historical debug callers; the current instrument has no launcher.
    fun launch(@Suppress("UNUSED_PARAMETER") power:Float){place()}
    /** A perturbation can miss the next finite plate; the score never corrects it. */
    fun impulse(metresPerSecond:Float){if(rolling)scalarSpeed=max(0.0,scalarSpeed+metresPerSecond) else v=v+v.unit()*metresPerSecond.toDouble()}
    fun sidewaysImpulse(x:Double,z:Double){if(!rolling)v=v+MusicVector(x,0.0,z)}
    fun step(dt:Float){
        if(!started||completed||missed)return
        var left=dt.toDouble()
        while(left>1e-10&&!completed&&!missed){val h=min(left,1.0/480.0);advance(h);left-=h}
        // The reading camera is driven by the authored phrase, NOT p.y. A missed
        // or perturbed ball remains visibly off course instead of being followed.
        if(directEntry){
            if(!firstContactTime.isNaN())scrollY=readingScroll.at(clock-firstContactTime)
        } else if(!launching&&!failedLaunch&&!finishing)scrollY=max(scrollY,min(course.returnOffset,max(0.0,.44-p.y)))
    }
    private fun advance(dt:Double){
        if(finishing){
            val motion=min(dt,max(0.0,.22-tailSeconds))
            p=p+v*motion+G*(.5*motion*motion);v=v+G*motion
            tailSeconds+=dt;clock+=dt
            if(tailSeconds>=resonanceSeconds){completed=true;v=ZERO}
            return
        }
        if(launching){
            p=p+MusicVector(0.0,launchVelocity*dt-4.905*dt*dt,0.0);launchVelocity-=9.81*dt;clock+=dt
            if(p.y>=course.rails.first().path.first().y){p=course.rails.first().path.first();launching=false;scalarSpeed=0.0;clock=0.0}
            else if(p.y<=CUP.y&&launchVelocity<0){p=CUP;launching=false;failedLaunch=true;completed=true}
            return
        }
        var left=dt;var transitions=0
        while(left>1e-11&&!completed&&!missed&&transitions++<64){
            if(finishing){advance(left);left=0.0;break}
            val used=if(rolling)advanceRail(left) else advanceFlight(left)
            clock+=used;left-=used
        }
        if(p.y<course.rails.last().path.last().y-1.0)missed=true
    }
    private fun advanceRail(dt:Double):Double{
        val r=course.rails[rail];val tangent=r.tangents[segment]
        val slope=-tangent.y;val force=9.81/1.4*(slope-.006*sqrt(max(0.0,1-slope*slope)))
        val initial=scalarSpeed;val beta=r.drag
        fun travel(t:Double)=if(beta==0.0)initial*t+.5*force*t*t else force/beta*t+(initial-force/beta)*(-expm1(-beta*t))/beta
        fun finalSpeed(t:Double)=if(beta==0.0)initial+force*t else force/beta+(initial-force/beta)*exp(-beta*t)
        val remaining=r.lengths[segment]-along;val crosses=travel(dt)>=remaining;var used=dt
        if(crosses){var lo=0.0;var hi=dt;repeat(36){val mid=(lo+hi)*.5;if(travel(mid)<remaining)lo=mid else hi=mid};used=(lo+hi)*.5}
        val ds=if(crosses)remaining else max(0.0,travel(used));along+=ds;scalarSpeed=max(0.0,finalSpeed(used));v=tangent*scalarSpeed
        roll+=(ds/radius).toFloat();travelled+=ds.toFloat();p=r.path[segment]+tangent*along
        if(crosses){p=r.path[segment+1];segment++;along=0.0
            if(segment==r.lengths.size){if(r.after==course.impacts.lastIndex){completed=true;scalarSpeed=0.0;v=ZERO}
                else rolling=false /* leave with the actual tangent and speed */}}
        return used
    }
    private fun at(t:Double)=p+v*t+G*(.5*t*t)
    private fun planeHit(center:MusicVector,n:MusicVector,dt:Double):Double?{
        val c=(p-center).dot(n);val b=v.dot(n);val a=.5*G.dot(n)
        val roots=if(abs(a)<1e-12){if(abs(b)<1e-12)return null;listOf(-c/b)} else {
            val discriminant=b*b-4*a*c;if(discriminant<0)return null
            val q=sqrt(discriminant);listOf((-b-q)/(2*a),(-b+q)/(2*a))}
        return roots.filter{it>1e-9&&it<=dt+1e-10&&(v+G*it).dot(n)<0}.minOrNull()
    }
    private fun advanceFlight(dt:Double):Double{
        var hit=-1;var earliest=dt+1.0
        // All finite plates participate, including an unintended plate along a bad trajectory.
        for((i,m) in course.impacts.withIndex()){
            if(abs(p.y-m.center.y)>.04+abs(v.y)*dt)continue
            val t=planeHit(m.center,m.n,dt)?:continue
            if(t>=earliest)continue
            val delta=at(t)-m.center
            if(m.contains(delta)){earliest=t;hit=i}}
        val nextRail=course.railAfter[noteIndex]?.takeIf{it!=rail};var capture=-1
        if(nextRail!=null){val r=course.rails[nextRail];val t=planeHit(r.path.first(),r.tangents.first()*-1.0,dt)
            if(t!=null&&t<earliest&&(at(t)-r.path.first()).length()<.0002){earliest=t;capture=nextRail;hit=-1}}
        if(hit>=0){
            physicalImpactCount++;plateImpactCounts[hit]++
            p=at(earliest);v=v+G*earliest;val m=course.impacts[hit];val vn=v.dot(m.n)
            incomingVector=v;impactPositionVector=p
            impactIncoming=v.point();incomingNormalSpeed=vn.toFloat();v=v-m.n*((1+m.bounce)*vn)
            impactOutgoing=v.point();outgoingNormalSpeed=v.dot(m.n).toFloat();impactPosition=p.point();impactTime=(clock+earliest).toFloat()
            if(hit==0&&firstContactTime.isNaN())firstContactTime=clock+earliest
            if(hit==noteIndex+1){noteIndex=hit;events.addLast(hit)}else {missed=true;failureReason="hit=$hit expected=${noteIndex+1} position=$p"}
            return earliest}
        if(capture>=0){
            p=at(earliest);v=v+G*earliest;val r=course.rails[capture];val tangent=r.tangents.first()
            val correction=(p-r.path.first()).length();val angle=acos((v.unit().dot(tangent)).coerceIn(-1.0,1.0))
            maxCatchCorrection=max(maxCatchCorrection,correction.toFloat());maxCaptureAngle=max(maxCaptureAngle,angle.toFloat())
            // Spin-up removes energy. An arbitrary incoming direction cannot be captured.
            if(angle>.025){missed=true;return earliest}
            scalarSpeed=max(0.0,v.dot(tangent))/sqrt(1.4);v=tangent*scalarSpeed;p=r.path.first();rail=capture;segment=0;along=0.0;rolling=true
            return earliest}
        p=at(dt);v=v+G*dt;return dt
    }
}
