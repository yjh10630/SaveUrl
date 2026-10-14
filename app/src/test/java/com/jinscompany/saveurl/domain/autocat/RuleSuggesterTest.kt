package com.jinscompany.saveurl.domain.autocat

import com.jinscompany.saveurl.domain.model.AutoCatEvidence
import com.jinscompany.saveurl.domain.model.AutoCategoryRule
import com.jinscompany.saveurl.domain.model.AutoRuleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 36번 §4.1 제안 (5개·10개 문턱, 100%, 거절·규칙·잠금·가져오기 제외) */
class RuleSuggesterTest {

    private val categories = mapOf("레시피" to 1, "맛집" to 2, "여행" to 3, "쇼핑" to 4)

    private fun links(url: String, category: String?, n: Int, imported: Boolean = false, locked: Boolean = false) =
        (1..n).map { AutoCatEvidence("$url/$it", category, imported, locked) }

    private fun suggest(evidence: List<AutoCatEvidence>, rules: List<AutoCategoryRule> = emptyList(), cats: Map<String, Int> = categories) =
        RuleSuggester.suggest(evidence, rules, cats)

    @Test
    fun `일반 사이트는 5개 모두 같으면 제안, 4개는 아니다`() {
        assertTrue(suggest(links("https://www.10000recipe.com/recipe", "레시피", 4)).isEmpty())
        val s = suggest(links("https://www.10000recipe.com/recipe", "레시피", 5)).single()
        assertEquals(RuleKey(RuleMatchType.HOST, "10000recipe.com"), s.key)
        assertEquals(1, s.categoryId)
        assertEquals(5, s.count)
    }

    @Test
    fun `하나라도 다른 카테고리면 제안하지 않는다 (99퍼센트 미제안)`() {
        val evidence = links("https://www.10000recipe.com/recipe", "레시피", 99) + links("https://10000recipe.com/x", "맛집", 1)
        assertTrue(suggest(evidence).isEmpty())
    }

    @Test
    fun `범용 사이트와 주인 호스트 전체는 10개`() {
        assertTrue(suggest(links("https://www.instagram.com/p", "맛집", 9)).isEmpty())
        assertEquals(10, suggest(links("https://www.instagram.com/p", "맛집", 10)).single().count)
        // 블로거 5명 × 2개: 주인 키는 각 2개라 안 되고, blog.naver.com 전체는 10개라 제안
        val blogs = (1..5).flatMap { i -> links("https://blog.naver.com/writer$i", "맛집", 2) }
        val s = suggest(blogs).single()
        assertEquals(RuleKey(RuleMatchType.HOST, "blog.naver.com"), s.key)
    }

    @Test
    fun `주인 키는 5개 기준이고, 사이트 전체 제안과 결과가 같으면 뺀다`() {
        val one = suggest(links("https://blog.naver.com/seoul_eat", "맛집", 5)).single()
        assertEquals(RuleKey(RuleMatchType.PATH, "blog.naver.com/seoul_eat"), one.key)
        val evidence = links("https://blog.naver.com/seoul_eat", "맛집", 6) + links("https://blog.naver.com/other", "맛집", 6)
        val result = suggest(evidence)
        assertEquals(listOf(RuleKey(RuleMatchType.HOST, "blog.naver.com")), result.map { it.key })
    }

    @Test
    fun `선택 안 함·미분류는 근거가 아니다`() {
        val evidence = links("https://www.10000recipe.com/recipe", "레시피", 5) +
            links("https://10000recipe.com/a", "전체", 3) + links("https://10000recipe.com/b", "미분류", 3) + links("https://10000recipe.com/c", null, 3)
        assertEquals(5, suggest(evidence).single().count)
    }

    @Test
    fun `가져온 링크는 근거가 아니다`() {
        assertTrue(suggest(links("https://www.10000recipe.com/recipe", "레시피", 8, imported = true)).isEmpty())
    }

    @Test
    fun `잠금 카테고리 링크가 하나라도 있는 키는 건너뛴다`() {
        val evidence = links("https://www.coupang.com/vp", "쇼핑", 12) + links("https://www.coupang.com/secret", "선물", 1, locked = true)
        assertTrue(suggest(evidence).isEmpty())
    }

    @Test
    fun `이미 규칙이 있는 키는 제안하지 않고, 거절은 같은 카테고리만 막는다`() {
        val evidence = links("https://www.10000recipe.com/recipe", "레시피", 5)
        val active = AutoCategoryRule(id = 1, matchType = "HOST", pattern = "10000recipe.com", categoryId = 3)
        val off = active.copy(state = AutoRuleState.OFF.name)
        val dismissedSame = active.copy(state = AutoRuleState.DISMISSED.name, categoryId = 1)
        val dismissedOther = active.copy(state = AutoRuleState.DISMISSED.name, categoryId = 2)
        assertTrue(suggest(evidence, listOf(active)).isEmpty())
        assertTrue(suggest(evidence, listOf(off)).isEmpty())
        assertTrue(suggest(evidence, listOf(dismissedSame)).isEmpty())
        assertEquals(1, suggest(evidence, listOf(dismissedOther)).single().categoryId)
    }

    @Test
    fun `지금 없는(또는 잠긴) 카테고리로는 제안하지 않는다`() {
        val evidence = links("https://www.10000recipe.com/recipe", "옛 카테고리", 7)
        assertTrue(suggest(evidence).isEmpty())
    }

    @Test
    fun `많은 순으로 정렬한다`() {
        val evidence = links("https://www.10000recipe.com/recipe", "레시피", 5) + links("https://www.musinsa.com/goods", "쇼핑", 8)
        assertEquals(listOf("musinsa.com", "10000recipe.com"), suggest(evidence).map { it.key.pattern })
    }

    @Test
    fun `문턱은 원격 설정으로 바꿀 수 있다`() {
        val evidence = links("https://www.10000recipe.com/recipe", "레시피", 3)
        val result = RuleSuggester.suggest(evidence, emptyList(), categories, SuggestThresholds(minN = 3, minNBroad = 6))
        assertEquals(3, result.single().count)
    }
}
