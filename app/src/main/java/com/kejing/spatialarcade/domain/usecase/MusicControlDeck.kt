package com.kejing.spatialarcade.domain.usecase

import com.kejing.spatialarcade.domain.model.Point

/** Model-space contract shared with the Moonlight instrument asset. */
enum class MusicControl(val center:Point) {
    SpeedDown(Point(-.160f,.079f,.320f)),
    SpeedUp(Point(-.075f,.079f,.320f)),
    VolumeDown(Point(.055f,.079f,.320f)),
    VolumeUp(Point(.140f,.079f,.320f)),
    Mute(Point(.255f,.079f,.320f));
    val capName get()="C Control $name"
    val hitName get()="Music control hit $name"
}

/** One action per physical press, independent latches for two hands. */
class MusicControlDeck {
    private val latches=MusicControl.entries.associateWith{PressLatch()}
    fun press(control:MusicControl,pressed:Boolean)=latches.getValue(control).press(pressed)
    fun down(control:MusicControl)=latches.getValue(control).down
    fun releaseAll(){latches.values.forEach{it.press(false)}}
}
