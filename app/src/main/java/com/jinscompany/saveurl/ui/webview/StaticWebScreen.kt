package com.jinscompany.saveurl.ui.webview

import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.jinscompany.saveurl.ui.composable.AdBannerBar
import com.jinscompany.saveurl.ui.composable.AppTopBar
import com.jinscompany.saveurl.ui.theme.AppTheme

/** 사용 방법(튜토리얼) 웹뷰 (Stitch 19): ✕ 사용 방법 + 로딩 진행바 + 하단 광고 */
@Composable
fun StaticWebScreen(navController: NavHostController, url: String) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
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
            // 진행바 표시용 (표시 전용)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress = newProgress
                }
            }
        }
    }
    // 화면을 벗어나면 WebView 해제 (Activity Context 를 잡고 있어 누수 및 백그라운드 JS 실행 방지)
    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    val colors = AppTheme.colors
    Scaffold(
        containerColor = colors.background,
        bottomBar = { AdBannerBar() }
    ) { paddingValue ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(paddingValue)
        ) {
            AppTopBar(
                title = "사용 방법",
                onNavigationClick = { navController.popBackStack() },
                navigationIcon = Icons.Default.Close,
                navigationContentDescription = "닫기",
            )
            Box(modifier = Modifier.fillMaxWidth().height(3.dp)) {
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { (progress.coerceIn(5, 100)) / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = colors.accent,
                        trackColor = colors.outline,
                    )
                } else {
                    HorizontalDivider(color = colors.outline, thickness = 1.dp)
                }
            }
            AndroidView(
                modifier = Modifier.weight(1f, true).fillMaxWidth(),
                factory = { webView },
                update = { it.loadUrl(url) }
            )
        }
    }
}
