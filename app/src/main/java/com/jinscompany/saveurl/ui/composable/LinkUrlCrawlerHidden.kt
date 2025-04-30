package com.jinscompany.saveurl.ui.composable

import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.jinscompany.saveurl.domain.model.UrlData
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
    val webView = remember {
        WebView(context).apply {
            visibility = View.GONE
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.userAgentString =
                when {
                    url.contains("kko.kakao.com") -> "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
                    else -> "Mozilla/5.0 (Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.5993.90 Mobile Safari/537.36"
                }

            // 딥 링크 처리
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url.toString()
                    if (url.startsWith("coupang://")) {
                        return true // WebView에서 처리하지 않도록 return true
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

                    evaluateJavascript("(document.documentElement.outerHTML)") { html ->
                        try {
                            val realHtml = html
                                .replace("\\u003C", "<")
                                .replace("\\u003E", ">")
                                .replace("\\u0022", "\"")
                                .replace("\\", "")
                            val doc = Jsoup.parse(realHtml)

                            val title = doc.select("meta[property=og:title]").attr("content")
                            val description = doc.select("meta[property=og:description]").attr("content")
                            val imageUrl = doc.select("meta[property=og:image]").attr("content")
                            val siteName = doc.select("meta[property=og:site_name]").attr("content")

                            if (!title.isNullOrEmpty()) {
                                onSuccess(
                                    UrlData(
                                        url = url,
                                        imgUrl = imageUrl,
                                        siteName = siteName,
                                        title = title,
                                        description = description
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            onError()
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
    }
    AndroidView(factory = { webView }, update = { it.loadUrl(url) })
}