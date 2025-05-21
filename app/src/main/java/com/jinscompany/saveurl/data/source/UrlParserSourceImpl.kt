package com.jinscompany.saveurl.data.source

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.utils.CmLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import org.apache.commons.lang3.StringEscapeUtils
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import javax.inject.Inject
import kotlin.coroutines.resume

class UrlParserSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : UrlParserSource {
    val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.157 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Version/16.3 Safari/537.36",
        "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_1 like Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/537.36",
    )

    override suspend fun jsoupUrlParser(url: String): UrlData = withContext(Dispatchers.IO) {
        lateinit var data: UrlData
        try {
            val response = Jsoup.connect(url).followRedirects(true).execute().url().toExternalForm()
            val document = Jsoup.connect(response).timeout(30000).userAgent(WebSettings.getDefaultUserAgent(context))
                .referrer("https://www.google.com/")
                .ignoreHttpErrors(true)
                .ignoreContentType(true).get()
            ensureActive()
            val title = document.selectFirst("meta[property=og:title]")?.attr("content")
            val description = document.selectFirst("meta[property=og:description]")?.attr("content")
            val imageUrl = document.selectFirst("meta[property=og:image]")?.attr("content")
            val siteName = document.selectFirst("meta[property=og:site_name]")?.attr("content")

            data = UrlData(
                url = url,
                imgUrl = imageUrl ?: "",
                siteName = siteName ?: "",
                title = title ?: "",
                description = description ?: "",
            )
        } catch (e: HttpStatusException) {
            Log.e("UriParserSourceImpl", "Error > ${e.printStackTrace()}")
            val realUrl = getExceptionUrl(e.message ?: "") ?: ""
            data = if (containsSmartstore(realUrl)) {
                UrlData(
                    url = url,
                    imgUrl = "",
                    siteName = "네이버 쇼핑",
                    title = "",
                    description = realUrl,
                )
            } else {
                UrlData(
                    url = url,
                    imgUrl = "",
                    siteName = "",
                    title = "",
                    description = realUrl.ifEmpty { url },
                )
            }

        } catch (e: Exception) {
            Log.e("UriParserSourceImpl", "Error > ${e.printStackTrace()}")
            val realUrl = getExceptionUrl(e.message ?: "") ?: ""
            data = if (containsSmartstore(realUrl)) {
                UrlData(
                    url = url,
                    imgUrl = "",
                    siteName = "네이버 쇼핑",
                    title = "",
                    description = realUrl,
                )
            } else {
                UrlData(
                    url = url,
                    imgUrl = "",
                    siteName = "",
                    title = "",
                    description = realUrl.ifEmpty { url },
                )
            }
        }

        return@withContext data
    }

    fun getExceptionUrl(error: String): String? {
        val regex = Regex("URL=\\[(.*?)]")
        return regex.find(error)?.groupValues?.get(1)
    }

    fun containsSmartstore(url: String?): Boolean = url?.contains("smartstore") == true

    override suspend fun webViewGetHtml(url: String): UrlData = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            var isResumed = false  // 중복 resume 방지용 플래그

            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.userAgentString = userAgents.random()

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        evaluateJavascript("(document.documentElement.outerHTML)") { html ->
                            if (isResumed || !continuation.isActive) return@evaluateJavascript
                            isResumed = true

                            CmLog.d("${html}")
                            Readability4J(url ?: "", html).parse().let { article ->
                                CmLog.d("html: ${article.textContent}")
                                CmLog.d("url : $url")

                                val realHtml = article.content ?: ""
                                val decodedHtml = realHtml
                                    .replace("\\u003C", "<")
                                    .replace("\\u003E", ">")
                                    .replace("\\u0022", "\"")
                                    .replace("\\", "")
                                val cleanHtml = StringEscapeUtils.unescapeHtml4(decodedHtml)
                                val doc = Jsoup.parse(cleanHtml)

                                val title = doc.select("meta[property=og:title]").attr("content")
                                val imageUrl = doc.select("meta[property=og:image]").attr("content")
                                val description =
                                    doc.select("meta[property=og:description]").attr("content")
                                val descriptionSub = doc.select("title").text()

                                continuation.resume(
                                    UrlData(
                                        title = title,
                                        imgUrl = imageUrl,
                                        url = article.uri,
                                        description = "$descriptionSub $description",
                                        siteName = Uri.parse(article.uri).host ?: ""
                                    )
                                )
                            }
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