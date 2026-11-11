package com.kejing.spatialarcade.ui.feature.arcade.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kejing.spatialarcade.R
import com.kejing.spatialarcade.data.repository.ArcadeCatalog
import com.kejing.spatialarcade.domain.model.*
import com.kejing.spatialarcade.ui.feature.arcade.*
import com.pico.spatial.ui.design.*
import com.pico.spatial.ui.foundation.layout.zOffset
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import kotlin.math.roundToInt

@Composable private fun Stat(icon:Int,value:String,label:String){
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
        Icon(painterResource(icon),label,Modifier.size(20.dp),tint=PicoTheme.colorScheme.labelSecondary)
        Text(value,color=PicoTheme.colorScheme.labelPrimary,style=PicoTheme.typography.bodyMedium,maxLines=1,softWrap=false)
    }
}
@Composable private fun HudIcon(icon:Int,label:String,checked:Boolean=false,onClick:()->Unit){
    ToggleIconButton(checked=checked,onCheckedChange={onClick()},modifier=Modifier.size(48.dp),size=ToggleIconButtonDefaults.toggleIconButtonSize(48.dp),
        colors=ToggleIconButtonDefaults.toggleIconButtonColors(checkedContainerColor=PicoTheme.colorScheme.fillPrimary,checkedContentColor=PicoTheme.colorScheme.labelPrimaryLight,
            uncheckedContainerColor=PicoTheme.colorScheme.fillTertiary,uncheckedContentColor=PicoTheme.colorScheme.labelPrimary)){
        Icon(painterResource(icon),label,Modifier.size(24.dp),tint=if(checked)PicoTheme.colorScheme.labelPrimaryLight else PicoTheme.colorScheme.labelPrimary)
    }
}
@Composable fun CompactHeader(kind:GameKind,state:ArcadeUiState,scene:ArcadeScene,onEvent:(ArcadeEvent)->Unit){
    val g=state.game;val en=state.english
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).backgroundMaterial(enable=true,style=Material.Regular)){
        // All dynamic glyphs share this one foreground plane, including the central prompt.
        Row(Modifier.fillMaxWidth().zOffset{6.dp.toPx()}.padding(horizontal=20.dp,vertical=14.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
            Column(Modifier.width(if(kind==GameKind.D)284.dp else 226.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                Text(when(kind){GameKind.C->ArcadeCatalog.tracks[g.track].title;GameKind.A->if(en)"Curve / Free Kick" else kind.title;else->kind.title},
                    color=PicoTheme.colorScheme.labelPrimary,style=PicoTheme.typography.titleMedium,maxLines=1,softWrap=false,overflow=TextOverflow.Ellipsis)
                Row(horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically){
                    if(kind==GameKind.C)Text("${g.count} / ${scene.vm.engine.notes.size}",color=PicoTheme.colorScheme.labelSecondary,style=PicoTheme.typography.bodyMedium)
                    else {
                        Stat(R.drawable.ic_trophy,"${g.score}",if(en)"Score" else "得分")
                        Stat(R.drawable.ic_grid,"${g.layout+1}/${if(kind==GameKind.A)8 else 6}",if(en)"Level" else "关卡")
                        if(kind==GameKind.D)Stat(R.drawable.ic_ball,"${g.lives}","剩余球数")
                    }
                }
            }
            Column(Modifier.weight(1f).height(62.dp).clipToBounds(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
                Text(if(kind==GameKind.A&&en)CurveCopy.status(g) else g.status,
                    modifier=Modifier.fillMaxWidth(),color=PicoTheme.colorScheme.labelPrimary,style=PicoTheme.typography.bodyMedium,
                    textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            if(kind==GameKind.D)Stat(R.drawable.ic_paddle,"${(scene.vm.engine.paddleScale*100).roundToInt()}%","挡板长度")
            if(kind==GameKind.C)HudIcon(R.drawable.ic_album,"选择专辑",state.library){onEvent(ArcadeEvent.OpenLibrary)}
            if(kind==GameKind.A)HudIcon(R.drawable.ic_language,if(en)"切换到中文" else "Switch to English",en){onEvent(ArcadeEvent.ToggleLanguage)}
            HudIcon(if(state.muted)R.drawable.ic_sound_off else R.drawable.ic_sound_on,
                if(en){if(state.muted)"Unmute" else "Mute"}else{if(state.muted)"取消静音" else "静音"},state.muted){onEvent(ArcadeEvent.ToggleSound)}
        }
    }
}
