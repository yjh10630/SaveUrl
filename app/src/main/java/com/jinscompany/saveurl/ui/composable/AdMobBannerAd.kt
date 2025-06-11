package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.jinscompany.saveurl.BuildConfig

@Composable
fun AdMobBannerAd(adView: AdView) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        factory = { context ->
            adView.apply {
                this.setAdSize(AdSize.BANNER)
                adUnitId = if (BuildConfig.DEBUG) BuildConfig.AdMobBannerIdDubug else BuildConfig.AdMobBannerUnitId
                loadAd(
                    AdRequest.Builder()
                        //.addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                        .build()
                )
            }
        }
    )
}