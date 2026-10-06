package com.jinscompany.saveurl.linkpreview

import com.jinscompany.saveurl.data.source.linkpreview.HttpGetter
import com.jinscompany.saveurl.data.source.linkpreview.HttpResult
import com.jinscompany.saveurl.data.source.linkpreview.LinkPreviewFetcher
import com.jinscompany.saveurl.data.source.linkpreview.UaProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 가짜 HttpGetter 로 리다이렉트/UA 전략/폴백 흐름을 검증 (네트워크 없음) */
class LinkPreviewFetcherTest {

    private val deviceUa = "Mozilla/5.0 (Linux; Android 14; SM-S918N; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/129.0.0.0 Mobile Safari/537.36"

    private fun fixture(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("linkpreview/$name")!!.use { it.readBytes() }

    private fun html(body: ByteArray, status: Int = 200, ct: String = "text/html; charset=utf-8") =
        HttpResult(status, ct, null, body)

    private fun html(body: String, status: Int = 200) = html(body.toByteArray(), status)
    private fun redirect(to: String, status: Int = 302) = HttpResult(status, "text/html", to, ByteArray(0))

    /** 요청 기록용 가짜 HTTP */
    private class FakeHttp(val handler: (url: String, ua: String) -> HttpResult) : HttpGetter {
        val requests = mutableListOf<Pair<String, String>>()
        override fun get(url: String, userAgent: String, cookies: Map<String, String>): HttpResult {
            requests += url to userAgent
            return handler(url, userAgent)
        }
    }

    private val blocked = "<html><head><title>Access Denied</title></head></html>"

    @Test
    fun `naver_me 단축 URL - 302 를 따라가 블로그를 모바일 URL 로 재작성`() {
        val http = FakeHttp { url, _ ->
            when (url) {
                "https://naver.me/5abcDEF" -> redirect("https://blog.naver.com/amazing_joy/224419427741")
                "https://m.blog.naver.com/amazing_joy/224419427741" -> html(fixture("naver_blog_mobile.html"))
                "https://blog.naver.com/amazing_joy/224419427741" -> html(fixture("naver_blog_desktop_redirect.html"))
                else -> html("", 404)
            }
        }
        val meta = LinkPreviewFetcher(http, deviceUserAgent = { deviceUa }).fetch("https://naver.me/5abcDEF")
        assertTrue(meta.isGood)
        assertTrue(meta.title!!.startsWith("성수동 맛집"))
        // PC 블로그(iframe 껍데기)는 요청하지 않음
        assertFalse(http.requests.any { it.first == "https://blog.naver.com/amazing_joy/224419427741" })
    }

    @Test
    fun `리다이렉트가 평문 http 를 가리켜도 https 로 요청 (cleartext 차단 회피)`() {
        val http = FakeHttp { url, _ ->
            when (url) {
                "https://www.11st.co.kr/products/1" -> redirect("http://m.11st.co.kr/products/ma/1")
                "https://m.11st.co.kr/products/ma/1" -> html("""<html><head><meta property="og:title" content="11번가 상품"></head></html>""")
                else -> html("", 404)
            }
        }
        val meta = LinkPreviewFetcher(http, deviceUserAgent = { deviceUa }).fetch("https://www.11st.co.kr/products/1")
        assertEquals("11번가 상품", meta.title)
        assertEquals("11번가", meta.siteName)
        assertTrue(http.requests.none { it.first.startsWith("http://") })
    }

    @Test
    fun `인스타그램은 facebookexternalhit UA 로 요청`() {
        val http = FakeHttp { _, ua ->
            if (ua == UaProfile.FACEBOOK_BOT.fixed) html(fixture("instagram_fb.html")) else html(fixture("instagram_webview.html"))
        }
        val meta = LinkPreviewFetcher(http, deviceUserAgent = { deviceUa }).fetch("https://www.instagram.com/p/BsOGulcndj-/?igsh=xyz")
        assertTrue(meta.isGood)
        assertEquals(UaProfile.FACEBOOK_BOT.fixed, http.requests.first().second)
        assertEquals(1, http.requests.size)
    }

    @Test
    fun `스마트스토어 - 주 전송계층이 로그인으로 튕기면 보조 전송계층으로 재시도`() {
        val primary = FakeHttp { url, _ ->
            if (url.startsWith("https://nid.naver.com")) html("<html><head><title>NAVER 로그인</title></head></html>")
            else redirect("https://nid.naver.com/nidlogin.login?url=x")
        }
        val fallback = FakeHttp { url, ua ->
            when {
                url == "https://smartstore.naver.com/s/products/1" -> redirect("https://m.smartstore.naver.com/s/products/1")
                ua == deviceUa -> html("""<html><head><meta property="og:title" content="쿨매트 : 편한잠"><meta property="og:image" content="https://shop-phinf.pstatic.net/a.jpg"></head></html>""")
                else -> html(blocked, 429)
            }
        }
        val meta = LinkPreviewFetcher(primary, fallback, deviceUserAgent = { deviceUa }).fetch("https://smartstore.naver.com/s/products/1")
        assertEquals("쿨매트 : 편한잠", meta.title)
        assertEquals("네이버 스마트스토어", meta.siteName)
        assertTrue(primary.requests.all { it.second == deviceUa })
    }

    @Test
    fun `모든 시도가 차단되면 제목 없이 반환 (호출부가 WebView 폴백)`() {
        val http = FakeHttp { _, _ -> html(blocked, 403) }
        val meta = LinkPreviewFetcher(http, deviceUserAgent = { deviceUa }).fetch("https://www.coupang.com/vp/products/1")
        assertFalse(meta.hasTitle)
        assertEquals("쿠팡", meta.siteName)
    }

    @Test
    fun `차단 페이지의 title 은 결과로 쓰지 않음`() {
        val http = FakeHttp { _, _ -> html(fixture("gmarket_block.html"), 403) }
        val meta = LinkPreviewFetcher(http, deviceUserAgent = { deviceUa }).fetch("https://item.gmarket.co.kr/Item?goodscode=1")
        assertFalse(meta.hasTitle)
    }

    @Test
    fun `YouTube 는 oEmbed 를 먼저 사용`() {
        val http = FakeHttp { url, _ ->
            if (url.startsWith("https://www.youtube.com/oembed")) HttpResult(200, "application/json", null, fixture("youtube_oembed.json"))
            else html("", 500)
        }
        val meta = LinkPreviewFetcher(http, deviceUserAgent = { deviceUa }).fetch("https://youtu.be/dQw4w9WgXcQ?si=abc")
        assertEquals("oembed", meta.method)
        assertTrue(meta.title!!.startsWith("Rick Astley"))
        assertEquals(1, http.requests.size)
    }

    @Test
    fun `단축 URL 이 TikTok 영상으로 풀리면 oEmbed 로 전환`() {
        val http = FakeHttp { url, _ ->
            when {
                url == "https://vt.tiktok.com/ZSabc/" -> redirect("https://www.tiktok.com/@scout2015/video/6718335390845095173?_r=1")
                url.startsWith("https://www.tiktok.com/oembed") -> HttpResult(200, "application/json", null, fixture("tiktok_oembed.json"))
                else -> html("<html><head><title>TikTok - Make Your Day</title></head></html>")
            }
        }
        val meta = LinkPreviewFetcher(http, deviceUserAgent = { deviceUa }).fetch("https://vt.tiktok.com/ZSabc/")
        assertEquals("oembed", meta.method)
        assertEquals("TikTok", meta.siteName)
    }

    @Test
    fun `EUC-KR 응답도 정상 디코딩`() {
        val http = FakeHttp { _, _ -> HttpResult(200, "text/html", null, fixture("humoruniv_euckr.html")) }
        val meta = LinkPreviewFetcher(http, deviceUserAgent = { deviceUa }).fetch("https://m.humoruniv.com/board/read.html?table=dump&number=49701")
        assertEquals("오랜 연애 끝에 헤어졌습니다", meta.title)
    }

    @Test
    fun `이미지 등 HTML 이 아닌 응답`() {
        val http = FakeHttp { _, _ -> HttpResult(200, "image/jpeg", null, ByteArray(10)) }
        val meta = LinkPreviewFetcher(http, deviceUserAgent = { deviceUa }).fetch("https://ex.com/photo/cat.jpg")
        assertEquals("cat.jpg", meta.title)
        assertEquals("https://ex.com/photo/cat.jpg", meta.imageUrl)
    }

    @Test
    fun `리다이렉트 루프는 제한 횟수에서 멈춤`() {
        val http = FakeHttp { url, _ -> redirect(if (url.endsWith("a")) "https://ex.com/b" else "https://ex.com/a") }
        val meta = LinkPreviewFetcher(http, deviceUserAgent = { deviceUa }).fetch("https://ex.com/a")
        assertFalse(meta.hasTitle)
        assertTrue(http.requests.size <= 30)
    }
}
