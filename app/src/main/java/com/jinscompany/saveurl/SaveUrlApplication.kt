package com.jinscompany.saveurl

import android.app.Application
import com.jinscompany.saveurl.utils.isDebuggable
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SaveUrlApplication: Application() {
    companion object {
        lateinit var INSTANCE: SaveUrlApplication
        var DEBUG: Boolean = true
    }

    init {
        INSTANCE = this
    }


    override fun onCreate() {
        super.onCreate()
        DEBUG = isDebuggable(this)
    }
}