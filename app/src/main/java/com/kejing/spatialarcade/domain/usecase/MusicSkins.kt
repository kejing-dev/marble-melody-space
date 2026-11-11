package com.kejing.spatialarcade.domain.usecase

/** Per-song optical data only; the skin has no authority over a physical course. */
data class MusicSkin(val trackId:Int,val id:String,val family:String,val bookCoverRgb:Int,val glowRgb:Int,val contactSeconds:Float,val sparkCount:Int)
object MusicSkins {
    val generic=MusicSkin(-1,"generic.moonlight","generic",0x354D67,0xBCE5F4,.130f,0)
    val all=listOf(
        MusicSkin(0,"morning_festival.dawn_ode","morning_festival",0x5A4E3F,0xF2DCAC,0.150f,2),
        MusicSkin(1,"starlit_fable.twinkle_sugar","starlit_fable",0x3E4B73,0xC9E8FF,0.150f,2),
        MusicSkin(2,"champagne_classical.canon_ivory","champagne_classical",0x635746,0xE4D9BE,0.180f,0),
        MusicSkin(3,"champagne_classical.minuet_azure","champagne_classical",0x45576E,0xD5E5F4,0.120f,2),
        MusicSkin(4,"champagne_classical.minuet_amber","champagne_classical",0x67513E,0xEDD6B2,0.150f,0),
        MusicSkin(5,"moonlit_crystal.gymno_silver","moonlit_crystal",0x354D67,0xBCE5F4,0.220f,1),
        MusicSkin(6,"moonlit_crystal.gymno_indigo","moonlit_crystal",0x354365,0xC3D1F5,0.210f,0),
        MusicSkin(7,"moonlit_crystal.gymno_mist","moonlit_crystal",0x3E575C,0xD0EBEA,0.220f,0),
        MusicSkin(8,"champagne_classical.mozart_pearl","champagne_classical",0x574F6B,0xE7E1F4,0.130f,2),
        MusicSkin(9,"starlit_fable.faraway_porcelain","starlit_fable",0x405A72,0xC9E0EF,0.160f,1),
        MusicSkin(10,"morning_festival.contentment_apricot","morning_festival",0x71594E,0xECD5BF,0.130f,2),
        MusicSkin(11,"dream_pearl.reverie_lilac","dream_pearl",0x5B4E6C,0xDDD3F1,0.200f,0),
        MusicSkin(12,"dream_pearl.candour_linen","dream_pearl",0x676353,0xE6E2CB,0.160f,1),
        MusicSkin(13,"dream_pearl.arabesque_smoke","dream_pearl",0x4C4D65,0xD2D4EE,0.120f,2),
        MusicSkin(14,"celadon_stream.pastoral_moss","celadon_stream",0x536850,0xD6E8CB,0.180f,1),
        MusicSkin(15,"morning_festival.gathering_rose","morning_festival",0x6C504A,0xEED4C2,0.130f,2),
        MusicSkin(16,"starlit_fable.innocence_sky","starlit_fable",0x477083,0xD6EDF6,0.140f,1),
        MusicSkin(17,"morning_festival.forward_honey","morning_festival",0x605C3D,0xE4D8A7,0.110f,2),
        MusicSkin(18,"celadon_stream.brook_aqua","celadon_stream",0x3F6864,0xBEF0E4,0.150f,0),
        MusicSkin(19,"dream_pearl.grace_blush","dream_pearl",0x715769,0xE7D4E4,0.180f,1)
    )
    fun forTrack(track:Int)=all.getOrNull(track)?:generic
    fun routeAsset(track:Int):String {
        require(track in all.indices)
        return "asset://C-route-v027-${track.toString().padStart(2,'0')}.glb"
    }
}

/** A loaded route and its skin become visible together; stale async loads cannot win. */
class MusicSkinSwitch {
    data class Ticket(val track:Int,val serial:Long)
    private var serial=0L
    private var pending:Ticket?=null
    var active=MusicSkins.generic;private set
    var appliedTrack=-1;private set
    fun begin(track:Int)=Ticket(track,++serial).also{pending=it}
    fun accepts(ticket:Ticket,selectedTrack:Int)=pending==ticket&&ticket.track==selectedTrack
    fun commit(ticket:Ticket,selectedTrack:Int,paletteAvailable:Boolean):Boolean{
        if(!accepts(ticket,selectedTrack))return false
        active=if(paletteAvailable)MusicSkins.forTrack(ticket.track) else MusicSkins.generic
        appliedTrack=ticket.track;pending=null
        return true
    }
}
