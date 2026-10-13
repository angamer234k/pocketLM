package com.angamer.pocketlm

import android.app.Application
import com.angamer.pocketlm.model.ModelDownloads

class PocketLMApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ModelDownloads.ensureReceiver(this)
        ModelDownloads.modelsDir(this)
    }
}
