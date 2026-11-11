package com.kejing.spatialarcade.domain.usecase

import com.kejing.spatialarcade.domain.model.Note

/** Read-only notation projection. Actual collision events decide the active note. */
object MusicScoreDisplay {
    // Keep the transparent score readable in every playback state.
    const val readingDegrees=20f
    const val rows=3
    const val columns=8
    const val slots=rows*columns
    const val firstX=-.205f
    const val columnGap=.061f
    const val pitchStep=.0045f
    fun row(slot:Int)=slot.coerceAtLeast(0)%slots/columns
    fun column(slot:Int)=slot.coerceAtLeast(0)%columns
    // Local coordinates under the asset's horizontal Score floor root.
    // Its X=-90 degree rotation maps positive notation Y towards the back (-Z).
    fun rowOrigin(slot:Int)=when(row(slot)){0->.119f;1->-.022f;else->-.163f}
    const val noteDepth=.0015f
    const val sharpDepth=.002f
    const val cursorDepth=.003f
    fun x(slot:Int)=firstX+column(slot)*columnGap
    fun pitchY(slot:Int,midi:Int)=rowOrigin(slot)+staffStep(displayMidi(midi))*pitchStep
    enum class Tone(val nodeState:String,val gold:Boolean){Upcoming("normal",false),Current("active",true),Played("past",true)}
    fun tone(index:Int,contact:Int)=when{index==contact->Tone.Current;index<contact->Tone.Played;else->Tone.Upcoming}
    fun octaveRaised(midi:Int)=midi>88
    fun displayMidi(midi:Int)=if(octaveRaised(midi))midi-12 else midi
    fun pageStart(contact:Int)=contact.coerceAtLeast(0)/slots*slots
    fun staffStep(midi:Int):Int {
        val degree=intArrayOf(0,0,1,1,2,3,3,4,4,5,5,6)[Math.floorMod(midi,12)]
        return Math.floorDiv(midi,12)*7+degree-37 // E4 = bottom staff line.
    }
    fun sharp(midi:Int)=Math.floorMod(midi,12) in listOf(1,3,6,8,10)
    fun sweep(notes:List<Note>,contact:Int,elapsedSinceImpact:Float):Float {
        if(contact<0||notes.isEmpty())return 0f
        val next=(contact+1).coerceAtMost(notes.lastIndex)
        val duration=if(next>contact)notes[next].time-notes[contact].time else notes[contact].duration
        val progress=if(duration>0f)(elapsedSinceImpact/duration).coerceIn(0f,1f) else 0f
        // Never sweep diagonally across rows or advance notation before a contact.
        // The last column holds until the physical impact on the following row.
        return (column(contact)+progress).coerceAtMost((columns-1).toFloat())
    }
}
