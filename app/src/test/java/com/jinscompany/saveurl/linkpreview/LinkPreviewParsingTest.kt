package com.jinscompany.saveurl.linkpreview

import com.jinscompany.saveurl.data.source.linkpreview.CharsetDecoder
import com.jinscompany.saveurl.data.source.linkpreview.ClientRedirectDetector
import com.jinscompany.saveurl.data.source.linkpreview.HtmlMetaExtractor
import com.jinscompany.saveurl.data.source.linkpreview.LinkFetchRules
import com.jinscompany.saveurl.data.source.linkpreview.OEmbed
import com.jinscompany.saveurl.data.source.linkpreview.UaProfile
import com.jinscompany.saveurl.data.source.linkpreview.WebViewHtml
import com.jinscompany.saveurl.utils.extractUrlFromText
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 네트워크 없이 저장된 HTML 픽스처로 파싱 헬퍼를 검증한다. */
class LinkPreviewParsingTest {

    private fun bytes(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("linkpreview/$name")!!.use { it.readBytes() }

    private fun text(name: String): String = String(bytes(name), Charsets.UTF_8)

    // ---------------------------------------------------------------- URL 재작성 / 정규화

    @Test
    fun `네이버 블로그 PC URL 은 모바일 단일 페이지로 재작성`() {
        val expected = "https://m.blog.naver.com/amazing_joy/224419427741"
        assertEquals(expected, LinkFetchRules.rewrite("https://blog.naver.com/amazing_joy/224419427741"))
        assertEquals(expected, LinkFetchRules.rewrite("https://blog.naver.com/PostView.naver?blogId=amazing_joy&logNo=224419427741&redirect=Dlog"))
        assertEquals(expected, LinkFetchRules.rewrite("https://m.blog.naver.com/PostView.naver?blogId=amazing_joy&logNo=224419427741&proxyReferer="))
        assertEquals(expected, LinkFetchRules.rewrite("http://blog.naver.com/amazing_joy?Redirect=Log&logNo=224419427741"))
        // 블로그 홈은 그대로
        assertEquals("https://blog.naver.com/amazing_joy", LinkFetchRules.rewrite("https://blog.naver.com/amazing_joy"))
    }

    @Test
    fun `쿠팡 모바일 상품 URL 은 PC 상품 URL 로 재작성`() {
        assertEquals(
            "https://www.coupang.com/vp/products/7335597976?itemId=1&vendorItemId=2",
            LinkFetchRules.rewrite("https://m.coupang.com/vm/products/7335597976?itemId=1&vendorItemId=2"),
        )
    }

    @Test
    fun `네이버 카페 모바일 URL 은 PC URL 로 재작성`() {
        assertEquals("https://cafe.naver.com/anycallusershow/5231717", LinkFetchRules.rewrite("https://m.cafe.naver.com/anycallusershow/5231717"))
        assertEquals(
            "https://cafe.naver.com/anycallusershow/5231717",
            LinkFetchRules.rewrite("https://m.cafe.naver.com/ca-fe/web/cafes/anycallusershow/articles/5231717?fromList=true"),
        )
    }

    @Test
    fun `평문 http 는 https 로 강제 (Android 9+ cleartext 차단 대응)`() {
        assertEquals("https://m.11st.co.kr/products/ma/1217141544", LinkFetchRules.rewrite("http://m.11st.co.kr/products/ma/1217141544"))
        assertEquals(
            "https://m.11st.co.kr/products/ma/1",
            LinkFetchRules.resolveLocation("https://www.11st.co.kr/products/1", "http://m.11st.co.kr/products/ma/1"),
        )
        assertEquals("https://a.com/x/y", LinkFetchRules.resolveLocation("https://a.com/x/z", "y"))
    }

    @Test
    fun `입력 정리 - 스킴 보정과 끝 구두점 제거`() {
        assertEquals("https://naver.me/abc", LinkFetchRules.normalizeInput("  naver.me/abc. "))
        assertEquals("https://youtu.be/x", LinkFetchRules.normalizeInput("https://youtu.be/x)"))
    }

    @Test
    fun `도메인별 UA 전략`() {
        assertEquals(UaProfile.FACEBOOK_BOT, LinkFetchRules.strategyFor("https://www.instagram.com/p/abc/").userAgents.first())
        assertEquals(UaProfile.FACEBOOK_BOT, LinkFetchRules.strategyFor("https://cafe.naver.com/a/1").userAgents.first())
        assertEquals(UaProfile.DEVICE_WEBVIEW, LinkFetchRules.strategyFor("https://m.smartstore.naver.com/a/products/1").userAgents.first())
        assertEquals(UaProfile.DESKTOP_CHROME, LinkFetchRules.strategyFor("https://link.coupang.com/a/xyz").userAgents.first())
        assertEquals(UaProfile.KAKAOTALK_SCRAP, LinkFetchRules.strategyFor("https://www.ssg.com/item/itemView.ssg?itemId=1").userAgents.first())
        assertEquals(UaProfile.DEVICE_WEBVIEW, LinkFetchRules.strategyFor("https://unknown.example.com/").userAgents.first())
        assertTrue(LinkFetchRules.isShortLink("https://naver.me/xYz"))
        assertFalse(LinkFetchRules.isShortLink("https://n.news.naver.com/article/1/2"))
    }

    // ---------------------------------------------------------------- JS 리다이렉트

    @Test
    fun `blog_naver_com 의 top_location_replace 리다이렉트를 감지`() {
        val html = text("naver_blog_desktop_redirect.html")
        val base = "https://blog.naver.com/amazing_joy/224419427741"
        val next = ClientRedirectDetector.find(Jsoup.parse(html, base), html, base)
        assertNotNull(next)
        assertTrue(next!!.startsWith("https://m.blog.naver.com/PostView.naver?blogId=amazing_joy&logNo=224419427741"))
        assertEquals("https://m.blog.naver.com/amazing_joy/224419427741", LinkFetchRules.rewrite(next))
    }

    @Test
    fun `meta refresh 리다이렉트 감지 및 일반 페이지는 무시`() {
        val refresh = """<html><head><meta http-equiv="Refresh" content="0; url=/next?a=1"></head></html>"""
        assertEquals("https://ex.com/next?a=1", ClientRedirectDetector.find(Jsoup.parse(refresh, "https://ex.com/p"), refresh, "https://ex.com/p"))

        val normal = text("naver_blog_mobile.html")
        assertNull(ClientRedirectDetector.find(Jsoup.parse(normal, "https://m.blog.naver.com/a/1"), normal, "https://m.blog.naver.com/a/1"))
    }

    // ---------------------------------------------------------------- 메타 추출

    @Test
    fun `네이버 블로그 모바일 og 추출`() {
        val meta = HtmlMetaExtractor.extract(text("naver_blog_mobile.html"), "https://m.blog.naver.com/amazing_joy/224419427741")
        assertEquals("성수동 맛집 '한정선 찹쌀떡' 웨이팅 30분 가치있을까? 무화과요거트 내돈내산 후기", meta.title)
        assertTrue(meta.imageUrl!!.startsWith("https://blogthumb.pstatic.net/"))
        assertTrue(meta.siteName!!.contains("네이버 블로그"))
        assertTrue(meta.isGood)
    }

    @Test
    fun `네이버 카페 facebookexternalhit 응답에서 제목과 이미지`() {
        val meta = HtmlMetaExtractor.extract(text("naver_cafe_fb.html"), "https://cafe.naver.com/anycallusershow/5235658")
        assertEquals("아이폰에서 폴드 넘어가신 분 있나요?", meta.title)
        assertNotNull(meta.imageUrl)
        assertEquals("네이버 카페", meta.siteName) // og:site_name 없음 → 도메인 사전
        assertTrue(meta.isGood)
    }

    @Test
    fun `인스타그램 - 크롤러 UA 응답은 og 있음, 일반 WebView UA 응답은 제목만`() {
        val fb = HtmlMetaExtractor.extract(text("instagram_fb.html"), "https://www.instagram.com/p/BsOGulcndj-/")
        assertTrue(fb.isGood)
        assertNotNull(fb.imageUrl)
        assertEquals("Instagram", fb.siteName)

        val wv = HtmlMetaExtractor.extract(text("instagram_webview.html"), "https://www.instagram.com/p/BsOGulcndj-/")
        assertFalse(wv.isGood) // <title>Instagram</title> 만 있음 → 미리보기로 부적합
    }

    @Test
    fun `og 없이 JSON-LD Product 만 있는 페이지`() {
        val meta = HtmlMetaExtractor.extract(text("jsonld_product_no_og.html"), "https://shop.example.com/p/1")
        assertEquals("힘내바 초코 스니커즈, 480g, 1개", meta.title)
        assertEquals("https://thumbnail.example.com/remote/492x492ex/image/product.jpg", meta.imageUrl) // // → https:
        assertEquals("별점 4.6점, 리뷰 3155개", meta.description)
        assertEquals("shop.example.com", meta.siteName)
        assertTrue(meta.isGood)
    }

    @Test
    fun `twitter 카드와 상대경로 이미지 처리`() {
        val html = """<html><head><title>t</title><meta name="twitter:title" content="트위터 제목">
            <meta name="twitter:image" content="/img/a.png"><meta name="description" content="설명"></head></html>"""
        val meta = HtmlMetaExtractor.extract(html, "https://ex.co.kr/a/b")
        assertEquals("트위터 제목", meta.title)
        assertEquals("https://ex.co.kr/img/a.png", meta.imageUrl)
        assertEquals("설명", meta.description)
    }

    @Test
    fun `og_url 이 다른 도메인이면 canonical 로 쓰지 않음`() {
        val html = """<html><head><meta property="og:title" content="a"><meta property="og:url" content="https://evil.com/x"></head></html>"""
        assertEquals("https://ex.com/p", HtmlMetaExtractor.extract(html, "https://ex.com/p").url)
        val ok = """<html><head><meta property="og:title" content="a"><meta property="og:url" content="https://blog.naver.com/a/1"></head></html>"""
        assertEquals("https://blog.naver.com/a/1", HtmlMetaExtractor.extract(ok, "https://m.blog.naver.com/a/1").url)
    }

    @Test
    fun `봇 차단 및 로그인 페이지 판정`() {
        val gmarket = Jsoup.parse(text("gmarket_block.html"), "https://item.gmarket.co.kr/Item?goodscode=1")
        assertTrue(HtmlMetaExtractor.isBlockedPage(gmarket, "https://item.gmarket.co.kr/Item?goodscode=1", 200))
        val login = Jsoup.parse("<html><head><title>NAVER 로그인</title></head></html>")
        assertTrue(HtmlMetaExtractor.isBlockedPage(login, "https://nid.naver.com/nidlogin.login?url=x", 200))
        val akamai = Jsoup.parse("<HTML><HEAD><TITLE>Access Denied</TITLE></HEAD></HTML>")
        assertTrue(HtmlMetaExtractor.isBlockedPage(akamai, "https://www.coupang.com/vp/products/1", 200))
        val blog = Jsoup.parse(text("naver_blog_mobile.html"))
        assertFalse(HtmlMetaExtractor.isBlockedPage(blog, "https://m.blog.naver.com/a/1", 200))
        assertTrue(HtmlMetaExtractor.isBlockedPage(blog, "https://m.blog.naver.com/a/1", 429))
    }

    // ---------------------------------------------------------------- 문자셋

    @Test
    fun `EUC-KR 페이지 (헤더에 charset 없음, meta 로 선언)`() {
        val html = CharsetDecoder.decode(bytes("humoruniv_euckr.html"), "text/html")
        val meta = HtmlMetaExtractor.extract(html, "https://m.humoruniv.com/board/read.html?table=dump&number=49701")
        assertEquals("오랜 연애 끝에 헤어졌습니다", meta.title)
    }

    @Test
    fun `헤더는 UTF-8 이라고 거짓말하지만 실제 EUC-KR 인 페이지`() {
        val raw = "<html><head><title>한글 제목 테스트입니다 똠방각하</title></head></html>".toByteArray(CharsetDecoder.korean())
        val html = CharsetDecoder.decode(raw, "text/html; charset=UTF-8")
        assertTrue(html.contains("한글 제목 테스트입니다 똠방각하"))
    }

    @Test
    fun `ks_c_5601-1987 선언 및 maxBodySize 로 잘린 UTF-8`() {
        val euc = "<title>예스24 도서</title>".toByteArray(CharsetDecoder.korean())
        assertTrue(CharsetDecoder.decode(euc, "text/html; charset=ks_c_5601-1987").contains("예스24 도서"))

        val utf = "<title>정상 UTF-8 제목</title>".toByteArray(Charsets.UTF_8)
        val truncated = utf + "가".toByteArray(Charsets.UTF_8).copyOf(2) // 마지막 글자가 잘림
        assertTrue(CharsetDecoder.decode(truncated, null).contains("정상 UTF-8 제목"))
    }

    // ---------------------------------------------------------------- oEmbed

    @Test
    fun `oEmbed 엔드포인트 매핑`() {
        assertTrue(OEmbed.endpointFor("https://youtu.be/dQw4w9WgXcQ?si=abc")!!.endsWith("url=https%3A%2F%2Fyoutu.be%2FdQw4w9WgXcQ%3Fsi%3Dabc"))
        assertNotNull(OEmbed.endpointFor("https://www.youtube.com/shorts/tPEE9ZwTmy0"))
        assertNotNull(OEmbed.endpointFor("https://m.youtube.com/watch?v=1&t=10"))
        assertTrue(OEmbed.endpointFor("https://music.youtube.com/watch?v=1")!!.contains("www.youtube.com%2Fwatch"))
        assertNotNull(OEmbed.endpointFor("https://www.tiktok.com/@a/video/1"))
        assertNull(OEmbed.endpointFor("https://www.tiktok.com/@a"))
        assertNull(OEmbed.endpointFor("https://www.instagram.com/p/abc/"))
    }

    @Test
    fun `YouTube 와 TikTok oEmbed JSON 파싱`() {
        val yt = OEmbed.parse(text("youtube_oembed.json"), "https://youtu.be/dQw4w9WgXcQ")!!
        assertTrue(yt.title!!.startsWith("Rick Astley"))
        assertTrue(yt.imageUrl!!.contains("ytimg.com"))
        assertEquals("YouTube", yt.siteName)

        val tt = OEmbed.parse(text("tiktok_oembed.json"), "https://www.tiktok.com/@scout2015/video/6718335390845095173")!!
        assertTrue(tt.title!!.startsWith("Scramble up"))
        assertEquals("TikTok", tt.siteName)
        assertNotNull(tt.imageUrl)
        assertNull(OEmbed.parse("{\"error\":\"x\"}", "u"))
        assertNull(OEmbed.parse("not json", "u"))
    }

    // ---------------------------------------------------------------- WebView / 공유 텍스트

    @Test
    fun `evaluateJavascript 결과 JSON 문자열 디코딩`() {
        val raw = "\"\\u003Chtml>\\u003Chead>\\u003Cmeta property=\\\"og:title\\\" content=\\\"a\\\\b\\\">\\u003C/head>\\u003C/html>\""
        val html = WebViewHtml.decodeJsResult(raw)
        assertEquals("a\\b", Jsoup.parse(html).selectFirst("meta[property=og:title]")!!.attr("content"))
        assertEquals("", WebViewHtml.decodeJsResult("null"))
    }

    @Test
    fun `공유 텍스트에서 URL 추출`() {
        assertEquals(
            "https://www.tiktok.com/@scout2015/video/6718335390845095173?lang=ko&is_from_webapp=1",
            extractUrlFromText("틱톡 보기 https://www.tiktok.com/@scout2015/video/6718335390845095173?lang=ko&is_from_webapp=1 공유"),
        )
        assertEquals("https://naver.me/5abcDEF", extractUrlFromText("[네이버 지도]\n성수 맛집\nhttps://naver.me/5abcDEF"))
        assertEquals("https://n.news.naver.com/article/001/0016354857", extractUrlFromText("기사 (https://n.news.naver.com/article/001/0016354857)."))
        assertEquals("https://ko.wikipedia.org/wiki/%EB%84%A4", extractUrlFromText("https://ko.wikipedia.org/wiki/%EB%84%A4 참고"))
        assertNull(extractUrlFromText("링크 없음"))
    }
}
