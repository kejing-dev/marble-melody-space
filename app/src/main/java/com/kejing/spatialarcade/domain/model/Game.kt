package com.kejing.spatialarcade.domain.model

data class Point(val x:Float,val y:Float,val z:Float) {
    operator fun plus(p:Point)=Point(x+p.x,y+p.y,z+p.z)
    operator fun times(s:Float)=Point(x*s,y*s,z*s)
}
enum class GameKind(val title:String,val english:String,val asset:String) {
    A("弧线任意球","CURVE / FREE KICK","A-free-kick.glb"),
    B("口袋宇宙弹球","ORBIT / POCKET COSMOS","B-pocket-pinball.glb"),
    C("钢球音乐盒","Marble Melody Space","C-score-music-box-v026.glb"),
    D("手势打砖块 MAX","BRICK / BREAKOUT MAX","D-breakout-max.glb")
}
data class Contact(val id:Int,val label:String,val u:Float,val v:Float,val curve:Float,val lift:Float)
data class Note(val midi:Int,val time:Float,val position:Point,val duration:Float=.5f,val spring:Boolean=false)
data class Track(val id:Int,val title:String,val subtitle:String,val assetNode:String,val notes:List<Note>) {
    val duration get()=notes.lastOrNull()?.let{it.time+it.duration}?:0f
}
data class BrickLevel(val title:String,val hint:String,val cells:List<Int>,val speed:Float)
data class KickLevel(val title:String,val hint:String,val startX:Float,val startZ:Float,val wallX:Float,val wallZ:Float,val exampleContact:Int,val examplePower:Float) {
    val defenders=4
    val keeper=false
}
data class HitEvent(val serial:Int,val position:Point,val color:Int,val destroyed:Boolean)
enum class OrbitImpactKind { RAIL, BODY, FLIPPER, COMET }
data class OrbitImpact(val serial:Int,val kind:OrbitImpactKind,val strength:Float,val x:Float)
enum class Phase { READY, HELD, PLAYING, PAUSED, RESULT }
data class GameSnapshot(val phase:Phase=Phase.READY,val score:Int=0,val lives:Int=3,val count:Int=0,
    val contact:Int=2,val power:Float=.65f,val status:String="准备好了",val remaining:Int=60,val layout:Int=0,
    val track:Int=0,val targets:Int=0,val combo:Int=0,val goalSerial:Int=0,val kickSerial:Int=0,
    val flightSeconds:Float=0f,val energy:Float=48f,val universe:Int=0,val warpCount:Int=0,
    val circuit:Int=0,val saves:Int=0,val cosmicHits:Int=0,val cometHits:Int=0,val nebulaCharge:Float=0f,
    val previousStatus:String="",val statusSerial:Int=0,
    val gravityCells:Float=10f,val gravityActive:Boolean=false,val beaconCount:Int=0,val novaSeconds:Float=0f)
