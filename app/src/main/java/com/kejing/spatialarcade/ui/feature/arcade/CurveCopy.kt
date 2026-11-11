package com.kejing.spatialarcade.ui.feature.arcade

import com.kejing.spatialarcade.domain.model.*

/** English copy belongs to presentation; switching language never changes the shot. */
object CurveCopy {
    val contacts=listOf("Centre · straight","Left · curl right","Right · curl left","High · dipping","Low · lofted",
        "High right · curl + dip","High left · curl + dip","Low right · curl + loft","Low left · curl + loft")
    fun status(g:GameSnapshot):String=when {
        g.status.startsWith("进球")->"Goal +2 · Next level"
        g.status.contains("人墙挡")->"Blocked · Try a different contact or power"
        g.status.contains("横梁")->"Too high · Reduce power or add dip"
        g.status.contains("偏出")->"Wide · Adjust power and curl"
        g.status.contains("未到球门")->"Short or too much curl · Adjust power"
        g.phase==Phase.HELD->"Pull to charge · Release to shoot"
        g.phase==Phase.PLAYING->"Shot in flight"
        else->"Pull the boot · Release to shoot"
    }
}
