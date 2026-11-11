package com.kejing.spatialarcade.domain.usecase

import com.kejing.spatialarcade.domain.model.Point
import com.kejing.spatialarcade.data.repository.MusicDisplayLayouts
import com.kejing.spatialarcade.data.repository.MusicLibrary
import kotlin.math.*

/** Display-only phrase magnification. Physics and MIDI never consume these values.
 * The marble and all note/rail groups share this exact uniform transform.
 * The entrance and tail retain the same zoom; there is no launch or return motion.
 */
class MusicPresentation {
    companion object {
        // v0.27 uses fewer, larger visible notes. Keep the ball/contact ratio
        // unchanged; the manufactured low arcs make room for a closer view.
        const val phraseScale=2.70f; const val bottom=.38f; const val top=.65f
        // Independent performance area above the fixed plinth, in C_Root metres.
        const val left=-.4692f;const val right=.4692f;const val back=-.4071f;const val front=.3381f
    }
    var scale=phraseScale; private set
    private val focus=Point(0f,.40f,0f)
    private var horizontal=Point(0f,0f,0f)
    private var track=-1
    private var currentNote=-1
    init {bind(0)}
    fun bind(id:Int){
        if(id==track)return
        val b=MusicDisplayLayouts.bounds[id]
        scale=minOf(phraseScale,(right-left)/(b[1]-b[0]),(front-back)/(b[3]-b[2]))
        horizontal=Point((left+right)/2-(b[0]+b[1])/2*scale,0f,(front+back)/2-(b[2]+b[3])/2*scale)
        track=id;currentNote=-1
    }
    fun update(dt:Float,physics:MusicSimulation){
        bind(physics.course.id)
        currentNote=physics.noteIndex
    }
    fun reset(id:Int=track){bind(id);currentNote=-1}
    fun map(p:Point)=focus+(p+focus*-1f)*scale+horizontal
    fun routeOffset(scroll:Float)=focus*(1f-scale)+Point(0f,scroll*scale,0f)+horizontal
    fun visible(low:Float,high:Float,scroll:Float)=map(Point(0f,high+scroll,0f)).y>bottom&&map(Point(0f,low+scroll,0f)).y<top
    /** Keep one passed note and three upcoming notes for readable anticipation.
     * Only rendering is culled; all finite physics surfaces remain present. */
    fun visibleNote(index:Int)=index in (currentNote-1)..(currentNote+3)
    fun visibleRail(after:Int)=after in (currentNote-1)..(currentNote+2)
}

/** A display-only camera path through the score, independent of the moving ball.
 * Previous scrolling subtracted the current ball height every frame, cancelling
 * gravity on screen. Here authored plate heights and score spacing form a smooth,
 * monotone reading path. The sphere, all rails, pads and effects receive this same
 * translation. Neither collision solving nor note submission consumes this path.
 * Starting the camera at the first real contact also preserves the initial drop.
 */
internal class MusicReadingScroll(course:MusicCourse) {
    companion object { const val contactHeight=.44 }
    private val times=MusicLibrary.tracks[course.id].notes.map{it.time.toDouble()}
    private val heights=course.impacts.map{contactHeight-it.center.y}
    private val slopes=DoubleArray(heights.size)
    val initial=heights.first()
    init {
        require(times.size==heights.size && times.zipWithNext().all{it.second>it.first})
        val durations=times.zipWithNext{a,b->b-a}
        val secants=heights.zipWithNext{a,b->b-a}.mapIndexed{i,d->d/durations[i]}
        // Fritsch-Carlson weighted harmonic tangents preserve monotonic motion
        // while keeping camera velocity continuous at note boundaries.
        for(i in 1 until slopes.lastIndex){
            val a=secants[i-1];val b=secants[i]
            if(a*b>0){val w1=2*durations[i]+durations[i-1];val w2=durations[i]+2*durations[i-1]
                slopes[i]=(w1+w2)/(w1/a+w2/b)}
        }
        // The book starts and ends at rest; no camera snap at the first/last hit.
        slopes[0]=0.0;slopes[slopes.lastIndex]=0.0
    }
    fun at(elapsed:Double):Double {
        if(elapsed<=times.first())return heights.first()
        if(elapsed>=times.last())return heights.last()
        val found=times.binarySearch(elapsed)
        if(found>=0)return heights[found]
        val i=-found-2;val dt=times[i+1]-times[i];val u=(elapsed-times[i])/dt
        val u2=u*u;val u3=u2*u
        return (2*u3-3*u2+1)*heights[i]+(u3-2*u2+u)*dt*slopes[i]+
            (-2*u3+3*u2)*heights[i+1]+(u3-u2)*dt*slopes[i+1]
    }
}

/** Edge latch prevents one held press or an additional hand from toggling twice. */
class PressLatch {
    var down=false;private set
    fun press(pressed:Boolean):Boolean {val rising=pressed&&!down;down=pressed;return rising}
}
