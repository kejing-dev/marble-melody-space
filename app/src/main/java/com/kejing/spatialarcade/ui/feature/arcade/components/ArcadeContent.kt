package com.kejing.spatialarcade.ui.feature.arcade.components
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.pico.spatial.ui.foundation.layout.layout3D
import com.kejing.spatialarcade.domain.model.*
import com.kejing.spatialarcade.R
import com.kejing.spatialarcade.data.repository.ArcadeCatalog
import com.kejing.spatialarcade.domain.usecase.MusicCourses
import com.kejing.spatialarcade.ui.feature.arcade.*
import com.kejing.spatialarcade.ui.theme.ArcadeColors
import com.pico.spatial.ui.design.*
import com.pico.spatial.ui.design.windows.Toolbar
import com.pico.spatial.ui.foundation.content.*
import com.pico.spatial.ui.foundation.gesture.*
import com.pico.spatial.ui.foundation.layout.zOffset
import com.pico.spatial.ui.foundation.layout.requiredDepth
import com.pico.spatial.ui.foundation.layout.Box3D
import com.pico.spatial.ui.foundation.layout.DepthAlignment
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

@Composable private fun Copy(text:String,small:Boolean=false){Text(text,modifier=Modifier.zOffset{3.dp.toPx()},color=if(small)PicoTheme.colorScheme.labelSecondary else PicoTheme.colorScheme.labelPrimary,style=if(small)PicoTheme.typography.bodyMedium else PicoTheme.typography.bodyLarge)}
@Composable private fun Action(text:String,onClick:()->Unit){Button(onClick=onClick,colors=ButtonDefaults.buttonColors(containerColor=PicoTheme.colorScheme.fillPrimary,contentColor=PicoTheme.colorScheme.labelPrimaryLight),modifier=Modifier.height(48.dp)){Text(text,color=PicoTheme.colorScheme.labelPrimaryLight,style=PicoTheme.typography.labelMedium)}}
private fun Modifier.glass()=this.clip(RoundedCornerShape(16.dp)).backgroundMaterial(enable=true,style=Material.Regular)
private fun Modifier.musicDepthEvidence(label:String)=layout3D { measurable,constraints->
    val place=measurable.measure(constraints)
    android.util.Log.i("ArcadeDepth","$label min=${constraints.minDepth} max=${constraints.maxDepth} measured=${place.depth} size=${place.width}x${place.height}")
    layout(place.width,place.height,place.depth){place.place3D(0,0,0)}
}
private fun Modifier.musicFrontLayer()=layout3D { measurable,constraints->
    val place=measurable.measure(constraints)
    // This Volume permits minDepth=0. alignDepth alone therefore aligns in the
    // child's 8 dp, not the available 900 dp. Claim the available depth only for
    // this overlay and place its thin surface at that depth's front edge.
    val depth=constraints.maxDepth
    val frontOffset=(depth-place.depth).coerceAtLeast(0)
    android.util.Log.i("ArcadeDepth","menu-front available=$depth surface=${place.depth} offset=$frontOffset textInsetDp=3")
    layout(place.width,place.height,depth){place.place3D(0,0,frontOffset)}
}

@Composable fun ArcadeContent(kind:GameKind,state:ArcadeUiState,contacts:List<Contact>,shot:(Float,Int)->Point,scene:ArcadeScene,onEvent:(ArcadeEvent)->Unit){
    val context=LocalContext.current
    val sceneContent:@Composable ()->Unit={
    Column(Modifier.fillMaxSize().then(if(kind==GameKind.C)Modifier.musicDepthEvidence("scene") else Modifier).padding(horizontal=24.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),horizontalAlignment=Alignment.CenterHorizontally){
        if(kind==GameKind.B)OrbitHeader(state,onEvent)
        else if(kind!=GameKind.C)CompactHeader(kind,state,scene,onEvent)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()){
          val density=LocalDensity.current
          var panelX by rememberSaveable { mutableFloatStateOf(0f) }
          var panelY by rememberSaveable { mutableFloatStateOf(0f) }
          val minX=with(density){-(maxWidth-264.dp).coerceAtLeast(0.dp).toPx()}
          val limitY=with(density){((maxHeight-408.dp)/2).coerceAtLeast(0.dp).toPx()}
        Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.weight(1f).fillMaxHeight()){
                SpatialView(modifier=Modifier.fillMaxSize().pointerInput(scene){
                    // Raw press/release is for the pedal; piston motion uses the SDK's spatial drag stream.
                    if(kind==GameKind.B||kind==GameKind.C){
                        val heldPointers=mutableSetOf<PointerId>()
                        val bookPointers=mutableSetOf<PointerId>()
                        val controlPointers=mutableMapOf<PointerId,com.kejing.spatialarcade.domain.usecase.MusicControl>()
                        try{detectSpatialPointerEvent(context){events->
                            var handled=false
                            for(info in events){
                                if(kind==GameKind.C){
                                    val control=scene.musicControl(info.targetedEntity)
                                    val heldControl=controlPointers[info.pointerId]
                                    if(info.isDownEvent()&&control!=null)controlPointers[info.pointerId]=control
                                    if(!info.pressed||info.isUpEvent())controlPointers.remove(info.pointerId)
                                    handled=handled||control!=null||heldControl!=null
                                    val book=scene.isMusicBook(info.targetedEntity)
                                    scene.focusMusicBook(book)
                                    if(info.isDownEvent()&&book)bookPointers.add(info.pointerId)
                                    if(!info.pressed||info.isUpEvent())bookPointers.remove(info.pointerId)
                                    handled=handled||book||info.pointerId in bookPointers
                                }
                                val captured=info.pointerId in heldPointers
                                val pedal=if(kind==GameKind.B)scene.isGravityPedal(info.targetedEntity) else scene.isMusicButton(info.targetedEntity)
                                if(info.isDownEvent()&&pedal)heldPointers.add(info.pointerId)
                                if(!info.pressed||info.isUpEvent())heldPointers.remove(info.pointerId)
                                handled=handled||captured||pedal
                            }
                            if(kind==GameKind.B)scene.pressGravityPedal(heldPointers.isNotEmpty()) else scene.pressMusicButton(heldPointers.isNotEmpty())
                            if(kind==GameKind.C){
                                scene.pressMusicBook(bookPointers.isNotEmpty())
                                com.kejing.spatialarcade.domain.usecase.MusicControl.entries.forEach{scene.pressMusicControl(it,controlPointers.containsValue(it))}
                            }
                            handled
                        }}finally{if(kind==GameKind.B)scene.pressGravityPedal(false) else {scene.pressMusicButton(false);scene.pressMusicBook(false);scene.focusMusicBook(false);scene.releaseMusicControls()}}
                    }
                }.pointerInput(scene){
                    detectSpatialDragGesture(context,targetedToEntity=TargetEntity.any{when(kind){GameKind.B->scene.isOrbitLauncher(it);GameKind.C->it.getName()=="Steel ball / pick and place";GameKind.A->it.getName()=="Boot / heel grab pivot";GameKind.D->it.getName()=="Paddle / captured horizontal drag"}},
                        onDragStart={_,value->scene.grab(value.targetEntity)},
                        onDragEnd={_->scene.release(false)},onDragCancel={_->scene.release(true)}){v->scene.dragPixels(v.dragAmount.x,v.dragAmount.y,v.dragAmount.z)}
                },initial={content,_->try{scene.load(content)}catch(e:CancellationException){throw e}catch(e:Exception){android.util.Log.e("Arcade","Scene load failed",e);scene.vm.error(e.message?:"模型加载失败")}})
                if(kind!=GameKind.C&&!state.ready&&state.error==null)Column(Modifier.align(Alignment.Center)){CircularProgressIndicator();Copy("正在准备模型…")}
                state.error?.let{Copy(if(kind==GameKind.C)MusicLibraryCopy.loadError(state.english) else "模型加载失败：$it")}
            }
            if(kind==GameKind.A)Spacer(Modifier.width(264.dp))
          }
          if(kind==GameKind.A)ContactPanel(state,contacts,onEvent,
              Modifier.align(Alignment.CenterEnd).offset{IntOffset(panelX.roundToInt(),panelY.roundToInt())}){dx,dy->
                  panelX=(panelX+dx).coerceIn(minX,0f);panelY=(panelY+dy).coerceIn(-limitY,limitY)
              }
        }
        if(kind==GameKind.B)Spacer(Modifier.height(12.dp))
    }
    }
    if(kind==GameKind.C){
        // Ordinary Box preserves the original scene constraints and origin.
        // Only the menu claims the available depth; scene Column stays at zero.
        Box(Modifier.fillMaxSize()){
            sceneContent()
            if(!state.ready&&state.error==null)MusicLoadingPanel(state.game.track,state.english,
                Modifier.align(Alignment.Center).musicFrontLayer().requiredDepth(8.dp))
            if(state.library)LibraryPanel(state,onEvent,Modifier.align(Alignment.Center)
                .musicDepthEvidence("menu-parent").musicFrontLayer()
                .requiredDepth(8.dp).musicDepthEvidence("menu-surface"))
        }
    }else sceneContent()
}

/** One compact game HUD combines live title, score, guidance and audio state. */
@Composable private fun OrbitHeader(state:ArcadeUiState,onEvent:(ArcadeEvent)->Unit){
    // Keep all changing glyphs on ONE elevated plane. Per-Text zOffset layers retained
    // old glyphs in the 6.1 emulator when text changed repeatedly (score and both rows).
    Box(Modifier.fillMaxWidth().glass()){
      Row(Modifier.fillMaxWidth().zOffset{3.dp.toPx()}.padding(horizontal=24.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)){
            Column(Modifier.width(200.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(GameKind.B.title,color=PicoTheme.colorScheme.labelPrimary,style=PicoTheme.typography.titleMedium,maxLines=1,softWrap=false)
                Text("${state.game.score} 分  ·  ${state.game.lives} 球",color=PicoTheme.colorScheme.labelSecondary,style=PicoTheme.typography.bodyMedium,maxLines=1,softWrap=false)
            }
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){
                Box(Modifier.fillMaxWidth().heightIn(min=28.dp).clipToBounds(),contentAlignment=Alignment.Center){
                    Text(state.game.previousStatus.ifEmpty{"准备出发"},modifier=Modifier.fillMaxWidth(),
                        color=PicoTheme.colorScheme.labelSecondary,style=PicoTheme.typography.bodyMedium,
                        textAlign=TextAlign.Center,maxLines=1,softWrap=false,overflow=TextOverflow.Ellipsis)
                }
                Box(Modifier.fillMaxWidth().heightIn(min=32.dp).clipToBounds(),contentAlignment=Alignment.Center){
                    Text(if(state.game.phase==Phase.HELD)"引擎蓄能 ${(state.game.power*100).roundToInt()}% · 松手点火" else state.game.status,
                        modifier=Modifier.fillMaxWidth(),color=PicoTheme.colorScheme.labelPrimary,
                        style=PicoTheme.typography.bodyLarge,textAlign=TextAlign.Center,maxLines=1,softWrap=false,overflow=TextOverflow.Ellipsis)
                }
            }
            Column(Modifier.width(106.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text("能量 ${kotlin.math.ceil(state.game.gravityCells.toDouble()).toInt()}/10",color=PicoTheme.colorScheme.labelSecondary,style=PicoTheme.typography.bodyMedium,maxLines=1,softWrap=false)
                Text(when{state.game.gravityActive->"持续引力";state.game.gravityCells<9.999f->"松开充能";else->"缺能 50/格"},
                    color=PicoTheme.colorScheme.labelPrimary,style=PicoTheme.typography.bodyMedium,maxLines=1,softWrap=false)
            }
            ToggleIconButton(checked=state.muted,onCheckedChange={onEvent(ArcadeEvent.ToggleSound)},
                modifier=Modifier.size(48.dp),size=ToggleIconButtonDefaults.toggleIconButtonSize(48.dp),
                colors=ToggleIconButtonDefaults.toggleIconButtonColors(
                    checkedContainerColor=PicoTheme.colorScheme.fillPrimary,checkedContentColor=PicoTheme.colorScheme.labelPrimaryLight,
                    uncheckedContainerColor=PicoTheme.colorScheme.fillTertiary,uncheckedContentColor=PicoTheme.colorScheme.labelPrimary)){
                Icon(painter=painterResource(if(state.muted)R.drawable.ic_sound_off else R.drawable.ic_sound_on),
                    contentDescription=if(state.muted)"取消静音" else "静音",modifier=Modifier.size(24.dp),
                    tint=if(state.muted)PicoTheme.colorScheme.labelPrimaryLight else PicoTheme.colorScheme.labelPrimary)
            }
      }
    }
}

@Composable private fun ContactPanel(state:ArcadeUiState,contacts:List<Contact>,onEvent:(ArcadeEvent)->Unit,modifier:Modifier,onMove:(Float,Float)->Unit){
    val en=state.english
    val context=LocalContext.current
    val movePanel by rememberUpdatedState(onMove)
    val displayedPower=if(state.game.phase==Phase.READY||state.game.phase==Phase.RESULT)0f else state.game.power
    Column(modifier.width(264.dp).glass().padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)){
        Row(Modifier.fillMaxWidth().height(44.dp).pointerInput(Unit){detectSpatialDragGesture(context){movePanel(it.dragAmount.x,it.dragAmount.y)}},
            horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Text(if(en)"Contact point" else "击球点",style=PicoTheme.typography.titleMedium,color=PicoTheme.colorScheme.labelPrimary)
            Icon(painterResource(R.drawable.ic_grip),if(en)"Drag panel" else "拖动面板",modifier=Modifier.size(24.dp),tint=PicoTheme.colorScheme.labelSecondary)
        }
        Box3D(Modifier.size(220.dp),depthAlignment=DepthAlignment.DepthBack){
          Box(Modifier.size(220.dp).requiredDepth(178.dp),contentAlignment=Alignment.Center){
            SpatialModelView(source=Source.assets("A-selector-ball.glb"),resizability=Resizability.FitInside,modifier=Modifier.size(178.dp).requiredDepth(178.dp)){loading->
                when(loading){is ModelLoadingState.Success->Model(loading.model);is ModelLoadingState.Error->Copy(if(en)"Model unavailable" else "足球加载失败",true);else->CircularProgressIndicator()}
            }
            // One flat hit plane beyond the sphere. All nine targets are reachable by far ray.
            // Native Button owns hover, down/up capture, and cancellation; no depth-varying targets.
            for(p in contacts)Button(onClick={onEvent(ArcadeEvent.Select(p.id))},
                colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent),
                modifier=Modifier.offset((p.u*120).dp,(-p.v*120).dp).size(52.dp).zOffset{184.dp.toPx()}){
                val selected=p.id==state.game.contact
                val marker=if(selected)ArcadeColors.accent(GameKind.A) else PicoTheme.colorScheme.labelPrimary
                Canvas(Modifier.requiredSize(if(selected)22.dp else 14.dp)){
                    drawCircle(Color.White);drawCircle(marker,radius=size.minDimension*.32f)
                }
            }
          }
        }
        // One foreground text plane prevents stale overlapping glyphs on SpatialUI 6.1.
        Column(Modifier.fillMaxWidth().zOffset{6.dp.toPx()},verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text(if(en)CurveCopy.contacts[state.game.contact] else contacts[state.game.contact].label,
                modifier=Modifier.fillMaxWidth().heightIn(min=30.dp),color=PicoTheme.colorScheme.labelPrimary,style=PicoTheme.typography.bodyLarge,
                maxLines=1,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Text(if(en)"Power" else "力度",color=PicoTheme.colorScheme.labelSecondary,style=PicoTheme.typography.bodyMedium)
                Text("${(displayedPower*100).toInt()}%",color=PicoTheme.colorScheme.labelPrimary,style=PicoTheme.typography.bodyMedium)
            }
        }
        val accent=ArcadeColors.accent(GameKind.A)
        Canvas(Modifier.fillMaxWidth().height(6.dp)){
            drawRoundRect(Color.Black.copy(alpha=.12f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(8f))
            drawRoundRect(accent,size=androidx.compose.ui.geometry.Size(size.width*displayedPower,size.height),cornerRadius=androidx.compose.ui.geometry.CornerRadius(8f))
        }
    }
}
@Composable private fun TrajectoryPreview(preset:Int,shot:(Float,Int)->Point){
    val accent=ArcadeColors.accent(GameKind.A)
    Canvas(Modifier.fillMaxWidth().height(88.dp).background(ArcadeColors.Preview,RoundedCornerShape(12.dp))){
        fun at(t:Float):Offset {val p=shot(t,preset);return Offset(size.width*.5f+p.x*size.width*1.5f,size.height*.90f-(.18f-p.z)*size.height*1.4f)}
        for(i in 1..36){val a=shot(i/45f,preset);if(a.y>=.039f&&a.z>=-.27f)drawLine(accent,at((i-1)/45f),at(i/45f),3f)}
        drawCircle(Color.White,6f,at(0f))
    }
}
@Composable private fun LibraryPanel(state:ArcadeUiState,onEvent:(ArcadeEvent)->Unit,modifier:Modifier){
    val en=state.english
    var page by remember { mutableStateOf(state.game.track/4) }
    val pages=(ArcadeCatalog.tracks.size+3)/4
    DisposableEffect(Unit){
        android.util.Log.i("ArcadeLibrary","MOUNT host=volume-front depth=8dp textPlane=3dp track=${state.game.track}")
        onDispose{android.util.Log.i("ArcadeLibrary","DISPOSE")}
    }
    Column(modifier.width(660.dp).clip(RoundedCornerShape(24.dp)).backgroundMaterial(enable=true,style=Material.Thick)
        .onGloballyPositioned{android.util.Log.i("ArcadeLibrary","LAYOUT size=${it.size} position=${it.positionInRoot()} parentDepthAlignment=Front")}){
        // A single elevated plane for all dynamic text prevents stale glyphs.
        Column(Modifier.fillMaxWidth().zOffset{3.dp.toPx()}.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                Text(if(en)"Music Library · ${ArcadeCatalog.tracks.size}" else "典藏曲集 · ${ArcadeCatalog.tracks.size} 首",color=PicoTheme.colorScheme.labelPrimary,style=PicoTheme.typography.titleMedium)
                Action(if(en)"Close" else "关闭"){onEvent(ArcadeEvent.CloseLibrary)}
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Text("语言 / Language",modifier=Modifier.weight(1f),color=PicoTheme.colorScheme.labelSecondary,style=PicoTheme.typography.bodyMedium)
                for(english in listOf(false,true)){
                    val selected=en==english
                    Button(onClick={onEvent(ArcadeEvent.MusicLanguage(english))},
                        colors=ButtonDefaults.buttonColors(containerColor=if(selected)PicoTheme.colorScheme.fillPrimary else PicoTheme.colorScheme.fillTertiary,contentColor=PicoTheme.colorScheme.labelPrimary),
                        modifier=Modifier.width(118.dp).height(48.dp)){
                        Text((if(selected)"✓ " else "")+(if(english)"English" else "中文"),color=PicoTheme.colorScheme.labelPrimary,style=PicoTheme.typography.labelMedium)
                    }
                }
            }
            Text(if(en)"Paused · Choose a piece, then press ▶" else "演奏已暂停 · 选择曲目后，按 ▶ 开始",color=PicoTheme.colorScheme.labelSecondary,style=PicoTheme.typography.bodyMedium)
            for(track in ArcadeCatalog.tracks.drop(page*4).take(4)){
                Row(Modifier.fillMaxWidth().background(PicoTheme.colorScheme.fillTertiary,RoundedCornerShape(16.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
                    val context=LocalContext.current
                    val bitmap=remember(track.id){context.assets.open("album_${track.id.toString().padStart(2,'0')}.png").use{android.graphics.BitmapFactory.decodeStream(it).asImageBitmap()}}
                    Image(bitmap,MusicLibraryCopy.title(track,en),Modifier.size(80.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text(MusicLibraryCopy.title(track,en),color=PicoTheme.colorScheme.labelPrimary,style=PicoTheme.typography.bodyLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(MusicLibraryCopy.subtitle(track,en),color=PicoTheme.colorScheme.labelSecondary,style=PicoTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    Action(if(en){if(state.game.track==track.id)"Replay" else "Select"}else{if(state.game.track==track.id)"重选" else "选择"}){
                        android.util.Log.i("ArcadeLibrary","SELECT track=${track.id}")
                        onEvent(ArcadeEvent.Track(track.id))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                Action(if(en)"Previous" else "上一页"){page=(page-1).coerceAtLeast(0)}
                Text("${page+1} / $pages",color=PicoTheme.colorScheme.labelSecondary,style=PicoTheme.typography.bodyMedium)
                Action(if(en)"Next" else "下一页"){page=(page+1).coerceAtMost(pages-1)}
            }
            Text(if(en)"Classical scores: Mutopia · Music-box arrangements" else "古典曲谱：Mutopia · 音乐盒旋律编曲",color=PicoTheme.colorScheme.labelSecondary,style=PicoTheme.typography.bodyMedium)
        }
    }
}

