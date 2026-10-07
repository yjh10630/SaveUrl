package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.jinscompany.saveurl.BuildConfig
import kotlinx.coroutines.delay

/** 창 크기가 연속으로 바뀌는 동안(멀티윈도우 드래그 등) 광고를 매번 다시 만들지 않도록 기다리는 시간 */
private const val AD_WIDTH_DEBOUNCE_MS = 300L

/**
 * 앵커드 적응형 배너. 실제로 쓸 수 있는 폭(dp)에 맞춘 [AdSize] 로 광고를 요청한다.
 *
 * - 폭이 바뀌면(폴드 접기·펼치기, 회전, 멀티윈도우) 잠시 기다린 뒤 그 폭으로 AdView 를 새로 만든다.
 *   AdView 는 폭마다 [key] 블록 안의 AndroidView factory 에서 새로 만들고, adUnitId 는 생성 직후 한 번만 설정한다.
 *   ("The ad unit ID can only be set once" 크래시 방지 — 같은 AdView 에 다시 설정하는 경로가 없다)
 * - 이전 AdView 는 AndroidView 가 컴포지션에서 빠질 때 onRelease 에서 destroy 한다.
 * - 생명주기 ON_PAUSE/ON_RESUME 에 맞춰 현재 AdView 를 pause/resume 한다.
 */
@Composable
fun AdMobBannerAd() {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        val measuredWidthDp = maxWidth.value.toInt()
        if (measuredWidthDp <= 0) return@BoxWithConstraints

        // 첫 측정값은 바로 쓰고, 이후 변경은 디바운스해서 반영
        var adWidthDp by remember { mutableIntStateOf(measuredWidthDp) }
        LaunchedEffect(measuredWidthDp) {
            if (measuredWidthDp != adWidthDp) {
                delay(AD_WIDTH_DEBOUNCE_MS)
                adWidthDp = measuredWidthDp
            }
        }

        // 현재 붙어 있는 AdView (생명주기 pause/resume 대상)
        var currentAdView by remember { mutableStateOf<AdView?>(null) }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> currentAdView?.resume()
                    Lifecycle.Event.ON_PAUSE -> currentAdView?.pause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        key(adWidthDp) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    AdView(context).apply {
                        setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthDp))
                        adUnitId = if (BuildConfig.DEBUG) BuildConfig.AdMobBannerIdDubug else BuildConfig.AdMobBannerUnitId
                        loadAd(AdRequest.Builder().build())
                    }.also { currentAdView = it }
                },
                onRelease = { adView ->
                    if (currentAdView === adView) currentAdView = null
                    adView.destroy()
                },
            )
        }
    }
}
