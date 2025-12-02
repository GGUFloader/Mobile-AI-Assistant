package com.example.localchatbot

import android.app.Application
import com.facebook.soloader.SoLoader

class ChatApplication : Application() {
    
    val modelRunner: ModelRunner by lazy {
        ModelRunner(this)
    }

    override fun onCreate() {
        super.onCreate()
        SoLoader.init(this, false)
    }
}
