package com.jinscompany.saveurl.domain.autocat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 36번 §1·33번 §2.1 키 추출 */
class AutoCategoryKeysTest {

    private fun host(url: String) = AutoCategoryKeys.keysOf(url)?.host
    private fun owner(url: String) = AutoCategoryKeys.keysOf(url)?.ownerKey?.pattern

    @Test
    fun `호스트는 소문자로, 앞의 www·m·mobile 을 뗀다`() {
        assertEquals("instagram.com", host("https://www.instagram.com/p/abc"))
        assertEquals("instagram.com", host("https://m.instagram.com/reel/abc"))
        assertEquals("instagram.com", host("HTTPS://WWW.Instagram.COM/p/abc"))
        assertEquals("example.co.kr", host("http://mobile.example.co.kr/a"))
        assertEquals("blog.naver.com", host("https://m.blog.naver.com/seoul_eat/2233"))
        // 남는 호스트에 점이 없으면 떼지 않는다
        assertEquals("m.com", host("https://m.com/a"))
    }

    @Test
    fun `포트·사용자 정보·쿼리·조각을 무시한다`() {
        assertEquals("example.com", host("https://user:pw@example.com:8443/a?b=1#c"))
        assertEquals("example.com", host("https://example.com?x=1"))
        assertEquals("example.com", host("https://example.com."))
    }

    @Test
    fun `별칭 - youtu_be 는 youtube_com, n_news 는 news_naver_com`() {
        assertEquals("youtube.com", host("https://youtu.be/abc"))
        assertEquals("news.naver.com", host("https://n.news.naver.com/article/001/1"))
        assertEquals("news.naver.com", host("https://m.news.naver.com/article/001/1"))
    }

    @Test
    fun `http·https 가 아니거나 호스트가 없으면 키가 없다`() {
        assertNull(AutoCategoryKeys.keysOf("ftp://example.com/a"))
        assertNull(AutoCategoryKeys.keysOf("example.com/a"))
        assertNull(AutoCategoryKeys.keysOf("https:///a"))
        assertNull(AutoCategoryKeys.keysOf(null))
        assertNull(AutoCategoryKeys.keysOf("intent://x"))
    }

    @Test
    fun `네이버 블로그 주인 - 경로, PostView 쿼리, 대문자`() {
        assertEquals("blog.naver.com/seoul_eat", owner("https://blog.naver.com/seoul_eat/223344"))
        assertEquals("blog.naver.com/seoul_eat", owner("https://m.blog.naver.com/Seoul_Eat/223344"))
        assertEquals("blog.naver.com/seoul_eat", owner("https://blog.naver.com/PostView.naver?blogId=seoul_eat&logNo=1"))
        assertEquals("blog.naver.com/seoul_eat", owner("https://blog.naver.com/PostView.nhn?logNo=1&BLOGID=seoul_eat"))
        assertNull(owner("https://blog.naver.com/"))
        assertNull(owner("https://blog.naver.com/PostView.naver?logNo=1"))
    }

    @Test
    fun `카페 - 경로, ca-fe cafes 경로, clubid 쿼리`() {
        assertEquals("cafe.naver.com/momcafe", owner("https://cafe.naver.com/momcafe/12345"))
        assertEquals("cafe.naver.com/10050146", owner("https://m.cafe.naver.com/ca-fe/cafes/10050146/articles/1"))
        assertEquals("cafe.naver.com/10050146", owner("https://cafe.naver.com/ArticleRead.nhn?clubid=10050146&articleid=1"))
    }

    @Test
    fun `스토어·브런치·velog·GitHub 주인`() {
        assertEquals("smartstore.naver.com/cosme", owner("https://smartstore.naver.com/cosme/products/1"))
        assertEquals("brand.naver.com/nike", owner("https://m.brand.naver.com/nike/products/1"))
        assertEquals("brunch.co.kr/travelnote", owner("https://brunch.co.kr/@travelnote/12"))
        assertNull(owner("https://brunch.co.kr/magazine/abc"))
        assertEquals("velog.io/writer", owner("https://velog.io/@Writer/post-1"))
        assertEquals("github.com/android", owner("https://github.com/android/nowinandroid"))
        assertEquals("github.com/jetbrains", owner("https://github.com/orgs/JetBrains/repositories"))
        assertNull(owner("https://github.com/features/actions"))
        assertNull(owner("https://github.com/"))
    }

    @Test
    fun `주인이 아닌 호스트는 주인 키가 없고 기본 키는 사이트 전체`() {
        val keys = AutoCategoryKeys.keysOf("https://www.10000recipe.com/recipe/1")!!
        assertNull(keys.ownerKey)
        assertEquals(RuleKey(RuleMatchType.HOST, "10000recipe.com"), keys.defaultKey)
        val blog = AutoCategoryKeys.keysOf("https://blog.naver.com/seoul_eat/1")!!
        assertEquals(RuleKey(RuleMatchType.PATH, "blog.naver.com/seoul_eat"), blog.defaultKey)
        assertEquals(RuleKey(RuleMatchType.HOST, "blog.naver.com"), blog.hostKey)
    }

    @Test
    fun `점 경계 매칭 - notgithub 은 github 에 맞지 않는다`() {
        assertTrue(AutoCategoryKeys.hostMatches("github.com", "github.com"))
        assertTrue(AutoCategoryKeys.hostMatches("github.com", "gist.github.com"))
        assertFalse(AutoCategoryKeys.hostMatches("github.com", "notgithub.com"))
        assertFalse(AutoCategoryKeys.hostMatches("instagram.com", "instagram.com.evil.io"))
    }

    @Test
    fun `범용 키 - 범용 목록과 주인 호스트의 사이트 전체, 주인 키는 아님`() {
        assertTrue(AutoCategoryKeys.isBroad(RuleKey(RuleMatchType.HOST, "instagram.com")))
        assertTrue(AutoCategoryKeys.isBroad(RuleKey(RuleMatchType.HOST, "blog.naver.com")))
        assertTrue(AutoCategoryKeys.isBroad(RuleKey(RuleMatchType.HOST, "seoul.tistory.com")))
        assertFalse(AutoCategoryKeys.isBroad(RuleKey(RuleMatchType.HOST, "10000recipe.com")))
        assertFalse(AutoCategoryKeys.isBroad(RuleKey(RuleMatchType.PATH, "blog.naver.com/seoul_eat")))
    }

    @Test
    fun `표시 이름 - 알려진 사이트, 주인, 모르는 사이트`() {
        assertEquals("Instagram", AutoCategoryKeys.displayName(RuleKey(RuleMatchType.HOST, "instagram.com")))
        assertEquals("네이버 블로그 · seoul_eat", AutoCategoryKeys.displayName(RuleKey(RuleMatchType.PATH, "blog.naver.com/seoul_eat")))
        assertEquals("브런치 · @travelnote", AutoCategoryKeys.displayName(RuleKey(RuleMatchType.PATH, "brunch.co.kr/travelnote")))
        assertEquals("GitHub · android", AutoCategoryKeys.displayName(RuleKey(RuleMatchType.PATH, "github.com/android")))
        assertEquals("10000recipe.com", AutoCategoryKeys.displayName(RuleKey(RuleMatchType.HOST, "10000recipe.com")))
    }
}
