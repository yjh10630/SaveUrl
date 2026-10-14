package com.jinscompany.saveurl.domain.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetscapeBookmarkParserTest {

    @Test
    fun `Chrome - 중첩 폴더, 루트 폴더 제외, 제외 링크 개수`() {
        val source = ImportFixtures.read("chrome_bookmarks.html").sources.single()
        assertEquals(ImportFormat.BOOKMARK, source.format)
        val byTitle = source.candidates.associateBy { it.title }
        assertEquals(7, source.candidates.size)
        // 루트 폴더(북마크바) 바로 아래 = 폴더 없음
        assertEquals(emptyList<String>(), byTitle["Hacker News"]!!.folderPath)
        assertEquals(listOf("개발"), byTitle["Android 개발자 & 가이드"]!!.folderPath)
        assertEquals(listOf("개발", "안드로이드"), byTitle["Kotlin Docs"]!!.folderPath)
        assertEquals(listOf("맛집"), byTitle["성수 브런치"]!!.folderPath)
        assertEquals(emptyList<String>(), byTitle["Wikipedia"]!!.folderPath)
        assertEquals(listOf("맛집"), byTitle["홍대 카페"]!!.folderPath)
        // &amp; 풀기
        assertEquals("https://developer.android.com/guide?hl=ko&utm_source=bm", byTitle["Android 개발자 & 가이드"]!!.url)
        // ADD_DATE 초 → ms
        assertEquals(1_650_000_000_000L, byTitle["Hacker News"]!!.date)
        // javascript:, chrome:// 는 건너뛰고 센다
        assertEquals(2, source.nonHttpCount)
    }

    @Test
    fun `Firefox - 마이크로초 ADD_DATE, place 링크, 태그`() {
        val source = ImportFixtures.read("firefox_bookmarks.html").sources.single()
        assertEquals(3, source.candidates.size)
        val essay = source.candidates.first { it.title == "주말 에세이" }
        assertEquals(1_710_000_000_000L, essay.date)
        assertEquals(listOf("읽을거리"), essay.folderPath)
        assertEquals(listOf("에세이", "주말"), essay.tags)
        // PERSONAL_TOOLBAR_FOLDER 는 루트
        assertEquals(emptyList<String>(), source.candidates.first { it.title == "GitHub" }.folderPath)
        assertEquals(1, source.nonHttpCount)
    }

    @Test
    fun `웨일·엣지 - 즐겨찾기 모음 루트`() {
        val source = ImportFixtures.read("whale_bookmarks.html").sources.single()
        assertEquals(2, source.candidates.size)
        assertEquals(emptyList<String>(), source.candidates[0].folderPath)
        assertEquals(listOf("쇼핑"), source.candidates[1].folderPath)
    }

    @Test
    fun `날짜 단위 판별`() {
        val parser = NetscapeBookmarkParser(nowMillis = { ImportFixtures.NOW })
        assertEquals(1_700_000_000_000L, parser.toMillis("1700000000"))
        assertEquals(1_700_000_000_000L, parser.toMillis("1700000000000"))
        assertEquals(1_700_000_000_000L, parser.toMillis("1700000000000000"))
        assertNull(parser.toMillis("0"))
        assertNull(parser.toMillis("4102444800"))  // 2100년
    }
}
