package com.jinscompany.saveurl.ui.composable

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.jinscompany.saveurl.BuildConfig
import com.jinscompany.saveurl.MainActivity
import com.jinscompany.saveurl.SharedViewModel

@Composable
fun AdMobBannerAd(
    sharedViewModel: SharedViewModel = hiltViewModel(LocalActivity.current as MainActivity)
) {
    val isAdsRemoved by sharedViewModel.isAdsRemoved.collectAsState()
    if (isAdsRemoved) return

    val context = LocalContext.current
    val adView = remember {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = if (BuildConfig.DEBUG) BuildConfig.AdMobBannerIdDubug else BuildConfig.AdMobBannerUnitId
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(Unit) {
        onDispose { adView.destroy() }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        factory = { adView }
    )
}
