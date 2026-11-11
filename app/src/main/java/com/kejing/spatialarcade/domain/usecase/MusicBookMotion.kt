package com.kejing.spatialarcade.domain.usecase

/** Display-only hinge, advanced by ECS time even when the song is paused. */
class MusicBookMotion {
    var openFraction=1f
        private set
    val fullyOpen get()=openFraction==1f
    val settled get()=openFraction==0f||openFraction==1f
    val closedAmount get()=(1f-openFraction).let{it*it*(3f-2f*it)}
    fun update(dt:Float,open:Boolean){
        if(!dt.isFinite()||dt<=0f)return
        openFraction=(openFraction+if(open)dt/.48f else -dt/.48f).coerceIn(0f,1f)
    }
}
