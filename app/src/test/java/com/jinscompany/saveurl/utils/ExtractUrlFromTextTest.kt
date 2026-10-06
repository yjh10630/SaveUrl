package com.jinscompany.saveurl.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtractUrlFromTextTest {

    @Test
    fun `쿼리 파라미터의 앰퍼샌드가 포함된 URL 전체를 추출한다`() {
        assertEquals(
            "https://www.youtube.com/watch?v=abc123&t=42s",
            extractUrlFromText("이 영상 보세요 https://www.youtube.com/watch?v=abc123&t=42s")
        )
    }

    @Test
    fun `퍼센트 인코딩된 URL 을 자르지 않는다`() {
        assertEquals(
            "https://namu.wiki/w/%EB%82%98%EB%AC%B4",
            extractUrlFromText("https://namu.wiki/w/%EB%82%98%EB%AC%B4 공유")
        )
    }

    @Test
    fun `포트와 프래그먼트를 포함한다`() {
        assertEquals(
            "http://example.com:8080/a/b#section",
            extractUrlFromText("http://example.com:8080/a/b#section")
        )
    }

    @Test
    fun `줄바꿈 뒤의 URL 을 추출한다`() {
        assertEquals(
            "https://link.coupang.com/a/cqn415",
            extractUrlFromText("쿠팡을 추천 합니다!\n삼성전자 4K UHD QLED 스마트 TV\nhttps://link.coupang.com/a/cqn415")
        )
    }

    @Test
    fun `문장 끝 구두점과 짝이 없는 닫는 괄호는 제외한다`() {
        assertEquals("https://a.com/b", extractUrlFromText("여기 참고(https://a.com/b)."))
        assertEquals(
            "https://en.wikipedia.org/wiki/Kotlin_(programming_language)",
            extractUrlFromText("https://en.wikipedia.org/wiki/Kotlin_(programming_language)")
        )
    }

    @Test
    fun `한글이 바로 붙어 있으면 한글 앞에서 끊는다`() {
        assertEquals("https://naver.me/abc", extractUrlFromText("https://naver.me/abc에서 공유"))
    }

    @Test
    fun `URL 이 없으면 null`() {
        assertNull(extractUrlFromText("링크 없음"))
    }
}
