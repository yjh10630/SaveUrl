package com.jinscompany.saveurl.domain.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlExtractorTest {

    @Test
    fun `여러 링크를 순서대로 모두 찾는다`() {
        val text = "첫째 https://a.com/1 둘째 http://b.co.kr/2?x=1&y=2 끝"
        assertEquals(listOf("https://a.com/1", "http://b.co.kr/2?x=1&y=2"), UrlExtractor.extractAll(text))
    }

    @Test
    fun `문장 끝 구두점과 짝 없는 괄호는 뺀다`() {
        assertEquals(listOf("https://a.com/b"), UrlExtractor.extractAll("여기 https://a.com/b."))
        assertEquals(listOf("https://a.com/b"), UrlExtractor.extractAll("(https://a.com/b)"))
        assertEquals(listOf("https://en.wikipedia.org/wiki/Kotlin_(language)"), UrlExtractor.extractAll("https://en.wikipedia.org/wiki/Kotlin_(language)"))
        assertEquals(listOf("https://a.com/b"), UrlExtractor.extractAll("<https://a.com/b>"))
    }

    @Test
    fun `한글 경로는 슬래시 뒤일 때만 URL 로 본다`() {
        assertEquals(listOf("https://namu.wiki/w/카카오톡"), UrlExtractor.extractAll("https://namu.wiki/w/카카오톡 문서"))
        assertEquals(listOf("https://example.com/path"), UrlExtractor.extractAll("https://example.com/path에서 봐"))
        assertEquals(listOf("https://a.com/search?q=김치"), UrlExtractor.extractAll("https://a.com/search?q=김치 검색"))
    }

    @Test
    fun `붙은 URL 은 쉼표·세미콜론·한글 뒤에서 나눈다`() {
        assertEquals(listOf("https://a.com/b", "https://c.com/d"), UrlExtractor.extractAll("https://a.com/b,https://c.com/d"))
        assertEquals(listOf("https://a.com/b", "https://c.com/d"), UrlExtractor.extractAll("https://a.com/b;https://c.com/d"))
        assertEquals(listOf("https://a.com/한글", "https://c.com/d"), UrlExtractor.extractAll("https://a.com/한글https://c.com/d"))
    }

    @Test
    fun `쿼리 값 안의 URL 은 나누지 않는다`() {
        assertEquals(listOf("https://x.com/r?u=https://y.com/z"), UrlExtractor.extractAll("https://x.com/r?u=https://y.com/z"))
    }

    @Test
    fun `www 로 시작하면 https 를 붙이고 스킴 없는 도메인은 잡지 않는다`() {
        assertEquals(listOf("https://www.coupang.com/vp/1"), UrlExtractor.extractAll("www.coupang.com/vp/1 상품"))
        assertEquals(emptyList<String>(), UrlExtractor.extractAll("naver.me/abc 파일.txt"))
    }

    @Test
    fun `이메일과 점 없는 호스트는 버린다`() {
        assertEquals(emptyList<String>(), UrlExtractor.extractAll("메일 minji@www.naver.com 로"))
        assertEquals(emptyList<String>(), UrlExtractor.extractAll("http://localhost:8080/a"))
        assertEquals(emptyList<String>(), UrlExtractor.extractAll("시각 3 : 2, 사진"))
    }

    @Test
    fun `http 링크 판별`() {
        assertTrue(UrlExtractor.isHttpUrl("https://a.com"))
        assertFalse(UrlExtractor.isHttpUrl("javascript:void(0)"))
        assertFalse(UrlExtractor.isHttpUrl("chrome://settings/"))
        assertFalse(UrlExtractor.isHttpUrl("place:sort=8"))
    }
}
