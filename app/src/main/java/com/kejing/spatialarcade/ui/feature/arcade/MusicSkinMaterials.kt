package com.kejing.spatialarcade.ui.feature.arcade

import com.kejing.spatialarcade.domain.usecase.MusicSkin
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.math.Color4
import kotlin.math.pow

/** Imported materials remain owned by the base Entity. No persistent/global resources.
 * The audited GLB shares book material only with its spine and has separate effect
 * materials. Steel properties are display-only; notation, controls and physics are untouched. */
class MusicSkinMaterials(nodes:Map<String,Entity>){
    private enum class Role{Book,Deck,Core,Halo,Trail,Spark}
    private data class Target(val material:PhysicallyBasedMaterial,val role:Role,val base:Color4,val emission:Color4,val metallic:Float,val roughness:Float)
    private val targets=buildList{
        for((name,entity) in nodes){
            val role=when{ name.startsWith("Moon blue silk hardcover")||name=="Supple leather spine"->Role.Book
                name=="Moon enamel playing deck"->Role.Deck
                name=="Steel ball / pick and place"->Role.Core
                name=="C Ball aura"->Role.Halo;name.startsWith("C Trail ")->Role.Trail;name.startsWith("C Spark ")->Role.Spark;else->continue }
            val component=entity.components.get(ModelComponent::class.java)?:continue
            component.materials.forEach{m->if(m is PhysicallyBasedMaterial)add(Target(m,role,m.getBaseColor(),m.getEmissiveColor(),m.getMetallic(),m.getRoughness()))}
        }
    }
    val available get()=targets.map{it.role}.toSet().containsAll(setOf(Role.Book,Role.Deck,Role.Core))
    fun apply(skin:MusicSkin):Boolean{
        if(!available)return false
        return try{
            for(t in targets){
                val rgb=when(t.role){Role.Book->skin.bookCoverRgb;Role.Deck->deckRgb(skin.family);Role.Core->0xD8DEE3;Role.Spark->0xF6D59A;else->skin.glowRgb}
                val color=linear(rgb,t.base.alpha*(if(t.role==Role.Halo).85f else 1f))
                t.material.setBaseColor(color)
                if(t.role==Role.Core){t.material.setMetallic(1f);t.material.setRoughness(.18f)}
                val strength=when(t.role){Role.Book,Role.Deck,Role.Core,Role.Halo,Role.Trail->0f;Role.Spark->.35f}
                t.material.setEmissiveColor(Color4(color.red*strength,color.green*strength,color.blue*strength,t.emission.alpha))
            }
            android.util.Log.i("ArcadeMusicLook","steel-pbr metallic=1.0 roughness=0.18 emission=0.0 opaque=true skin=${skin.id}")
            true
        }catch(_:Exception){
            // Optional tint failure cannot make the selected song unavailable.
            targets.forEach{t->runCatching{t.material.setBaseColor(t.base);t.material.setEmissiveColor(t.emission);t.material.setMetallic(t.metallic);t.material.setRoughness(t.roughness)}}
            false
        }
    }
    private fun deckRgb(family:String)=when(family){
        "champagne_classical"->0x342F2C;"starlit_fable"->0x243450
        "dream_pearl"->0x352D43;"celadon_stream"->0x203C38
        "morning_festival"->0x45382B;else->0x14283D
    }
    private fun linear(rgb:Int,alpha:Float):Color4{
        fun channel(shift:Int):Float{val v=((rgb shr shift) and 255)/255f;return if(v<=.04045f)v/12.92f else ((v+.055f)/1.055f).pow(2.4f)}
        return Color4(channel(16),channel(8),channel(0),alpha)
    }
}
