package com.kejing.spatialarcade.domain.usecase

/** Held gravity drive: stored energy first, then exactly 50 points per extra cell. */
class GravityPulse {
    companion object { const val cost=50; const val capacity=10; const val secondsPerCell=1f; const val recharge=4f }
    enum class Result { NONE, STARTED, BOUGHT, EMPTY, UNAVAILABLE }
    var down=false;private set
    var active=false;private set
    private var stored=capacity.toDouble()
    val cells get()=stored.toFloat()
    val charge get()=cells/capacity
    var serial=0;private set // Paid cells in this round, not presses.
    private var blockedUntilRelease=false
    private fun supply(eligible:Boolean,score:Int):Result {
        if(!eligible||blockedUntilRelease){active=false;return Result.UNAVAILABLE}
        if(stored<=1e-8){
            stored=0.0
            if(score<cost){val wasActive=active;active=false;return if(wasActive)Result.EMPTY else Result.NONE}
            stored+=1.0;serial++;active=true;return Result.BOUGHT
        }
        val wasActive=active;active=true
        return if(wasActive)Result.NONE else Result.STARTED
    }
    fun press(pressed:Boolean,eligible:Boolean,score:Int):Result {
        val rising=pressed&&!down;down=pressed
        if(!pressed){active=false;blockedUntilRelease=false;return Result.NONE}
        val result=supply(eligible,score)
        return if(rising&&!active&&eligible&&!blockedUntilRelease)Result.EMPTY else result
    }
    /** Production calls this at 120 Hz; one paid cell always buys one full second. */
    fun step(dt:Float,eligible:Boolean,score:Int):Result {
        require(dt in 0f..0.1f)
        if(!down){active=false;stored=(stored+dt.toDouble()/recharge).coerceAtMost(capacity.toDouble());return Result.NONE}
        val result=supply(eligible,score)
        if(active)stored=(stored-dt.toDouble()/secondsPerCell).coerceAtLeast(0.0)
        return result
    }
    fun cancel(){active=false;blockedUntilRelease=down}
    fun reset(){stored=capacity.toDouble();active=false;down=false;serial=0;blockedUntilRelease=false}
}
