package com.kejing.spatialarcade

import com.kejing.spatialarcade.domain.model.GameKind
import com.kejing.spatialarcade.ui.theme.ArcadeTheme
import com.kejing.spatialarcade.ui.feature.arcade.ArcadeScreen
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultWindowContainer {
            ArcadeTheme(GameKind.valueOf(BuildConfig.GAME)) {
                ArcadeScreen(GameKind.valueOf(BuildConfig.GAME))
            }
        }
    }
