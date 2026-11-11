package com.kejing.spatialarcade.ui.feature.arcade.components

import com.kejing.spatialarcade.domain.model.Track

/** Music UI language. Score data and physical course IDs never change. */
object MusicLibraryCopy {
    private val englishTitles = listOf(
        "Ode to Joy · Complete Theme", "Twinkle, Twinkle, Little Star", "Canon in D",
        "Minuet in G", "Minuet in B-flat", "Gymnopédie No. 1", "Gymnopédie No. 2",
        "Gymnopédie No. 3", "Mozart Minuet K.2", "Of Foreign Lands and Peoples",
        "Contentment", "Träumerei", "Candour", "Arabesque", "Pastoral",
        "A Little Gathering", "Innocence", "Progress", "The Limpid Stream", "Grace"
    )
    fun title(track: Track, english: Boolean) =
        if (english) englishTitles[track.id] else track.title
    fun subtitle(track: Track, english: Boolean) =
        if (english) track.subtitle.substringBefore(" · ") + " · Complete melody" else track.subtitle
    fun preparing(english:Boolean)=if(english)"Preparing model…" else "正在准备模型…"
    fun elapsed(seconds:Long,english:Boolean)=if(english)"${seconds.coerceAtLeast(0)} s elapsed" else "已等待 ${seconds.coerceAtLeast(0)} 秒"
    fun loadError(english:Boolean)=if(english)"Couldn't load the model" else "模型加载失败"
}
