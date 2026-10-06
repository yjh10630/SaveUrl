package com.jinscompany.saveurl.data.source

import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.jinscompany.saveurl.data.source.linkpreview.HtmlMetaExtractor
import com.jinscompany.saveurl.data.source.linkpreview.JsoupHttpGetter
import com.jinscompany.saveurl.data.source.linkpreview.OkHttpGetter
import com.jinscompany.saveurl.data.source.linkpreview.LinkPreviewFetcher
import com.jinscompany.saveurl.data.source.linkpreview.WebViewHtml
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.utils.CmLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

class UrlParserSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : UrlParserSource {

    private val httpGetter = OkHttpGetter()
    private val fallbackHttpGetter = JsoupHttpGetter()

    /**
     * 기기의 실제 WebView UA. 네이버 스마트스토어 등은 "; wv" 가 포함된 Android WebView UA 에만
     * 상품 페이지를 내려준다 (데스크톱/일반 모바일 UA 는 429 또는 로그인 리다이렉트).
     */
    private val deviceUserAgent: String by lazy {
        try {
            WebSettings.getDefaultUserAgent(context)
        } catch (e: Exception) {
            "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}; wv) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Version/4.0 Chrome/129.0.0.0 Mobile Safari/537.36"
        }
    }

    override suspend fun jsoupUrlParser(url: String): UrlData = withContext(Dispatchers.IO) {
        val ctx = coroutineContext
        val meta = try {
            LinkPreviewFetcher(
                http = httpGetter,
                fallbackHttp = fallbackHttpGetter,
                deviceUserAgent = { deviceUserAgent },
                isCancelled = { !ctx.isActive },
                log = { CmLog.d("LinkPreview $it") },
            ).fetch(url)
        } catch (e: Exception) {
            Firebase.crashlytics.recordException(e)
            null
        }
        if (meta == null || !meta.hasTitle) {
            // 제목을 못 얻으면 호출부(ViewModel)가 WebView 크롤러로 폴백한다.
            return@withContext UrlData(url = meta?.url ?: url, siteName = meta?.siteName.orEmpty(), title = "", description = url)
        }
        meta.toUrlData()
    }

    override suspend fun webViewGetHtml(url: String): UrlData = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            var isResumed = false  // 중복 resume 방지용 플래그

            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.userAgentString = WebSettings.getDefaultUserAgent(context)

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        evaluateJavascript(WebViewHtml.OUTER_HTML_JS) { raw ->
                            if (isResumed || !continuation.isActive) return@evaluateJavascript
                            isResumed = true
                            val pageUrl = url.orEmpty()
                            val meta = HtmlMetaExtractor.extract(WebViewHtml.decodeJsResult(raw), pageUrl, "webview")
                            continuation.resume(meta.toUrlData())
                        }
                    }
                }
                loadUrl(url)
            }

            // 취소 시 WebView 해제
            continuation.invokeOnCancellation {
                webView.destroy()
            }
        }
    }
}
