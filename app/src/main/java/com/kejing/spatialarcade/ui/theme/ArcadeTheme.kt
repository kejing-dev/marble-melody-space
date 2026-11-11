package com.kejing.spatialarcade.ui.theme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pico.spatial.ui.foundation.vibrant.Vibrant
import com.pico.spatial.ui.foundation.vibrant.withVibrant
import com.kejing.spatialarcade.R
import com.kejing.spatialarcade.domain.model.GameKind
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.systemColorScheme

object ArcadeColors {
    val Cream=Color(0xFFFFFFFF).withVibrant(Vibrant.None) // design-style: fixed-figma-color User-requested readable OS 7 foreground
    val Muted=Color(0xFF303030).withVibrant(Vibrant.None) // design-style: fixed-figma-color User-requested readable OS 7 secondary foreground
    val Ink=Color(0xFF111111).withVibrant(Vibrant.None) // design-style: fixed-figma-color User-requested readable OS 7 foreground
    val Preview=Color(0xFFE2E3E4) // design-style: fixed-figma-color OS 7 reference neutral UI palette; game accent used only for scene feedback
    fun accent(kind:GameKind)=when(kind){GameKind.A,GameKind.D->Color(0xFFE67A55);GameKind.B->Color(0xFF77CBBB);GameKind.C->Color(0xFF407E9D)} // design-style: fixed-figma-color OS 7 reference neutral UI palette; game accent used only for scene feedback
}
@Composable fun ArcadeTheme(kind:GameKind,content:@Composable ()->Unit){
    val system=systemColorScheme(LocalContext.current)
    // design-style: fixed-figma-color C v0.20 Moonlight modern classical approved design
    val musicInk=Color(0xFF202B47).withVibrant(Vibrant.None) // design-style: fixed-figma-color C v0.20 Moonlight ink
    val musicSecondary=Color(0xFF455471).withVibrant(Vibrant.None) // design-style: fixed-figma-color C v0.20 Moonlight secondary
    val scheme=if(kind==GameKind.C)system.copy(
        fillPrimary=Color(0xFFF3F6FF),fillSecondary=Color(0xFFE4EAF6),fillTertiary=Color(0xFFCBD5E8),fillLight=system.fillLight, // design-style: fixed-figma-color C v0.20 Moonlight ivory surfaces
        labelPrimaryLight=musicInk,labelPrimary=musicInk,labelSecondary=musicSecondary,labelTertiary=Color(0xFF697B97).withVibrant(Vibrant.None), // design-style: fixed-figma-color C v0.20 Moonlight tertiary
        labelQuaternary=system.labelQuaternary,lightenHover=system.lightenHover,lightenPressed=system.lightenPressed,
        error=system.error,alert=system.alert,passable=system.passable,interaction=Color(0xFF407E9D),dividerLine=Color(0xFFBCCAE1) // design-style: fixed-figma-color C v0.20 Moonlight champagne
    ) else system.copy(fillPrimary=ArcadeColors.Ink,fillSecondary=system.fillSecondary,
        fillTertiary=system.fillTertiary,fillLight=system.fillLight,labelPrimaryLight=ArcadeColors.Cream,
        labelPrimary=ArcadeColors.Ink,labelSecondary=ArcadeColors.Muted,labelTertiary=system.labelTertiary,
        labelQuaternary=system.labelQuaternary,lightenHover=system.lightenHover,lightenPressed=system.lightenPressed,
        error=Color(0xFFFFB4A3),alert=Color(0xFFD2AD64),passable=Color(0xFF77CBBB),interaction=ArcadeColors.Ink,dividerLine=system.dividerLine) // design-style: fixed-figma-color OS 7 reference neutral UI palette; game accent used only for scene feedback
    val font=FontFamily(Font(R.font.noto_sans_sc,FontWeight.Medium),Font(R.font.noto_sans_sc_semibold,FontWeight.SemiBold))
    val t=PicoTheme.typography
    val type=t.copy(bodyLarge=t.bodyLarge.copy(fontFamily=font,fontWeight=FontWeight.Medium),bodyLargeMultiline=t.bodyLargeMultiline.copy(fontFamily=font),
        bodyMedium=t.bodyMedium.copy(fontFamily=font,fontWeight=FontWeight.Medium),bodyMediumMultiline=t.bodyMediumMultiline.copy(fontFamily=font),
        bodySmall=t.bodySmall.copy(fontFamily=font),bodyTiny=t.bodyTiny.copy(fontFamily=font),
        displayLarge=t.displayLarge.copy(fontFamily=font),displayMedium=t.displayMedium.copy(fontFamily=font),displaySmall=t.displaySmall.copy(fontFamily=font),
        headlineLarge=t.headlineLarge.copy(fontFamily=font),headlineMedium=t.headlineMedium.copy(fontFamily=font),headlineSmall=t.headlineSmall.copy(fontFamily=font,fontWeight=FontWeight.SemiBold),
        labelLarge=t.labelLarge.copy(fontFamily=font),labelMedium=t.labelMedium.copy(fontFamily=font),labelSmall=t.labelSmall.copy(fontFamily=font),
        titleLarge=t.titleLarge.copy(fontFamily=font),titleMedium=t.titleMedium.copy(fontFamily=font),titleSmall=t.titleSmall.copy(fontFamily=font))
    PicoTheme(colorScheme=scheme,typography=type,content=content)
}
