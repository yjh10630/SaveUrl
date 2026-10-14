package com.jinscompany.saveurl.domain.autocat

import com.jinscompany.saveurl.domain.model.AutoCategoryRule
import com.jinscompany.saveurl.domain.model.AutoRuleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 33번 §2.2 우선순위, 36번 §2.5·§7.1 기록·점검 */
class AutoCategoryMatcherTest {

    private var nextId = 1L
    private fun rule(type: RuleMatchType, pattern: String, categoryId: Int, state: AutoRuleState = AutoRuleState.ACTIVE) =
        AutoCategoryRule(id = nextId++, matchType = type.name, pattern = pattern, categoryId = categoryId, state = state.name)

    private fun match(url: String, rules: List<AutoCategoryRule>) = AutoCategoryMatcher.match(url, rules) { it }?.categoryId

    @Test
    fun `하위 도메인·모바일 주소도 사이트 규칙에 맞는다`() {
        val rules = listOf(rule(RuleMatchType.HOST, "instagram.com", 1))
        assertEquals(1, match("https://www.instagram.com/p/abc", rules))
        assertEquals(1, match("https://m.instagram.com/reel/abc", rules))
        assertEquals(1, match("http://instagram.com/x", rules))
        assertNull(match("https://notinstagram.com/x", rules))
    }

    @Test
    fun `주인 규칙이 사이트 전체 규칙보다 먼저다`() {
        val rules = listOf(
            rule(RuleMatchType.HOST, "blog.naver.com", 2),
            rule(RuleMatchType.PATH, "blog.naver.com/seoul_eat", 1),
        )
        assertEquals(1, match("https://blog.naver.com/seoul_eat/22", rules))
        assertEquals(1, match("https://blog.naver.com/PostView.naver?blogId=seoul_eat&logNo=1", rules))
        assertEquals(2, match("https://blog.naver.com/other/22", rules))
    }

    @Test
    fun `긴 호스트가 짧은 호스트보다 먼저다`() {
        val rules = listOf(rule(RuleMatchType.HOST, "naver.com", 1), rule(RuleMatchType.HOST, "news.naver.com", 2))
        assertEquals(2, match("https://n.news.naver.com/article/1", rules))
        assertEquals(1, match("https://map.naver.com/p/1", rules))
    }

    @Test
    fun `꺼진 규칙과 거절 기록은 고르지 않는다`() {
        val rules = listOf(
            rule(RuleMatchType.HOST, "instagram.com", 1, AutoRuleState.OFF),
            rule(RuleMatchType.HOST, "youtube.com", 2, AutoRuleState.DISMISSED),
        )
        assertNull(match("https://instagram.com/p/1", rules))
        assertNull(match("https://youtu.be/abc", rules))
    }

    @Test
    fun `규칙이 없으면 아무것도 고르지 않는다 (추측 없음)`() {
        assertNull(match("https://www.youtube.com/watch?v=1", emptyList()))
        assertNull(match("not a url", listOf(rule(RuleMatchType.HOST, "instagram.com", 1))))
    }

    @Test
    fun `기록 - 바뀌면 바뀜 수와 최근 비트가 늘고, 최근 5번만 남는다`() {
        var r = rule(RuleMatchType.HOST, "instagram.com", 7)
        r = AutoRuleStats.record(r, 7, now = 1)
        r = AutoRuleStats.record(r, 8, now = 2)
        assertEquals(2, r.appliedCount)
        assertEquals(1, r.overriddenCount)
        assertEquals(listOf("7", "8"), r.recentCategoryIds)
        assertFalse(AutoRuleStats.needsFix(r))
        r = AutoRuleStats.record(r, AutoRuleStats.NONE_CATEGORY_ID, now = 3)
        assertTrue(AutoRuleStats.needsFix(r))
        assertEquals(2, AutoRuleStats.recentOverrides(r))
        repeat(5) { r = AutoRuleStats.record(r, 7, now = 10L + it) }
        assertEquals(5, r.recentCount)
        assertEquals(0, AutoRuleStats.recentOverrides(r))
        assertFalse(AutoRuleStats.needsFix(r))
        assertEquals(8, r.appliedCount)
        assertEquals(2, r.overriddenCount)
        assertEquals(5, r.recentCategoryIds.size)
    }

    @Test
    fun `점검은 켜진 규칙만, 초기화하면 사라진다`() {
        var r = rule(RuleMatchType.HOST, "coupang.com", 3)
        r = AutoRuleStats.record(AutoRuleStats.record(r, 4, 1), 4, 2)
        assertTrue(AutoRuleStats.needsFix(r))
        assertFalse(AutoRuleStats.needsFix(r.copy(state = AutoRuleState.OFF.name)))
        val reset = AutoRuleStats.resetRecent(r.copy(fixPromptShown = true))
        assertFalse(AutoRuleStats.needsFix(reset))
        assertFalse(reset.fixPromptShown)
        assertEquals(2, reset.appliedCount)
    }

    @Test
    fun `바꾼 값이 모두 같은 실제 카테고리일 때만 공통 카테고리를 준다`() {
        val base = rule(RuleMatchType.HOST, "coupang.com", 3)
        assertEquals(4, AutoRuleStats.commonOverrideCategoryId(base.copy(recentCategoryIds = listOf("3", "4", "3", "4"))))
        assertNull(AutoRuleStats.commonOverrideCategoryId(base.copy(recentCategoryIds = listOf("4", "5"))))
        assertNull(AutoRuleStats.commonOverrideCategoryId(base.copy(recentCategoryIds = listOf("0", "0"))))
        assertNull(AutoRuleStats.commonOverrideCategoryId(base.copy(recentCategoryIds = listOf("3", "3"))))
    }
}
