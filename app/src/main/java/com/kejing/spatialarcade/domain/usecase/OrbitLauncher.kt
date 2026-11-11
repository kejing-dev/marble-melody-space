package com.kejing.spatialarcade.domain.usecase

import com.kejing.spatialarcade.domain.model.Point
import kotlin.math.*

/** A captured, one-axis spring piston. The booster is powered; the recoil is damped. */
class OrbitLauncher {
    companion object { const val stroke=.045f; const val minimumPull=.0015f }
    var displacement=0f; private set
    var velocity=0f; private set
    var firing=false; private set
    var fired=false; private set
    var serial=0; private set
    var launchSpeed=0f; private set
    var burn=0f; private set
    private var captured=false
    private var charged=0f
    val force get()=16f*displacement+1200f*displacement.pow(3)
    fun reset(){displacement=0f;velocity=0f;firing=false;fired=false;captured=false;burn=0f}
    fun grab(){reset();captured=true}
    fun pull(distance:Float){if(captured)displacement=distance.coerceIn(0f,stroke)}
    fun release(cancel:Boolean=false):Boolean {
        captured=false;charged=displacement
        firing=!cancel&&charged>=minimumPull;fired=false
        return firing
    }
    fun step(dt:Float){
        val steps=ceil(dt*480f).toInt().coerceAtLeast(1);val h=dt/steps
        repeat(steps){
            burn=(burn-h).coerceAtLeast(0f)
            if(!captured){
                val damping=if(firing).42f else 1.4f
                velocity+=(-16f*displacement-1200f*displacement.pow(3)-damping*velocity)/.075f*h
                displacement+=velocity*h
                if(firing&&displacement<=0f){
                    firing=false;fired=true;serial++;burn=.42f
                    val energy=8f*charged*charged+300f*charged.pow(4)
                    // Powered engine preload + the pull's stored spring energy.
                    launchSpeed=sqrt(.56f*.56f+2f*energy*.30f/.018f)
                    displacement=0f;velocity=-velocity*.16f
                }
                if(abs(displacement)<.00005f&&abs(velocity)<.001f){displacement=0f;velocity=0f}
            }
        }
    }
}

/** Arc-length sampling keeps the ball's speed continuous through the curved launch rail. */
object OrbitLaunchRail {
    private val points=List(301){i->val u=i/300f
        if(u<.7f)Point(.133f,.074f,.16f-u/.7f*.316f)
        else {val v=(u-.7f)/.3f;Point(.133f-.190f*v,.074f+.012f*sin(v*PI.toFloat()),-.156f-.025f*sin(v*PI.toFloat()))}}
    private val distances=FloatArray(points.size).also{d->for(i in 1 until points.size){val a=points[i-1];val b=points[i];d[i]=d[i-1]+sqrt((b.x-a.x).pow(2)+(b.y-a.y).pow(2)+(b.z-a.z).pow(2))}}
    val length get()=distances.last()
    fun position(distance:Float):Point {
        val s=distance.coerceIn(0f,length);val found=distances.binarySearch(s)
        val i=if(found>=0)found.coerceAtMost(points.lastIndex-1) else (-found-2).coerceIn(0,points.lastIndex-1)
        val u=(s-distances[i])/(distances[i+1]-distances[i]);val a=points[i];val b=points[i+1]
        return Point(a.x+(b.x-a.x)*u,a.y+(b.y-a.y)*u,a.z+(b.z-a.z)*u)
    }
}
