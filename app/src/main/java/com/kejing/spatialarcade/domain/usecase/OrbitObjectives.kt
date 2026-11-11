package com.kejing.spatialarcade.domain.usecase

import com.kejing.spatialarcade.domain.model.Point
import kotlin.math.hypot

/** Three spatial targets, collected once each, open an eight-second scoring window. */
class OrbitObjectives {
    companion object {
        val beacons=listOf(Point(-.098f,.075f,-.142f),Point(.087f,.075f,.057f),Point(-.026f,.075f,.083f))
        const val novaDuration=8f
    }
    var mask=0;private set
    var nova=0f;private set
    var rounds=0;private set
    var lastBeacon=-1;private set
    var flash=0f;private set
    val count get()=Integer.bitCount(mask)
    val multiplier get()=if(nova>0f)2 else 1
    fun reset(){mask=0;nova=0f;rounds=0;lastBeacon=-1;flash=0f}
    fun step(dt:Float){
        flash=(flash-dt).coerceAtLeast(0f)
        if(nova>0f){nova=(nova-dt).coerceAtLeast(0f);if(nova==0f)mask=0}
    }
    /** Non-solid sensors use the actual ball position; they never redirect its motion. */
    fun contact(ball:Point):Int {
        if(nova>0f)return 0
        for((i,p) in beacons.withIndex())if(mask and (1 shl i)==0&&hypot(ball.x-p.x,ball.z-p.z)<.017f){
            mask=mask or (1 shl i);lastBeacon=i;flash=.6f
            if(mask==7){nova=novaDuration;rounds++;return 300}
            return 40
        }
        return 0
    }
}
