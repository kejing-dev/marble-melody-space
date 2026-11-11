package com.kejing.spatialarcade.data.repository
import com.kejing.spatialarcade.domain.model.*

interface GameRepository { fun contacts():List<Contact>; fun score():List<Note> }
class LocalGameRepository:GameRepository {
    override fun contacts()=listOf(
        Contact(0,"直球",0f,0f,0f,0f),Contact(1,"右弧线",-.48f,0f,.085f,0f),
        Contact(2,"左弧线",.48f,0f,-.085f,0f),Contact(3,"下坠球",0f,.48f,0f,-.045f),
        Contact(4,"上飘球",0f,-.48f,0f,.045f),
        Contact(5,"右上 · 左弧下坠",.48f,.48f,-.065f,-.032f),
        Contact(6,"左上 · 右弧下坠",-.48f,.48f,.065f,-.032f),
        Contact(7,"右下 · 左弧上飘",.48f,-.48f,-.065f,.032f),
        Contact(8,"左下 · 右弧上飘",-.48f,-.48f,.065f,.032f))
    override fun score():List<Note> = ArcadeCatalog.tracks[0].notes
}
