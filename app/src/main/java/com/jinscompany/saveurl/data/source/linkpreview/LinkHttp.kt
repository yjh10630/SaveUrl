package com.jinscompany.saveurl.data.source.linkpreview

import okhttp3.OkHttpClient
import org.jsoup.Connection
import org.jsoup.Jsoup
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 네트워크 추상화 (단위 테스트에서 픽스처로 대체) */
fun interface HttpGetter {
    /** 리다이렉트를 따라가지 않고 단일 요청만 수행한다. */
    fun get(url: String, userAgent: String, cookies: Map<String, String>): HttpResult
}

class HttpResult(
    val status: Int,
    val contentType: String?,
    val location: String?,
    val body: ByteArray,
    val cookies: Map<String, String> = emptyMap(),
)

/**
 * OkHttp 기반 구현.
 *
 * jsoup 의 Jsoup.connect() 는 HttpURLConnection 을 쓰므로 HTTP/1.1 만 지원한다.
 * 네이버 스마트스토어/브랜드스토어는 HTTP/1.1 요청에 UA 와 무관하게 429 를 돌려주고
 * HTTP/2 요청에만 상품 페이지를 준다 (2026-10 curl --http1.1 vs --http2 로 확인).
 * OkHttp 는 ALPN 으로 HTTP/2 를 협상하므로 이 문제를 피할 수 있다.
 * (OkHttp 4.11 은 이미 Coil 의 의존성으로 포함되어 있어 APK 크기 증가 없음)
 */
class OkHttpGetter(
    private val maxBodyBytes: Long = 3L * 1024 * 1024,
    private val client: OkHttpClient = sharedClient,
) : HttpGetter {

    override fun get(url: String, userAgent: String, cookies: Map<String, String>): HttpResult {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
        if (cookies.isNotEmpty()) {
            builder.header("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }
        client.newCall(builder.build()).execute().use { res ->
            val body = res.body
            val bytes = if (body == null) ByteArray(0) else {
                val source = body.source()
                source.request(maxBodyBytes) // 최대 maxBodyBytes 까지만 버퍼링 (og 태그는 <head> 에 있음)
                val buffered = source.buffer
                buffered.readByteArray(minOf(buffered.size, maxBodyBytes))
            }
            val newCookies = res.headers("Set-Cookie").mapNotNull { header ->
                val pair = header.substringBefore(';')
                val idx = pair.indexOf('=')
                if (idx <= 0) null else pair.substring(0, idx).trim() to pair.substring(idx + 1).trim()
            }.toMap()
            return HttpResult(
                status = res.code,
                contentType = res.header("Content-Type"),
                location = res.header("Location"),
                body = bytes,
                cookies = newCookies,
            )
        }
    }

    companion object {
        val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .followRedirects(false)      // 리다이렉트는 LinkPreviewFetcher 가 직접 처리 (UA 유지, https 강제, URL 재작성)
                .followSslRedirects(false)
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }
}

/** jsoup(HttpURLConnection, HTTP/1.1) 기반 구현 — OkHttp 가 차단될 때의 보조 전송 계층 */
class JsoupHttpGetter(
    private val timeoutMs: Int = 10_000,
    private val maxBodyBytes: Int = 3 * 1024 * 1024,
) : HttpGetter {
    override fun get(url: String, userAgent: String, cookies: Map<String, String>): HttpResult {
        val res = Jsoup.connect(url)
            .userAgent(userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
            .cookies(cookies)
            .method(Connection.Method.GET)
            .followRedirects(false)
            .ignoreHttpErrors(true)
            .ignoreContentType(true)
            .timeout(timeoutMs)
            .maxBodySize(maxBodyBytes)
            .execute()
        return HttpResult(
            status = res.statusCode(),
            contentType = res.contentType(),
            location = res.header("Location"),
            body = res.bodyAsBytes(),
            cookies = res.cookies(),
        )
    }
}
