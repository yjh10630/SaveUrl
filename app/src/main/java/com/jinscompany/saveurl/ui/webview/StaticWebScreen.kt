package com.jinscompany.saveurl.ui.webview

import android.graphics.Bitmap
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.google.android.gms.ads.AdView
import com.jinscompany.saveurl.ui.composable.AdMobBannerAd
import com.jinscompany.saveurl.ui.composable.FullScreenLoading

@Composable
fun StaticWebScreen(navController: NavHostController, url: String) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = WebSettings.getDefaultUserAgent(context)
            settings.setSupportZoom(true)             // 줌 가능 여부
            settings.builtInZoomControls = false       // 줌 컨트롤(돋보기 버튼) 사용
            settings.displayZoomControls = false      // 줌 컨트롤 UI 숨김

            webViewClient = object: WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    isLoading = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    isLoading = false
                }
            }
        }
    }
    val adView = remember { AdView(context) }
    DisposableEffect(Unit) {
        onDispose { adView.destroy() }
    }

    Scaffold { paddingValue ->
        Column (
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValue)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.Black,
                    )
                }
            }
            AdMobBannerAd(adView = adView)
            Box( modifier = Modifier.weight(1f, true) ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { webView },
                    update = { it.loadUrl(url) }
                )
                this@Column.AnimatedVisibility(visible = isLoading, modifier = Modifier.fillMaxSize()) {
                    FullScreenLoading()
                }
            }
        }
    }
}