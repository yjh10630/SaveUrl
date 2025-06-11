package com.jinscompany.saveurl

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.jinscompany.saveurl.utils.CmLog
import com.jinscompany.saveurl.utils.isDebuggable
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(this@SaveUrlApplication) {
                CmLog.d("MobileAds initialize")
            }
        }
    }
}