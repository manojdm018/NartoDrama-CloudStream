package com.manojdm018.nartodrama

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class NartoDramaPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(NartoDramaProvider())
        registerMainAPI(DramaFrenProvider())
    }
}
