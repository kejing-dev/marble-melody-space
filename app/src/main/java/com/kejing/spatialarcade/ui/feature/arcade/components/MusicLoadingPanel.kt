package com.kejing.spatialarcade.ui.feature.arcade.components

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.layout.zOffset
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import kotlinx.coroutines.delay

/** Honest elapsed time plus the native indeterminate animation, never a fake ETA.
 * The timer belongs to this load/track, not to the selected UI language.
 */
@Composable fun MusicLoadingPanel(track:Int,english:Boolean,modifier:Modifier=Modifier){
    var seconds by remember(track){mutableLongStateOf(0L)}
    LaunchedEffect(track){
        val start=SystemClock.elapsedRealtime()
        while(true){
            delay(250)
            seconds=(SystemClock.elapsedRealtime()-start)/1000
        }
    }
    Row(modifier.width(340.dp).clip(RoundedCornerShape(20.dp))
        .backgroundMaterial(enable=true,style=Material.Regular).padding(24.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)){
        CircularProgressIndicator(modifier=Modifier.size(36.dp).zOffset{3.dp.toPx()})
        Column(Modifier.weight(1f).zOffset{3.dp.toPx()},verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text(MusicLibraryCopy.preparing(english),color=PicoTheme.colorScheme.labelPrimary,
                style=PicoTheme.typography.bodyLarge,maxLines=1,softWrap=false)
            Text(MusicLibraryCopy.elapsed(seconds,english),color=PicoTheme.colorScheme.labelSecondary,
                style=PicoTheme.typography.bodyMedium,maxLines=1,softWrap=false)
        }
    }
}
