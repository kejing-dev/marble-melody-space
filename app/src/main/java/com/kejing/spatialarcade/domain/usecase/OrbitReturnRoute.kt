package com.kejing.spatialarcade.domain.usecase

import com.kejing.spatialarcade.domain.model.Point
import kotlin.math.*

/** Open front gutter and its rounded elbow, in the same metres as the Blender rails. */
class OrbitReturnRoute(start:Point) {
    companion object { val dock=Point(.133f,.074f,.16f); const val duration=1.9f }
    private val points=buildList {
        val join=Point(min(start.x+.012f,.110f),.074f,.190f)
        val control=Point(start.x,.074f,.190f)
        for(i in 0..12){val t=i/12f;add(start*((1-t)*(1-t))+control*(2*t*(1-t))+join*(t*t))}
        add(Point(.110f,.074f,.190f))
        for(i in 1..16){val a=(1-i/16f)*PI.toFloat()/2;add(Point(.110f+.023f*cos(a),.074f,.167f+.023f*sin(a)))}
        add(dock)
    }
    private val distances=points.zipWithNext{a,b->sqrt((a.x-b.x).pow(2)+(a.y-b.y).pow(2)+(a.z-b.z).pow(2))}
    private val length=distances.sum()
    fun position(progress:Float):Point {
        val t=progress.coerceIn(0f,1f)
        if(t==0f)return points.first()
        if(t==1f)return dock
        var distance=length*(t*t*(3-2*t))
        for(i in distances.indices){val d=distances[i];if(distance<=d&&d>0){val u=distance/d;return points[i]*(1-u)+points[i+1]*u};distance-=d}
        return dock
    }
}
