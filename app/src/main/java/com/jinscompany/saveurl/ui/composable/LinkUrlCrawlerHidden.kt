package com.jinscompany.saveurl.ui.composable

import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.jinscompany.saveurl.data.source.linkpreview.HtmlMetaExtractor
import com.jinscompany.saveurl.data.source.linkpreview.WebViewHtml
import com.jinscompany.saveurl.domain.model.UrlData
import kotlinx.coroutines.delay
import org.jsoup.Jsoup

/**
 * 링크 크롤링을 위한 히든 웹뷰
 */
@Composable
fun LinkUrlCrawlerHidden(
    url: String,
    onSuccess: (UrlData) -> Unit,
    onError: () -> Unit

) {
    val context = LocalContext.current
    val hasFinished = remember { mutableStateOf(false) }
    // og 등 메타 없이 <title> 만 있는 결과 — 타임아웃 시점에 최후의 수단으로 사용
    val titleOnlyFallback = remember { mutableStateOf<UrlData?>(null) }
    val webView = remember {
        WebView(context).apply {
            visibility = View.GONE
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.userAgentString = WebSettings.getDefaultUserAgent(context)

            // 딥 링크 처리
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url.toString()
                    // coupang://, intent://, market:// 등 앱 딥링크는 WebView 에서 처리하지 않는다
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        return true
                    }
                    return super.shouldOverrideUrlLoading(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String) {
                    // 페이지 로딩 완료 후 HTML 파싱
                    when (true) {
                        url.startsWith("https://link.coupang.com") -> {
                            return
                        }

                        else -> {}
                    }
                    if (hasFinished.value) return
                    evaluateJavascript(WebViewHtml.OUTER_HTML_JS) { raw ->
                        try {
                            val html = WebViewHtml.decodeJsResult(raw)
                            val doc = Jsoup.parse(html, url)
                            if (HtmlMetaExtractor.isBlockedPage(doc, url)) return@evaluateJavascript
                            // og → twitter → JSON-LD 순으로 추출
                            val meta = HtmlMetaExtractor.extract(doc, url, "webview")
                            if (meta.isGood) {
                                hasFinished.value = true
                                onSuccess(meta.toUrlData())
                            } else if (meta.hasTitle) {
                                titleOnlyFallback.value = meta.toUrlData()
                            }
                        } catch (e: Exception) {
                            Firebase.crashlytics.recordException(e)
                            if (!hasFinished.value) {
                                hasFinished.value = true
                                onError()
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(url) {
        if (url.isNotBlank()) {
            webView.loadUrl(url)
        }
        delay(10000)
        if (!hasFinished.value) {
            hasFinished.value = true
            val fallback = titleOnlyFallback.value
            if (fallback != null) onSuccess(fallback) else onError()
        }
    }
    AndroidView(factory = { webView }, update = {})
}