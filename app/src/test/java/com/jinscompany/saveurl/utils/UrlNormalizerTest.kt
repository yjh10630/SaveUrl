package com.jinscompany.saveurl.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UrlNormalizerTest {

    private fun n(url: String) = UrlNormalizer.normalize(url)

    private fun assertSame(a: String, b: String) = assertEquals("$a <-> $b", n(a), n(b))

    @Test
    fun `추적 파라미터만 다른 링크는 같은 정규화 결과`() {
        assertSame("https://www.bbc.com/news/technology", "https://www.bbc.com/news/technology?utm_source=test")
        assertSame("https://shop.example.com/item/42", "https://shop.example.com/item/42?fbclid=abc&gclid=def")
        assertSame("https://www.instagram.com/p/C1a2B3/", "https://www.instagram.com/p/C1a2B3/?igsh=xyz")
        assertSame("https://open.spotify.com/track/abc", "https://open.spotify.com/track/abc?si=123")
        assertSame("https://ko.aliexpress.com/item/1.html", "https://ko.aliexpress.com/item/1.html?spm=a2g0o.x")
        assertSame("https://example.com/a?q=1", "https://example.com/a?q=1&_hsenc=x&mc_cid=y&msclkid=z")
    }

    @Test
    fun `scheme host www 끝 슬래시 fragment 차이 무시`() {
        assertSame("http://WWW.Example.com/path/", "https://example.com/path#frag")
        assertSame("https://example.com:443/path", "https://example.com/path")
    }

    @Test
    fun `쿼리 순서 무시`() {
        assertSame("https://example.com/s?b=2&a=1", "https://example.com/s?a=1&b=2")
    }

    @Test
    fun `유튜브 단축 URL 통일`() {
        assertEquals("https://youtube.com/watch?v=dQw4w9WgXcQ", n("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertSame("https://youtu.be/dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=xyz&feature=share")
    }

    @Test
    fun `콘텐츠가 다르면 다른 결과`() {
        assertNotEquals(n("https://www.youtube.com/watch?v=aaa"), n("https://www.youtube.com/watch?v=bbb"))
        assertNotEquals(
            n("https://www.coupang.com/vp/products/1?itemId=1&vendorItemId=2"),
            n("https://www.coupang.com/vp/products/1?itemId=1&vendorItemId=3")
        )
        assertNotEquals(n("https://n.news.naver.com/article/001/1?sid=105"), n("https://n.news.naver.com/article/001/1?sid=101"))
    }

    @Test
    fun `퍼센트 인코딩 경로와 값은 디코딩해서 비교`() {
        assertSame("https://ko.wikipedia.org/wiki/%EC%BD%94%ED%8B%80%EB%A6%B0", "https://ko.wikipedia.org/wiki/코틀린")
        assertEquals("https://example.com/s?q=가 나", n("https://example.com/s?q=%EA%B0%80%20%EB%82%98"))
    }

    @Test
    fun `URL 이 아니면 원문 반환`() {
        assertEquals("example.com/a", n("example.com/a"))
        assertEquals("", n(""))
        assertEquals("mailto:a@b.com", n("mailto:a@b.com"))
    }

    @Test
    fun `기존(v1) 구현과 같은 형식 유지`() {
        assertEquals("https://bbc.com/news/technology", n("https://www.bbc.com/news/technology/?utm_source=x&ref=y"))
        assertEquals("https://example.com/p?a=1&b=2", n("https://example.com/p?b=2&a=1&a=3"))
    }
}
