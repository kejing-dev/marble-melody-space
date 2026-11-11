package com.kejing.spatialarcade.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.kejing.spatialarcade.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
